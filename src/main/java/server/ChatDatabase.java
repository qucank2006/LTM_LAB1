package server;

import model.Message;
import util.AppConfig;

import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ChatDatabase {
    private static final String URL = AppConfig.databaseUrl();
    private static final String USER = AppConfig.databaseUsername();
    private static final String PASS = AppConfig.databasePassword();
    private boolean isMySQLAvailable = false;
    private final File fallbackFile = new File("chat_fallback_history.txt");

    public ChatDatabase() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(URL, USER, PASS)) {
                isMySQLAvailable = true;
                System.out.println("[ChatDatabase] Đã kết nối thành công tới MySQL (XAMPP) CSDL ltm_chat!");
            }
        } catch (Exception e) {
            System.out.println("[ChatDatabase] MySQL không hoạt động hoặc chưa bật XAMPP (" + e.getMessage() + "). Đang dùng chế độ File Fallback lưu trữ!");
            isMySQLAvailable = false;
        }
    }

    public boolean isMySQLAvailable() {
        return isMySQLAvailable;
    }

    public synchronized void saveMessage(Message msg) {
        if (isMySQLAvailable) {
            String sql = "INSERT INTO messages (id, sender, receiver, content, type, file_name, file_size, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, msg.getId() != null ? msg.getId() : java.util.UUID.randomUUID().toString().substring(0, 8));
                stmt.setString(2, msg.getSender());
                stmt.setString(3, msg.getReceiver());
                stmt.setString(4, msg.getContent());
                stmt.setString(5, msg.getType() != null ? msg.getType().name() : "TEXT_MESSAGE");
                stmt.setString(6, msg.getFileName());
                stmt.setLong(7, msg.getFileSize());
                stmt.setString(8, msg.getTimestamp());
                stmt.executeUpdate();
                return;
            } catch (SQLException e) {
                System.err.println("[ChatDatabase] Lỗi lưu MySQL: " + e.getMessage());
            }
        }
        // Fallback lưu ra File
        try (FileWriter fw = new FileWriter(fallbackFile, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            out.println((msg.getId() != null ? msg.getId() : "MSG") + "|" +
                    msg.getSender() + "|" +
                    msg.getReceiver() + "|" +
                    msg.getType() + "|" +
                    msg.getTimestamp() + "|" +
                    (msg.getContent() != null ? msg.getContent().replace("\n", " ") : ""));
        } catch (IOException e) {
            System.err.println("[ChatDatabase] Lỗi ghi file fallback: " + e.getMessage());
        }
    }
}
