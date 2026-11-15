package util;

import model.Message;

import java.io.*;
import java.net.*;
import java.util.Enumeration;
import java.util.function.Consumer;

public class MulticastHandler {
    public static final String MULTICAST_IP = "230.0.0.1";
    public static final int MULTICAST_PORT = 8888;

    private MulticastSocket socket;
    private InetSocketAddress groupAddress;
    private NetworkInterface networkInterface;
    private volatile boolean running = false;
    private final Consumer<Message> messageConsumer;

    public MulticastHandler(Consumer<Message> messageConsumer) {
        this.messageConsumer = messageConsumer;
    }

    public void start() {
        try {
            socket = new MulticastSocket(MULTICAST_PORT);
            groupAddress = new InetSocketAddress(InetAddress.getByName(MULTICAST_IP), MULTICAST_PORT);

            networkInterface = findMulticastInterface();
            if (networkInterface != null) {
                socket.joinGroup(groupAddress, networkInterface);
            } else {
                socket.joinGroup(groupAddress, null);
            }

            running = true;
            Thread listenerThread = new Thread(this::listenLoop);
            listenerThread.setDaemon(true);
            listenerThread.setName("MulticastListenerThread");
            listenerThread.start();
            System.out.println("[Multicast] Đã tham gia nhóm UDP " + MULTICAST_IP + ":" + MULTICAST_PORT);
        } catch (Exception e) {
            System.err.println("[Multicast] Lỗi khởi tạo: " + e.getMessage());
        }
    }

    private NetworkInterface findMulticastInterface() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isUp() && ni.supportsMulticast() && !ni.isLoopback()) {
                    return ni;
                }
            }
        } catch (Exception ignored) {}
        return null;
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
                    System.err.println("[Multicast] Lỗi nhận gói: " + e.getMessage());
                }
            }
        }
    }

    public void sendMulticast(Message message) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(message);
            oos.flush();
            byte[] data = baos.toByteArray();

            DatagramPacket packet = new DatagramPacket(
                    data,
                    data.length,
                    InetAddress.getByName(MULTICAST_IP),
                    MULTICAST_PORT
            );
            DatagramSocket sendSocket = new DatagramSocket();
            sendSocket.send(packet);
            sendSocket.close();
        } catch (Exception e) {
            System.err.println("[Multicast] Lỗi gửi tin: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            try {
                if (networkInterface != null && groupAddress != null) {
                    socket.leaveGroup(groupAddress, networkInterface);
                }
                socket.close();
            } catch (Exception ignored) {}
        }
    }
}
