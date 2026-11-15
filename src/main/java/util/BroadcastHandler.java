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

    public void start() {
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
            System.err.println("[Broadcast] Lỗi mở cổng " + BROADCAST_PORT + ": " + e.getMessage());
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

            DatagramPacket packet = new DatagramPacket(
                    data,
                    data.length,
                    InetAddress.getByName("255.255.255.255"),
                    BROADCAST_PORT
            );
            DatagramSocket sendSocket = new DatagramSocket();
            sendSocket.setBroadcast(true);
            sendSocket.send(packet);
            sendSocket.close();
        } catch (Exception e) {
            System.err.println("[Broadcast] Lỗi gửi: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}
