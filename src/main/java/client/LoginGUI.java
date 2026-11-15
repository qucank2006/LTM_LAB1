package client;

import model.*;
import util.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public final class LoginGUI extends JFrame {
    private final JTextField username = new JTextField();
    private final JPasswordField password = new JPasswordField();
    private final JLabel status = new JLabel("Connecting…"), error = new JLabel(" ");
    private final JButton login = new JButton("Đăng nhập"), retry = new JButton("Retry"), register = new JButton("Chưa có tài khoản? Đăng ký");
    private ClientConnection connection;
    private boolean connecting, busy, handedOff;
    private final int peerPort = 9000 + new java.util.Random().nextInt(1000);

    public LoginGUI() {
        super("LAB1 Chat — Welcome Back");
        setSize(1100, 720);
        setMinimumSize(new Dimension(900, 600));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        JPanel center = new JPanel(new GridBagLayout());
        JPanel form = new JPanel(new GridLayout(0, 1, 8, 10));
        form.setBorder(new EmptyBorder(24, 32, 24, 32));
        form.setPreferredSize(new Dimension(480, 470));
        JLabel brand = new JLabel("LAB1 Chat", SwingConstants.CENTER);
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 28));
        form.add(brand);
        form.add(new JLabel("Welcome Back"));
        username.putClientProperty("JTextField.placeholderText", "Username / Email");
        password.putClientProperty("JTextField.placeholderText", "Password");
        form.add(username);
        JPanel passRow = new JPanel(new BorderLayout(8, 0));
        passRow.add(password);
        JToggleButton show = new JToggleButton("Hiện");
        char echo = password.getEchoChar();
        show.addActionListener(e -> {
            password.setEchoChar(show.isSelected() ? (char) 0 : echo);
            show.setText(show.isSelected() ? "Ẩn" : "Hiện");
        });
        passRow.add(show, BorderLayout.EAST);
        form.add(passRow);
        error.setForeground(ThemeManager.RED);
        form.add(error);
        form.add(login);
        form.add(register);
        JPanel state = new JPanel(new BorderLayout());
        state.add(status);
        state.add(retry, BorderLayout.EAST);
        form.add(state);
        center.add(form);
        add(center);
        getRootPane().setDefaultButton(login);
        login.addActionListener(e -> login());
        retry.addActionListener(e -> connect());
        register.addActionListener(e -> {
            if (connection != null && connection.isConnected()) new RegisterGUI(this, connection).setVisible(true);
        });
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(java.awt.event.WindowEvent e) {
                if (!handedOff && connection != null) connection.close();
            }
        });
        connect();
    }

    private void showError(String message) {
        error.setForeground(ThemeManager.RED);
        error.setText("<html><div style='width:360px'>" + message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</div></html>");
    }

    private void availability() {
        boolean online = connection != null && connection.isConnected();
        status.setForeground(online ? ThemeManager.GREEN : ThemeManager.MUTED);
        login.setEnabled(online && !busy && !connecting);
        register.setEnabled(online && !busy && !connecting);
        retry.setEnabled(!connecting && !busy && !online);
    }

    private void connect() {
        if (connecting || (connection != null && connection.isConnected())) return;
        connecting = true;
        status.setText("Connecting…");
        showError(" ");
        availability();
        ClientConnection next = new ClientConnection();
        connection = next;
        next.onDisconnect(() -> {
            if (connection == next && !handedOff) {
                status.setText("Offline / Disconnected");
                busy = false;
                availability();
            }
        });
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception {
                next.connect();
                return null;
            }

            protected void done() {
                connecting = false;
                try {
                    get();
                    status.setText(next.isConnected() ? "Online" : "Offline");
                } catch (Exception e) {
                    next.close();
                    status.setText("Offline");
                    error.setText("Không thể kết nối 172.20.10.4:8000. Kiểm tra Server và mạng.");
                }
                availability();
            }
        }.execute();
    }

    private void login() {
        if (busy || connection == null || !connection.isConnected()) return;
        String name = username.getText().trim(), secret = new String(password.getPassword());
        if (name.isEmpty() || secret.isEmpty()) {
            error.setText("Vui lòng nhập tài khoản và mật khẩu.");
            return;
        }
        busy = true;
        error.setText("Đang đăng nhập…");
        availability();
        connection.authenticate(new AuthRequest(name, null, null, secret, peerPort), false).whenComplete((result, failure) ->
                SwingUtilities.invokeLater(() -> {
                    busy = false;
                    password.setText("");
                    availability();
                    if (failure != null) {
                        error.setText("Yêu cầu thất bại. Hãy kết nối lại.");
                        return;
                    }
                    if (!result.success()) {
                        error.setText(result.message());
                        return;
                    }
                    handedOff = true;
                    ClientGUI chat = new ClientGUI(connection, result.user());
                    chat.setVisible(true);
                    dispose();
                }));
    }

    void registered(String name) {
        username.setText(name);
        password.setText("");
        error.setText("Đăng ký thành công. Vui lòng đăng nhập.");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ThemeManager.install();
            new LoginGUI().setVisible(true);
        });
    }
}
