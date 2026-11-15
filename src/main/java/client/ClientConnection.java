package client;

import model.*;
import util.AppConfig;

import javax.swing.SwingUtilities;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * One socket, one reader and one ordered writer per client connection.
 */
public final class ClientConnection implements AutoCloseable {
    private final Socket socket = new Socket();
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "chat-writer");
        t.setDaemon(true);
        return t;
    });
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "chat-heartbeat");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean closed;
    private volatile Consumer<Message> receiver = m -> {
    };
    private volatile Runnable disconnected = () -> {
    };
    private volatile CompletableFuture<AuthResult> pending;

    public void connect() throws IOException {
        socket.connect(new InetSocketAddress(AppConfig.serverHost(), AppConfig.serverPort()), 5000);
        socket.setKeepAlive(true);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(5000);
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());
        socket.setSoTimeout(40000);
        Thread reader = new Thread(this::readLoop, "chat-reader");
        reader.setDaemon(true);
        reader.start();
        heartbeat.scheduleAtFixedRate(() -> send(new Message(MessageType.PING, "", "server")), 15, 15, TimeUnit.SECONDS);
    }

    public boolean isConnected() {
        return socket.isConnected() && !closed;
    }

    public void onMessage(Consumer<Message> listener) {
        receiver = listener;
    }

    public void onDisconnect(Runnable listener) {
        disconnected = listener;
        if (closed) SwingUtilities.invokeLater(listener);
    }

    public synchronized CompletableFuture<AuthResult> authenticate(AuthRequest request, boolean register) {
        if (pending != null) return CompletableFuture.failedFuture(new IOException("Yêu cầu đang xử lý."));
        CompletableFuture<AuthResult> future = new CompletableFuture<>();
        pending = future;
        Message m = new Message(register ? MessageType.REGISTER : MessageType.LOGIN, "", "server");
        m.setPayload(request);
        send(m).exceptionally(e -> {
            future.completeExceptionally(e);
            return null;
        });
        future.orTimeout(12, TimeUnit.SECONDS).whenComplete((r, e) -> {
            synchronized (this) {
                if (pending == future) pending = null;
            }
            // A late response must never be mistaken for another authentication request.
            if (e != null) close();
        });
        return future;
    }

    public CompletableFuture<Void> send(Message message) {
        if (!isConnected()) return CompletableFuture.failedFuture(new IOException("Server đã ngắt kết nối."));
        try {
            return CompletableFuture.runAsync(() -> {
                try {
                    synchronized (out) {
                        out.writeObject(message);
                        out.flush();
                        out.reset();
                    }
                } catch (IOException e) {
                    close();
                    throw new CompletionException(e);
                }
            }, writer);
        } catch (RejectedExecutionException e) {
            return CompletableFuture.failedFuture(new IOException("Kết nối đã đóng."));
        }
    }

    private void readLoop() {
        try {
            while (!closed) {
                Object object = in.readObject();
                if (!(object instanceof Message m)) continue;
                if (m.getType() == MessageType.PONG) continue;
                if (m.getType() == MessageType.AUTH_RESPONSE && m.getPayload() instanceof AuthResult result) {
                    CompletableFuture<AuthResult> future = pending;
                    if (future != null) future.complete(result);
                } else {
                    SwingUtilities.invokeLater(() -> {
                        if (!closed) receiver.accept(m);
                    });
                }
            }
        } catch (IOException | ClassNotFoundException | RuntimeException e) {
            close();
        }
    }

    @Override
    public void close() {
        synchronized (this) {
            if (closed) return;
            closed = true;
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            writer.shutdownNow();
            heartbeat.shutdownNow();
        }
        CompletableFuture<AuthResult> future = pending;
        if (future != null) future.completeExceptionally(new IOException("Mất kết nối Server."));
        SwingUtilities.invokeLater(() -> disconnected.run());
    }
}
