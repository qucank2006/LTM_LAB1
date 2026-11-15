package client;

import model.Message;
import util.AppConfig;
import model.MessageType;
import model.User;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Scanner;

public class Client {
    private static final String SERVER_IP = AppConfig.serverHost(); // Đổi thành IP của Server nếu chạy 2 máy khác nhau
    private static final int SERVER_PORT = AppConfig.serverPort();

    private static ObjectOutputStream out;
    private static ObjectInputStream in;
    private static String myUsername;
    private static String pendingFilePath = "";

    public static void main(String[] args) {
        LoginGUI.main(args);
    }

    // =================================================================================
    // CÁC HÀM XỬ LÝ LOGIC
    // =================================================================================

    @SuppressWarnings("unchecked")
    private static void handleReceivedMessage(Message msg) {
        switch (msg.getType()) {
            case TEXT_MESSAGE:
                System.out.println("\n[" + msg.getSender() + "] nói: " + msg.getContent());
                break;

            case USER_LIST_RESPONSE:
                List<User> onlineUsers = (List<User>) msg.getPayload();
                System.out.print("\n[Hệ thống] Đang online: ");
                for (User u : onlineUsers) {
                    System.out.print(u.getUsername() + "  ");
                }
                System.out.println();
                break;

            case GET_IP_RESPONSE:
                // Nhận được IP từ Server, tiến hành đâm thẳng vào máy kia (P2P)
                User targetUser = (User) msg.getPayload();
                System.out.println("[Hệ thống] Đã nhận IP của " + targetUser.getUsername() + " (" + targetUser.getIpAddress() + ":" + targetUser.getPeerPort() + ")");
                System.out.println("[P2P] Bắt đầu truyền file...");

                // Kích hoạt hàm truyền byte file
                sendFileP2P(targetUser.getIpAddress(), targetUser.getPeerPort(), pendingFilePath);
                break;

            default:
                break;
        }
    }

    // Mở một ServerSocket ẩn để luôn sẵn sàng nhận file từ Client khác
    private static void startPeerServer(int peerPort) {
        Thread p2pServerThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(peerPort)) {
                System.out.println("[P2P] Máy bạn đã sẵn sàng nhận file ở Port " + peerPort);

                while (true) {
                    Socket senderSocket = serverSocket.accept();
                    System.out.println("\n[P2P] Có kết nối truyền file đang đến!");
                    receiveFileP2P(senderSocket); // Gọi hàm nhận byte file
                }
            } catch (IOException e) {
                System.out.println("[P2P] Lỗi mở cổng P2P: " + e.getMessage());
            }
        });
        p2pServerThread.setDaemon(true);
        p2pServerThread.start();
    }

    // Thuật toán GỬI file bằng DataOutputStream (Chạy trên Thread riêng để không block Console)
    private static void sendFileP2P(String ip, int port, String filePath) {
        new Thread(() -> {
            File file = new File(filePath);
            if (!file.exists()) {
                System.out.println("[Lỗi] File không tồn tại: " + filePath);
                return;
            }

            try (Socket socket = new Socket(ip, port);
                 DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                 FileInputStream fis = new FileInputStream(file)) {

                // 1. Gửi Tên file và Kích thước file trước (Header)
                dos.writeUTF(file.getName());
                dos.writeLong(file.length());

                // 2. Cắt file thành các mảng byte nhỏ (4KB) để truyền đi
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) > 0) {
                    dos.write(buffer, 0, bytesRead);
                }

                System.out.println("[P2P] => Đã gửi file thành công!");

            } catch (IOException e) {
                System.out.println("[P2P] => Lỗi gửi file: " + e.getMessage());
            }
        }).start();
    }

    // Thuật toán NHẬN file bằng DataInputStream
    private static void receiveFileP2P(Socket socket) {
        new Thread(() -> {
            try (DataInputStream dis = new DataInputStream(socket.getInputStream())) {

                // 1. Đọc Tên file và Kích thước file từ Header
                String fileName = dis.readUTF();
                long fileSize = dis.readLong();

                // Thêm tiền tố để tránh ghi đè file có sẵn
                String savePath = "nhan_duoc_" + fileName;

                // 2. Nhận các mảng byte và ráp thành file hoàn chỉnh
                try (FileOutputStream fos = new FileOutputStream(savePath)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    long totalRead = 0;

                    // Chỉ đọc đúng kích thước file để tránh kẹt luồng (blocking)
                    while (totalRead < fileSize && (bytesRead = dis.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalRead))) > 0) {
                        fos.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                    }
                }
                System.out.println("[P2P] <= Đã nhận file lưu tại: " + savePath);

            } catch (IOException e) {
                System.out.println("[P2P] <= Lỗi nhận file: " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                }
            }
        }).start();
    }
}