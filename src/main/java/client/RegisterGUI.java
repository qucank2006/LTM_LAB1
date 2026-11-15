package client;

import model.*;
import util.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public final class RegisterGUI extends JDialog {
    private void showError(JLabel label, String message) {
        label.setText("<html><div style='width:380px'>" + message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</div></html>");
    }

    public RegisterGUI(LoginGUI owner, ClientConnection connection) {
        super(owner, "LAB1 Chat — Đăng ký", true);
        JPanel form = new JPanel(new GridLayout(0, 1, 8, 8));
        form.setBorder(new EmptyBorder(24, 32, 24, 32));
        JTextField username = new JTextField(), display = new JTextField(), email = new JTextField();
        JPasswordField password = new JPasswordField(), confirm = new JPasswordField();
        form.add(new JLabel("Tạo tài khoản LAB1 Chat"));
        form.add(new JLabel("Username (3–50 chữ cái, số, gạch dưới)"));
        form.add(username);
        form.add(new JLabel("Tên hiển thị"));
        form.add(display);
        form.add(new JLabel("Email"));
        form.add(email);
        form.add(new JLabel("Mật khẩu (8–255 ký tự)"));
        form.add(password);
        form.add(new JLabel("Xác nhận mật khẩu"));
        form.add(confirm);
        JLabel error = new JLabel(" ");
        error.setForeground(ThemeManager.RED);
        form.add(error);
        JButton submit = new JButton("Đăng ký"), back = new JButton("Quay về đăng nhập");
        form.add(submit);
        form.add(back);
        add(form);
        setSize(620, 690);
        setMinimumSize(new Dimension(580, 650));
        setLocationRelativeTo(owner);
        getRootPane().setDefaultButton(submit);
        back.addActionListener(e -> dispose());
        submit.addActionListener(e -> {
            String u = username.getText().trim(), d = display.getText().trim(), mail = email.getText().trim();
            String p = new String(password.getPassword()), c = new String(confirm.getPassword());
            if (!u.matches("[A-Za-z0-9_]{3,50}")) {
                showError(error, "Username không hợp lệ.");
                return;
            }
            if (d.isBlank() || d.length() > 100) {
                error.setText("Tên hiển thị phải có 1–100 ký tự.");
                return;
            }
            if (mail.length() > 255 || !mail.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
                error.setText("Email không hợp lệ.");
                return;
            }
            if (p.length() < 8 || p.length() > 255) {
                error.setText("Mật khẩu phải có 8–255 ký tự.");
                return;
            }
            if (!p.equals(c)) {
                error.setText("Xác nhận mật khẩu không trùng.");
                return;
            }
            submit.setEnabled(false);
            back.setEnabled(false);
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            error.setText("Đang đăng ký…");
            connection.authenticate(new AuthRequest(u, mail, d, p, 0), true).whenComplete((r, failure) -> SwingUtilities.invokeLater(() -> {
                submit.setEnabled(true);
                back.setEnabled(true);
                setDefaultCloseOperation(DISPOSE_ON_CLOSE);
                password.setText("");
                confirm.setText("");
                if (failure != null) error.setText("Mất kết nối. Quay về đăng nhập để Retry.");
                else if (!r.success()) error.setText(r.message());
                else {
                    owner.registered(u);
                    dispose();
                }
            }));
        });
    }
}
