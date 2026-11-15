package util;

import model.Message;

import java.io.*;
import java.net.*;
import java.util.Collections;
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

    public synchronized void start() {
        if (running) return;
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
            System.err.println("=== MULTICAST ERROR ===");
            System.err.println("Message: " + e.getMessage());
            e.printStackTrace();

            if (socket != null) {
                socket.close();
                socket = null;
            }

            throw new IllegalStateException(
                    "Không mở được Multicast UDP 8888.", e
            );
        }
    }

    private NetworkInterface findMulticastInterface()
            throws SocketException {

        Enumeration<NetworkInterface> interfaces =
                NetworkInterface.getNetworkInterfaces();

        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();

            if (!ni.isUp()
                    || ni.isLoopback()
                    || !ni.supportsMulticast()) {
                continue;
            }

            boolean hasIPv4 = Collections.list(
                    ni.getInetAddresses()
            ).stream().anyMatch(
                    address -> address instanceof Inet4Address
                            && !address.isLoopbackAddress()
                            && !address.isLinkLocalAddress()
            );

            if (!hasIPv4) {
                continue;
            }

            String name = ni.getDisplayName().toLowerCase();

            if (name.contains("vmware")
                    || name.contains("virtual")
                    || name.contains("hyper-v")
                    || name.contains("wsl")) {
                continue;
            }

            System.out.println(
                    "[Multicast] Selected interface: "
                            + ni.getDisplayName()
            );

            return ni;
        }

        throw new SocketException(
                "Không tìm thấy NetworkInterface IPv4 hỗ trợ Multicast"
        );
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
            if (data.length > 65507)
                throw new IllegalArgumentException("Dữ liệu vượt giới hạn một gói UDP (65.507 byte). Hãy dùng TCP hoặc P2P.");

            DatagramPacket packet = new DatagramPacket(
                    data,
                    data.length,
                    InetAddress.getByName(MULTICAST_IP),
                    MULTICAST_PORT
            );
            try (DatagramSocket sendSocket = new DatagramSocket()) {
                sendSocket.send(packet);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Không gửi được Multicast: " + e.getMessage(), e);
        }
    }

    public synchronized void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            try {
                if (networkInterface != null && groupAddress != null) {
                    socket.leaveGroup(groupAddress, networkInterface);
                }
                socket.close();
            } catch (Exception ignored) {
            } finally {
                socket.close();
            }
        }
    }
}
