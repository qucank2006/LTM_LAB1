package server;

import model.*;

import java.sql.SQLException;

public final class AuthService {
    private final UserDAO dao = new UserDAO();

    public AuthResult authenticate(AuthRequest r, boolean register, String ip) {
        try {
            if (r == null || r.username() == null || r.username().isBlank() || r.password() == null || r.password().isEmpty())
                throw new IllegalArgumentException("Vui lòng nhập tài khoản và mật khẩu.");
            if (r.username().length() > 255 || r.password().length() > 255)
                throw new IllegalArgumentException("Tài khoản hoặc mật khẩu quá dài.");
            if (register) {
                if (!r.username().matches("[A-Za-z0-9_]{3,50}"))
                    throw new IllegalArgumentException("Username gồm 3–50 chữ cái, số hoặc dấu gạch dưới.");
                if (r.email() == null || r.email().length() > 255 || !r.email().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
                    throw new IllegalArgumentException("Email không hợp lệ.");
                if (r.displayName() == null || r.displayName().isBlank() || r.displayName().length() > 100)
                    throw new IllegalArgumentException("Tên hiển thị phải có 1–100 ký tự.");
                if (r.password().length() < 8 || r.password().length() > 255)
                    throw new IllegalArgumentException("Mật khẩu phải có 8–255 ký tự.");
                dao.register(r);
                return new AuthResult(true, "Đăng ký thành công. Vui lòng đăng nhập.", null);
            }
            if (r.peerPort() < 9000 || r.peerPort() > 9999)
                throw new IllegalArgumentException("Cổng P2P không hợp lệ.");
            return new AuthResult(true, "Đăng nhập thành công.", dao.login(r, ip));
        } catch (IllegalArgumentException e) {
            return new AuthResult(false, e.getMessage(), null);
        } catch (SQLException e) {
            return new AuthResult(false, "MySQL chưa sẵn sàng hoặc schema chưa được migration. Vui lòng liên hệ người chạy Server.", null);
        }
    }
}
