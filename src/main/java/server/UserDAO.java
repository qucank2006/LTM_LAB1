package server;

import model.*;
import util.AppConfig;

import java.sql.*;

public final class UserDAO {
    private Connection open() throws SQLException {
        return DriverManager.getConnection(AppConfig.databaseUrl(), AppConfig.databaseUsername(), AppConfig.databasePassword());
    }

    public void register(AuthRequest request) throws SQLException {
        try (Connection c = open()) {
            try (PreparedStatement p = c.prepareStatement("SELECT username, email FROM users WHERE username=? OR email=?")) {
                p.setString(1, request.username());
                p.setString(2, request.email());
                try (ResultSet r = p.executeQuery()) {
                    if (r.next()) throw new IllegalArgumentException(
                            request.username().equalsIgnoreCase(r.getString("username")) ? "Username đã tồn tại." : "Email đã tồn tại.");
                }
            }
            try (PreparedStatement p = c.prepareStatement("INSERT INTO users(username,email,password,display_name) VALUES(?,?,?,?)")) {
                p.setString(1, request.username());
                p.setString(2, request.email());
                p.setString(3, request.password());
                p.setString(4, request.displayName());
                p.executeUpdate();
            } catch (SQLIntegrityConstraintViolationException duplicate) {
                throw new IllegalArgumentException("Username hoặc Email đã tồn tại.");
            }
        }
    }

    public User login(AuthRequest request, String ip) throws SQLException {
        try (Connection c = open();
             PreparedStatement p = c.prepareStatement("SELECT id,username,email,password,display_name FROM users WHERE username=? OR email=?")) {
            p.setString(1, request.username());
            p.setString(2, request.username());
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new IllegalArgumentException("Tài khoản không tồn tại.");
                if (!request.password().equals(r.getString("password")))
                    throw new IllegalArgumentException("Mật khẩu không đúng.");
                User user = new User(r.getString("username"), ip, request.peerPort());
                user.setAccount(r.getLong("id"), r.getString("email"), r.getString("display_name"));
                return user;
            }
        }
    }
}
