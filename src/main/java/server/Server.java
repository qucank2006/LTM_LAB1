package server;

import model.Message;
import model.AuthRequest;
import model.AuthResult;
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
    private static final AuthService auth = new AuthService();
    private static final ChatDatabase database = new ChatDatabase();

    public static void main(String[] args) {
        String localIp = getRealLanIp();

        System.out.println("==========================================================");
        System.out.println("=== CHAT SERVER TRUNG TÂM (TCP SOCKET & CSDL MYSQL)      ===");
        System.out.println("=== IP LAN Máy Chủ: " + localIp + " (Client tự kết nối)         ===");
        System.out.println("=== Cổng TCP: " + PORT + "                                          ===");
        System.out.println("=== CSDL: " + (database.isMySQLAvailable() ? "MySQL XAMPP (ltm_chat)" : "Tệp cục bộ Fallback") + "                 ===");
        System.out.println("==========================================================");

        try (ServerSocket serverSocket = new ServerSocket(PORT, 50, InetAddress.getByName(AppConfig.serverBind()))) {
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
            System.err.println("Lỗi kết nối Server.");
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
        private volatile User currentUser;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                socket.setSoTimeout(5000);
                in = new ObjectInputStream(socket.getInputStream());
                socket.setSoTimeout(45000);

                while (true) {
                    Message message = (Message) in.readObject();
                    if (message == null || message.getType() == null) continue;
                    if (message.getType() == MessageType.PING) {
                        sendMessage(new Message(MessageType.PONG, "server", ""));
                        continue;
                    }
                    if (message.getType() == MessageType.LOGOUT) break;
                    if (message.getType() == MessageType.LOGIN || message.getType() == MessageType.REGISTER) {
                        handleAuthentication(message);
                        continue;
                    }
                    if (currentUser == null) {
                        sendMessage(new Message(MessageType.ERROR, "server", "", "Bạn cần đăng nhập trước khi chat."));
                        continue;
                    }
                    // Identity always comes from this connection's authenticated session.
                    message.setSender(currentUser.getUsername());

                    switch (message.getType()) {
                        case LOGIN:
                            handleAuthentication(message);
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
                System.err.println("! Lỗi luồng Client (" + (currentUser != null ? currentUser.getUsername() : "Unknown") + "): " + "Kết nối không hợp lệ.");
            } finally {
                if (currentUser != null) {
                    // Chỉ xóa nếu đúng handler hiện tại đang giữ username này (tránh trường hợp login lại đè mất)
                    onlineClients.remove(currentUser.getUsername(), this);
                    System.out.println("- User thoát: " + currentUser.getUsername());
                    broadcastUserList();
                }
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
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
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }

        private void handleAuthentication(Message message) {
            if (currentUser != null) {
                sendMessage(new Message(MessageType.ERROR, "server", "", "Phiên đã đăng nhập."));
                return;
            }
            AuthResult result;
            if (!(message.getPayload() instanceof AuthRequest request)) {
                result = new AuthResult(false, "Client cần được cập nhật để đăng nhập bằng mật khẩu.", null);
            } else {
                result = auth.authenticate(request, message.getType() == MessageType.REGISTER,
                        socket.getInetAddress().getHostAddress());
                if (result.success() && result.user() != null) {
                    User user = result.user();
                    currentUser = user;
                    if (onlineClients.putIfAbsent(user.getUsername(), this) != null) {
                        currentUser = null;
                        result = new AuthResult(false, "Tài khoản đang đăng nhập trên kết nối khác.", null);
                    } else {
                        currentUser = user;
                    }
                }
            }
            Message response = new Message(MessageType.AUTH_RESPONSE, "server", "");
            response.setPayload(result);
            sendMessage(response);
            if (currentUser != null) broadcastUserList();
        }

        private void handleMessage(Message message) {
            String receiverName = message.getReceiver();
            // Preserve the existing group text envelope; route only to its declared members.
            if (message.getContent() != null && message.getContent().startsWith("[GROUP_CHAT|")) {
                int close = message.getContent().indexOf(']');
                if (close < 0) return;
                String[] parts = message.getContent().substring(12, close).split("\\|", 2);
                if (parts.length != 2) return;
                java.util.Set<String> members = new java.util.HashSet<>(java.util.Arrays.asList(parts[1].split(",")));
                if (!members.contains(currentUser.getUsername())) return;
                for (String member : members) {
                    ClientHandler target = onlineClients.get(member);
                    if (target != null && target != this) target.sendMessage(message);
                }
                return;
            }
            if (receiverName != null && receiverName.startsWith("👥 Nhóm: ") && message.getPayload() instanceof java.util.Set<?> members) {
                if (!members.contains(currentUser.getUsername())) return;
                for (Object member : members) {
                    ClientHandler target = onlineClients.get(member);
                    if (target != null && target != this) target.sendMessage(message);
                }
                return;
            }
            if (receiverName == null || receiverName.equalsIgnoreCase("All")) {
                for (ClientHandler handler : onlineClients.values()) {
                    if (handler.currentUser != null && !handler.currentUser.getUsername().equals(message.getSender())) {
                        handler.sendMessage(message);
                    }
                }
            } else if (onlineClients.get(receiverName) != null) {
                ClientHandler receiverHandler = onlineClients.get(receiverName);
                if (receiverHandler != null) receiverHandler.sendMessage(message);
            } else {
                sendMessage(new Message(MessageType.ERROR, "server", currentUser.getUsername(), "Người nhận không còn Online."));
            }
        }

        private void handleGetIpRequest(Message message) {
            String targetUsername = message.getReceiver();
            ClientHandler target = onlineClients.get(targetUsername);
            if (target != null && target.currentUser != null) {
                User targetUser = target.currentUser;

                Message response = new Message(MessageType.GET_IP_RESPONSE, "server", message.getSender());
                response.setPayload(targetUser);
                this.sendMessage(response);
                System.out.println("-> Cấp IP của " + targetUsername + " (" + targetUser.getIpAddress() + ":" + targetUser.getPeerPort() + ") cho " + message.getSender());
            } else {
                sendMessage(new Message(MessageType.ERROR, "server", currentUser.getUsername(), "Người nhận P2P không còn Online."));
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