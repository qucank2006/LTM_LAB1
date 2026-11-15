package util;

import model.Message;

import java.io.*;
import java.net.*;
import java.util.function.Consumer;

public class BroadcastHandler {
    public static final int BROADCAST_PORT = 9999;
    private DatagramSocket socket;
    private volatile boolean running = false;
    private final Consumer<Message> messageConsumer;

    public BroadcastHandler(Consumer<Message> messageConsumer) {
        this.messageConsumer = messageConsumer;
    }

    public synchronized void start() {
        if (running) return;
        try {
            socket = new DatagramSocket(BROADCAST_PORT);
            socket.setBroadcast(true);
            running = true;

            Thread listenerThread = new Thread(this::listenLoop);
            listenerThread.setDaemon(true);
            listenerThread.setName("BroadcastListenerThread");
            listenerThread.start();
            System.out.println("[Broadcast] Đang lắng nghe UDP Broadcast trên cổng " + BROADCAST_PORT);
        } catch (Exception e) {
            if (socket != null) socket.close();
            throw new IllegalStateException("Không mở được Broadcast UDP 9999.", e);
        }
    }

    private void listenLoop() {
        byte[] buffer = new byte[65535];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                ByteArrayInputStream bais = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
                ObjectInputStream ois = new ObjectInputStream(bais);
                Object obj = ois.readObject();
                if (obj instanceof Message msg) {
                    if (messageConsumer != null) {
                        messageConsumer.accept(msg);
                    }
                }
            } catch (SocketException se) {
                if (!running) break;
            } catch (Exception e) {
                if (running) {
                    System.err.println("[Broadcast] Lỗi nhận: " + e.getMessage());
                }
            }
        }
    }

    public void sendBroadcast(Message message) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(message);
            oos.flush();
            byte[] data = baos.toByteArray();
            if (data.length > 65507)
                throw new IllegalArgumentException("Dữ liệu vượt giới hạn một gói UDP (65.507 byte). Hãy dùng TCP hoặc P2P.");

            DatagramPacket packet = new DatagramPacket(
                    data,
                    data.length,
                    InetAddress.getByName("255.255.255.255"),
                    BROADCAST_PORT
            );
            try (DatagramSocket sendSocket = new DatagramSocket()) {
                sendSocket.setBroadcast(true);
                sendSocket.send(packet);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Không gửi được Broadcast: " + e.getMessage(), e);
        }
    }

    public synchronized void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}
