package server;

import model.Message;
import util.AppConfig;
import model.MessageType;
import model.User;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    private static final int PORT = AppConfig.serverPort();

    private static final ConcurrentHashMap<String, ClientHandler> onlineClients = new ConcurrentHashMap<>();
    private static final ChatDatabase database = new ChatDatabase();

    public static void main(String[] args) {
        String localIp = getRealLanIp();

        System.out.println("==========================================================");
        System.out.println("=== CHAT SERVER TRUNG TÂM (TCP SOCKET & CSDL MYSQL)      ===");
        System.out.println("=== IP LAN Máy Chủ: " + localIp + " (Nhập vào Client)         ===");
        System.out.println("=== Cổng TCP: " + PORT + "                                          ===");
        System.out.println("=== CSDL: " + (database.isMySQLAvailable() ? "MySQL XAMPP (ltm_chat)" : "Tệp cục bộ Fallback") + "                 ===");
        System.out.println("==========================================================");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                // Chống router tự ngắt kết nối TCP khi nhàn rỗi & giảm độ trễ gói tin nhỏ
                clientSocket.setKeepAlive(true);
                clientSocket.setTcpNoDelay(true);

                System.out.println("-> Có Client mới kết nối từ: " + clientSocket.getInetAddress().getHostAddress());

                ClientHandler handler = new ClientHandler(clientSocket);
                handler.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Lấy đúng IP Wi-Fi / Ethernet thực tế (tránh lấy nhầm IP card ảo VMware/WSL)
    private static String getRealLanIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                String name = iface.getDisplayName().toLowerCase();
                if (name.contains("vmware") || name.contains("virtual") || name.contains("wsl") || name.contains("hyper-v")) {
                    continue;
                }
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    // =========================================================
    // Luồng xử lý riêng cho từng Client
    // =========================================================
    private static class ClientHandler extends Thread {
        private final Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private User currentUser;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());

                while (true) {
                    Message message = (Message) in.readObject();
                    if (message == null) continue;

                    switch (message.getType()) {
                        case LOGIN:
                            handleLogin(message);
                            break;

                        case TEXT_MESSAGE:
                        case AUDIO_MESSAGE:
                        case VIDEO_MESSAGE:
                            // Bỏ qua lưu DB nếu là gói tin Ping giữ kết nối
                            if (!"__PING__".equals(message.getContent())) {
                                database.saveMessage(message);
                                handleMessage(message);
                            }
                            break;

                        case GET_IP_REQUEST:
                            handleGetIpRequest(message);
                            break;

                        case GET_USER_LIST:
                            broadcastUserList();
                            break;

                        default:
                            break;
                    }
                }
            } catch (EOFException | SocketException e) {
                System.out.println("- Client đóng kết nối: " + (currentUser != null ? currentUser.getUsername() : socket.getInetAddress().getHostAddress()));
            } catch (Exception e) {
                System.err.println("! Lỗi luồng Client (" + (currentUser != null ? currentUser.getUsername() : "Unknown") + "): " + e.getMessage());
                e.printStackTrace();
            } finally {
                if (currentUser != null) {
                    // Chỉ xóa nếu đúng handler hiện tại đang giữ username này (tránh trường hợp login lại đè mất)
                    onlineClients.remove(currentUser.getUsername(), this);
                    System.out.println("- User thoát: " + currentUser.getUsername());
                    broadcastUserList();
                }
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        // [QUAN TRỌNG] Đồng bộ hóa để 2 luồng không ghi đè byte lên cùng 1 ObjectOutputStream
        public synchronized void sendMessage(Message msg) {
            try {
                if (out != null && !socket.isClosed()) {
                    out.writeObject(msg);
                    out.flush();
                    out.reset(); // Giải phóng cache tham chiếu object tránh tràn RAM khi gửi Audio/Video
                }
            } catch (IOException e) {
                System.err.println("! Không gửi được tin tới " + (currentUser != null ? currentUser.getUsername() : "client") + ": " + e.getMessage());
            }
        }

        private void handleLogin(Message message) {
            User incomingUser = (User) message.getPayload();
            // Cập nhật IP thực tế từ socket kết nối tới Server (chính xác 100% trong mạng LAN)
            String realClientIp = socket.getInetAddress().getHostAddress();
            this.currentUser = new User(incomingUser.getUsername(), realClientIp, incomingUser.getPeerPort());

            onlineClients.put(currentUser.getUsername(), this);
            System.out.println("+ Đăng nhập thành công: " + currentUser.getUsername() +
                    " (IP thực: " + currentUser.getIpAddress() + ", Port P2P: " + currentUser.getPeerPort() + ")");
            broadcastUserList();
        }

        private void handleMessage(Message message) {
            String receiverName = message.getReceiver();
            if (receiverName == null || receiverName.equalsIgnoreCase("All")) {
                for (ClientHandler handler : onlineClients.values()) {
                    if (handler.currentUser != null && !handler.currentUser.getUsername().equals(message.getSender())) {
                        handler.sendMessage(message);
                    }
                }
            } else if (onlineClients.containsKey(receiverName)) {
                ClientHandler receiverHandler = onlineClients.get(receiverName);
                receiverHandler.sendMessage(message);
            } else {
                System.out.println("Không tìm thấy người nhận: " + receiverName);
            }
        }

        private void handleGetIpRequest(Message message) {
            String targetUsername = message.getReceiver();
            if (onlineClients.containsKey(targetUsername)) {
                User targetUser = onlineClients.get(targetUsername).currentUser;

                Message response = new Message(MessageType.GET_IP_RESPONSE, "server", message.getSender());
                response.setPayload(targetUser);
                this.sendMessage(response);
                System.out.println("-> Cấp IP của " + targetUsername + " (" + targetUser.getIpAddress() + ":" + targetUser.getPeerPort() + ") cho " + message.getSender());
            }
        }

        private void broadcastUserList() {
            List<User> listUsers = new ArrayList<>();
            for (ClientHandler handler : onlineClients.values()) {
                if (handler.currentUser != null) {
                    listUsers.add(handler.currentUser);
                }
            }

            Message msg = new Message(MessageType.USER_LIST_RESPONSE, "server", "All");
            msg.setPayload(listUsers);

            for (ClientHandler handler : onlineClients.values()) {
                handler.sendMessage(msg);
            }
        }
    }
}