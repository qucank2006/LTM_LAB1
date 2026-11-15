package client;

import model.Message;
import util.AppConfig;
import util.ThemeManager;
import model.MessageType;
import model.User;
import util.AudioUtil;
import util.BroadcastHandler;
import util.MulticastHandler;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class ClientGUI extends JFrame {
    private static final int SERVER_PORT = AppConfig.serverPort();

    // Các kênh chat hệ thống đặc biệt
    public static final String CH_ALL = "Kênh chung · TCP";
    public static final String CH_MULTICAST = "Multicast · UDP";
    public static final String CH_BROADCAST = "Broadcast · UDP";
    public static final String GROUP_PREFIX = "👥 Nhóm: ";

    private final ClientConnection connection;
    private final User account;
    private volatile boolean active = true;
    private ServerSocket peerServer;
    private final java.util.Set<Socket> peerSockets = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.ExecutorService network = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "chat-media");
        t.setDaemon(true);
        return t;
    });
    private String myUsername;
    private int myPeerPort;
    private String pendingFilePath = "";
    private String pendingFileReceiver;

    // Handlers UDP
    private MulticastHandler multicastHandler;
    private BroadcastHandler broadcastHandler;

    // Trạng thái thu âm
    private boolean isRecording = false;

    // Kho lưu trữ dữ liệu Audio & File theo ID để bấm nghe/mở lại bất kỳ lúc nào (kiểu Messenger)
    private final Map<String, byte[]> audioClipsStore = new HashMap<>();
    private final Map<String, File> fileLinksStore = new HashMap<>();

    // Quản lý Nhóm chat: Tên kênh nhóm -> Danh sách thành viên
    private final Map<String, Set<String>> groupMembersMap = new LinkedHashMap<>();
    private final List<String> currentOnlineUsers = new ArrayList<>();

    // Thành phần UI
    private ChatTimeline timeline;
    private JTextArea messageField;
    private JButton btnSendText, btnSendFile, btnConnect, btnVoice, btnVideo, btnEmoji, btnCreateGroup;
    private JLabel connectionStatus;
    private JLabel lblChatTitle;
    private String receivedTimestamp;
    private final Map<String, String> displayNames = new HashMap<>();
    private JPanel chatHeaderPanel;

    // Danh sách User online & Nhóm và Bộ nhớ lịch sử chat
    private DefaultListModel<String> listModel;
    private JList<String> userList;

    // --- BỘ MÀU SẮC PREMIUM DARK MODE ---
    private final Color BG_TOP_BAR = ThemeManager.CRUST;       // Đen sâu cho Header
    private final Color BG_SIDEBAR = ThemeManager.MANTLE;       // Xám đậm cho Danh bạ
    private final Color BG_CHAT = ThemeManager.BASE;          // Xám nhạt cho khung Chat
    private final Color BG_INPUT = ThemeManager.SURFACE;         // Màu nền ô nhập chữ
    private final Color ACCENT_COLOR = ThemeManager.MAUVE;   // Xanh Tím Discord/Messenger
    private final Color HOVER_COLOR = ThemeManager.SURFACE;      // Xám sáng khi hover
    private final Color TEXT_PRIMARY = ThemeManager.TEXT;  // Chữ trắng sáng
    private final Color TEXT_MUTED = ThemeManager.MUTED;    // Chữ xám mờ

    // Composite Font chuẩn của Java: hiển thị sắc nét cả Tiếng Việt có dấu và Emoji
    private final Font MAIN_FONT = getCompositeFont(Font.PLAIN, 14);
    private final Font BOLD_FONT = getCompositeFont(Font.BOLD, 14);

    private enum BubbleAlign {
        LEFT,   // Người nhận (bên trái)
        RIGHT,  // Người gửi (bên phải)
        CENTER  // Thông báo hệ thống (ở giữa)
    }

    private static Font getCompositeFont(int style, int size) {
        return new Font("Segoe UI", style, size);
    }

    public ClientGUI(ClientConnection connection, User account) {
        this.connection = connection;
        this.account = account;
        myUsername = account.getUsername();
        myPeerPort = account.getPeerPort();
        setTitle("Lab 1 Chat - TCP/UDP, Multicast, Broadcast, Group, Audio/Video & P2P");
        setSize(1100, 720);
        setMinimumSize(new Dimension(900, 600));
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent e) {
                shutdown(false);
            }
        });
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_CHAT);

        UIManager.put("Label.font", MAIN_FONT);
        UIManager.put("TextField.font", MAIN_FONT);
        UIManager.put("Button.font", BOLD_FONT);

        JPanel mainPanel = new JPanel(new BorderLayout());
        add(mainPanel);

        // ==========================================
        // 1. TOP PANEL: KHU VỰC ĐĂNG NHẬP & TẠO NHÓM
        // ==========================================
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(BG_TOP_BAR);
        topPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.decode("#11111B")));

        JPanel topLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 12));
        topLeft.setOpaque(false);

        JLabel brand = new JLabel("LAB1 Chat");
        brand.setForeground(ACCENT_COLOR);
        brand.setFont(BOLD_FONT);
        connectionStatus = new JLabel("Online");
        connectionStatus.setForeground(ThemeManager.GREEN);
        topLeft.add(brand);
        topLeft.add(connectionStatus);
        btnConnect = createStyledButton("Đăng xuất", BG_INPUT, TEXT_PRIMARY);
        topLeft.add(btnConnect);

        JPanel topRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 12));
        topRight.setOpaque(false);
        btnCreateGroup = createStyledButton("Tạo nhóm", Color.decode("#585B70"), ThemeManager.TEXT);
        btnCreateGroup.setEnabled(false);
        btnCreateGroup.setToolTipText("Tạo phòng chat nhóm với các thành viên đang online");
        topRight.add(btnCreateGroup);
        JButton info = createStyledButton("Thông tin nhóm", BG_INPUT, TEXT_PRIMARY);
        info.addActionListener(e -> {
            String target = userList.getSelectedValue();
            if (target == null || !groupMembersMap.containsKey(target)) {
                JOptionPane.showMessageDialog(this, "Chọn nhóm để xem thành viên.");
                return;
            }
            JTextArea members = new JTextArea(String.join("\n", groupMembersMap.get(target)));
            members.setEditable(false);
            members.setRows(Math.min(12, groupMembersMap.get(target).size()));
            JOptionPane.showMessageDialog(this, new JScrollPane(members), target.replace(GROUP_PREFIX, "Nhóm: "), JOptionPane.INFORMATION_MESSAGE);
        });
        topRight.add(info);

        topPanel.add(topLeft, BorderLayout.WEST);
        topPanel.add(topRight, BorderLayout.EAST);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // ==========================================
        // 2. LEFT PANEL: DANH SÁCH KÊNH, NHÓM & BẠN BÈ
        // ==========================================
        listModel = new DefaultListModel<>();
        listModel.addElement(CH_ALL);
        listModel.addElement(CH_MULTICAST);
        listModel.addElement(CH_BROADCAST);

        userList = new JList<>(listModel);
        userList.setBackground(BG_SIDEBAR);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.setCellRenderer(new DarkModeUserRenderer());

        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setPreferredSize(new Dimension(270, 0));
        userScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        userScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, ThemeManager.CRUST));
        userScroll.getVerticalScrollBar().setUnitIncrement(16);
        userScroll.getViewport().setBackground(BG_SIDEBAR);

        // ==========================================
        // 3. CENTER PANEL: KHU VỰC CHAT (MESSENGER STYLE)
        // ==========================================
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBackground(BG_CHAT);

        // --- Header của khung chat ---
        chatHeaderPanel = new JPanel(new BorderLayout());
        chatHeaderPanel.setBackground(BG_CHAT);
        chatHeaderPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.decode("#1E1E2E")));

        JPanel headerLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 12));
        headerLeft.setOpaque(false);
        lblChatTitle = new JLabel("Chọn một kênh, nhóm hoặc bạn chat bên trái để bắt đầu");
        lblChatTitle.setFont(getCompositeFont(Font.BOLD, 16));
        lblChatTitle.setForeground(TEXT_MUTED);
        headerLeft.add(lblChatTitle);

        chatHeaderPanel.add(headerLeft, BorderLayout.CENTER);
        chatPanel.add(chatHeaderPanel, BorderLayout.NORTH);

        // --- Khung hiển thị tin nhắn HTML (Hỗ trợ click Audio & File) ---
        timeline = new ChatTimeline(this::handleBubbleLinkClick);
        JScrollPane chatScroll = new JScrollPane(timeline);
        chatScroll.setBorder(BorderFactory.createEmptyBorder());
        chatScroll.getViewport().setBackground(BG_CHAT);
        chatScroll.getVerticalScrollBar().setUnitIncrement(16);
        chatPanel.add(chatScroll, BorderLayout.CENTER);

        // --- Khung nhập tin nhắn & nút chức năng ---
        JPanel bottomChatPanel = new JPanel(new BorderLayout(8, 8));
        bottomChatPanel.setBackground(BG_CHAT);
        bottomChatPanel.setBorder(new EmptyBorder(12, 14, 12, 14));

        messageField = new JTextArea(2, 20) {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && !isFocusOwner()) {
                    g.setColor(TEXT_MUTED);
                    g.setFont(getFont());
                    g.drawString("Nhập tin nhắn…", 12, 24);
                }
            }
        };
        messageField.setLineWrap(true);
        messageField.setWrapStyleWord(true);
        messageField.setFont(MAIN_FONT);
        messageField.setBackground(BG_INPUT);
        messageField.setForeground(TEXT_PRIMARY);
        messageField.setCaretColor(TEXT_PRIMARY);
        messageField.setBorder(new EmptyBorder(8, 12, 8, 12));
        messageField.setToolTipText("Enter để gửi · Shift + Enter để xuống dòng");
        messageField.setEnabled(false);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttonPanel.setBackground(BG_CHAT);

        btnEmoji = createStyledButton("Emoji", BG_INPUT, Color.decode("#FAB387"));
        btnEmoji.setFont(getCompositeFont(Font.PLAIN, 17));
        btnEmoji.setToolTipText("Chọn Icon biểu cảm (32 Emojis)");
        btnEmoji.setEnabled(false);

        btnVoice = createStyledButton("Thu âm", BG_INPUT, Color.decode("#F38BA8"));
        btnVoice.setToolTipText("Bấm để bắt đầu thu âm, bấm lần nữa để dừng và gửi");
        btnVoice.setEnabled(false);

        btnVideo = createStyledButton("Video", BG_INPUT, Color.decode("#CBA6F7"));
        btnVideo.setToolTipText("Gửi tệp video (.mp4/.avi) tới đối tác");
        btnVideo.setEnabled(false);

        btnSendFile = createStyledButton("Đính kèm", BG_INPUT, TEXT_PRIMARY);
        btnSendFile.setToolTipText("Truyền file P2P trực tiếp");
        btnSendFile.setEnabled(false);

        btnSendText = createStyledButton("Gửi", ACCENT_COLOR, ThemeManager.CRUST);
        btnSendText.setEnabled(false);

        buttonPanel.add(btnEmoji);
        buttonPanel.add(btnVoice);
        buttonPanel.add(btnVideo);
        buttonPanel.add(btnSendFile);
        buttonPanel.add(btnSendText);

        bottomChatPanel.add(new JScrollPane(messageField), BorderLayout.CENTER);
        bottomChatPanel.add(buttonPanel, BorderLayout.SOUTH);
        chatPanel.add(bottomChatPanel, BorderLayout.SOUTH);

        JPanel sidebar = new JPanel(new BorderLayout(0, 12));
        sidebar.setBackground(BG_SIDEBAR);
        sidebar.setPreferredSize(new Dimension(270, 0));
        JLabel profile = new JLabel("<html><b>LAB1 Chat</b><br><br>[" + escapeHtml(myUsername.substring(0, 1).toUpperCase()) + "] " + escapeHtml(account.getDisplayName()) + "<br>@" + escapeHtml(myUsername) + "</html>");
        profile.setBorder(new EmptyBorder(18, 16, 18, 16));
        profile.setForeground(TEXT_PRIMARY);
        sidebar.add(profile, BorderLayout.NORTH);
        sidebar.add(userScroll, BorderLayout.CENTER);
        sidebar.add(btnConnect, BorderLayout.SOUTH);
        mainPanel.add(sidebar, BorderLayout.WEST);
        mainPanel.add(chatPanel, BorderLayout.CENTER);

        // ==========================================
        // 4. BẮT SỰ KIỆN UI & PLACEHOLDER
        // ==========================================
        messageField.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent evt) {
                if (messageField.getText().equals("Nhập tin nhắn...")) {
                    messageField.setText("");
                    messageField.setForeground(TEXT_PRIMARY);
                }
            }
        });

        btnConnect.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Đăng xuất khỏi LAB1 Chat?", "Đăng xuất", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION)
                shutdown(true);
        });
        btnCreateGroup.addActionListener(e -> showCreateGroupDialog());
        btnSendText.addActionListener(e -> sendTextMessage());
        messageField.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "send");
        messageField.getActionMap().put("send", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                sendTextMessage();
            }
        });
        messageField.getInputMap().put(KeyStroke.getKeyStroke("shift ENTER"), "insert-break");
        btnSendFile.addActionListener(e -> sendFileAction());
        btnVoice.addActionListener(e -> toggleVoiceRecording());
        btnVideo.addActionListener(e -> sendVideoAction());
        btnEmoji.addActionListener(e -> showEmojiPicker());

        userList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedTarget = userList.getSelectedValue();
                if (selectedTarget != null) {
                    if (selectedTarget.startsWith(GROUP_PREFIX) && groupMembersMap.containsKey(selectedTarget)) {
                        lblChatTitle.setText(selectedTarget.replace(GROUP_PREFIX, "Nhóm: ") + "  (" + groupMembersMap.get(selectedTarget).size() + " thành viên · Group)");
                    } else {
                        lblChatTitle.setText(selectedTarget.replace(GROUP_PREFIX, "Nhóm: ") + (currentOnlineUsers.contains(selectedTarget) ? " · Riêng / P2P" : ""));
                    }
                    if (selectedTarget.equals(CH_ALL))
                        lblChatTitle.setText("Kênh chung · TCP · " + (currentOnlineUsers.size() + 1) + " Online");
                    if (selectedTarget.equals(CH_MULTICAST)) lblChatTitle.setText("Multicast · UDP 230.0.0.1:8888");
                    if (selectedTarget.equals(CH_BROADCAST)) lblChatTitle.setText("Broadcast · UDP 9999");
                    lblChatTitle.setToolTipText(lblChatTitle.getText());
                    lblChatTitle.setForeground(TEXT_PRIMARY);
                    messageField.setEnabled(active);
                    btnSendText.setEnabled(active);
                    btnSendFile.setEnabled(active);
                    btnVoice.setEnabled(active);
                    btnVideo.setEnabled(active);
                    btnEmoji.setEnabled(active);

                    resetChatPaneDocument();

                }
            }
        });

        multicastHandler = new MulticastHandler(msg -> SwingUtilities.invokeLater(() -> {
            if (active) handleReceivedMessage(msg);
        }));
        broadcastHandler = new BroadcastHandler(msg -> SwingUtilities.invokeLater(() -> {
            if (active) handleReceivedMessage(msg);
        }));
        connection.onMessage(this::handleReceivedMessage);
        connection.onDisconnect(() -> {
            if (!active) return;
            shutdown(true);
        });
        network.execute(() -> {
            if (!active) return;
            startPeerServer(myPeerPort);
            for (Runnable start : new Runnable[]{multicastHandler::start, broadcastHandler::start}) {
                try {
                    if (active) start.run();
                } catch (RuntimeException e) {
                    SwingUtilities.invokeLater(() -> {
                        if (active)
                            JOptionPane.showMessageDialog(this, e.getMessage(), "UDP", JOptionPane.WARNING_MESSAGE);
                    });
                }
            }
            if (!active) {
                multicastHandler.stop();
                broadcastHandler.stop();
            }
        });
        btnCreateGroup.setEnabled(true);
        userList.setSelectedIndex(0);
        sendTcp(new Message(MessageType.GET_USER_LIST, myUsername, "server"));
    }

    // ==========================================
    // XỬ LÝ CLICK VÀO BONG BÓNG GHI ÂM HOẶC FILE
    // ==========================================
    private void handleBubbleLinkClick(String href) {
        if (href == null) return;

        // 1. Click vào bong bóng ghi âm kiểu Messenger -> Phát âm thanh ngay (cả người gửi & nhận)
        if (href.startsWith("audio:")) {
            String audioId = href.substring("audio:".length());
            byte[] wavBytes = audioClipsStore.get(audioId);
            if (wavBytes != null && wavBytes.length > 0) {
                new Thread(() -> AudioUtil.playAudio(wavBytes)).start();
            } else {
                JOptionPane.showMessageDialog(this, "Không tìm thấy dữ liệu đoạn ghi âm này!");
            }
        }
        // 2. Click vào bong bóng File / Video -> Mở thư mục lưu hoặc tải lưu sang vị trí mới
        else if (href.startsWith("file:")) {
            String fileId = href.substring("file:".length());
            File savedFile = fileLinksStore.get(fileId);
            if (savedFile != null && savedFile.exists()) {
                String[] options = {"📂 Mở Thư Mục Lưu", "💾 Tải Về Nơi Khác (Save As)", "▶ Mở Tệp Ngay"};
                int choice = JOptionPane.showOptionDialog(
                        this,
                        "Tên tệp: " + savedFile.getName() + "\nVị trí hiện tại: " + savedFile.getAbsolutePath(),
                        "Quản Lý Tệp Đính Kèm",
                        JOptionPane.DEFAULT_OPTION,
                        JOptionPane.INFORMATION_MESSAGE,
                        null,
                        options,
                        options[0]
                );

                try {
                    if (choice == 0) {
                        // Mở Windows Explorer và bôi đen đúng file đã lưu
                        if (System.getProperty("os.name").toLowerCase().contains("win")) {
                            Runtime.getRuntime().exec(new String[]{"explorer.exe", "/select,", savedFile.getAbsolutePath()});
                        } else if (Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().open(savedFile.getParentFile());
                        }
                    } else if (choice == 1) {
                        // Cho phép tải lưu về thư mục tùy chọn
                        JFileChooser chooser = new JFileChooser();
                        chooser.setSelectedFile(new File(savedFile.getName()));
                        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                            File dest = chooser.getSelectedFile();
                            Files.copy(savedFile.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            JOptionPane.showMessageDialog(this, "✅ Đã tải tệp về:\n" + dest.getAbsolutePath());
                        }
                    } else if (choice == 2 && Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(savedFile);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Không thể mở tệp/thư mục: " + ex.getMessage());
                }
            } else {
                JOptionPane.showMessageDialog(this, "Tệp không còn tồn tại trên ổ đĩa!");
            }
        }
    }

    // ==========================================
    // HỘP THOẠI TẠO NHÓM CHAT
    // ==========================================
    private void showCreateGroupDialog() {
        if (!active || !connection.isConnected()) return;
        if (currentOnlineUsers.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Hiện chưa có người dùng nào khác đang online để tạo nhóm!");
            return;
        }

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setPreferredSize(new Dimension(340, 260));

        JPanel namePanel = new JPanel(new BorderLayout(5, 5));
        namePanel.add(new JLabel("Tên nhóm chat:"), BorderLayout.NORTH);
        JTextField txtGroupName = new JTextField("Nhóm Lập Trình Mạng");
        namePanel.add(txtGroupName, BorderLayout.CENTER);
        panel.add(namePanel, BorderLayout.NORTH);

        JPanel memberPanel = new JPanel();
        memberPanel.setLayout(new BoxLayout(memberPanel, BoxLayout.Y_AXIS));
        List<JCheckBox> checkBoxes = new ArrayList<>();
        for (String user : currentOnlineUsers) {
            JCheckBox cb = new JCheckBox(user, true);
            checkBoxes.add(cb);
            memberPanel.add(cb);
        }
        JScrollPane scrollMembers = new JScrollPane(memberPanel);
        scrollMembers.setBorder(BorderFactory.createTitledBorder("Chọn thành viên tham gia:"));
        panel.add(scrollMembers, BorderLayout.CENTER);

        int res = JOptionPane.showConfirmDialog(this, panel, "Tạo Nhóm Chat Mới", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION) {
            String groupName = txtGroupName.getText().trim().replace("|", "-").replace("]", "").replace(",", "-");
            if (groupName.isEmpty() || groupName.length() > 100) {
                JOptionPane.showMessageDialog(this, "Tên nhóm phải có 1–100 ký tự!");
                return;
            }

            Set<String> members = new LinkedHashSet<>();
            members.add(myUsername);
            for (JCheckBox cb : checkBoxes) {
                if (cb.isSelected()) {
                    members.add(cb.getText());
                }
            }

            if (members.size() < 2) {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn ít nhất 1 thành viên khác!");
                return;
            }

            String groupKey = GROUP_PREFIX + groupName;
            if (groupMembersMap.containsKey(groupKey)) {
                JOptionPane.showMessageDialog(this, "Tên nhóm đã tồn tại trên Client này.");
                return;
            }
            groupMembersMap.put(groupKey, members);
            if (!listModel.contains(groupKey)) {
                listModel.add(3, groupKey); // Đưa nhóm lên ngay dưới 3 kênh hệ thống
            }
            userList.setSelectedValue(groupKey, true);

            String memberListStr = String.join(", ", members);
            saveAndShowBubble(groupKey, "", "👥 Bạn đã tạo nhóm \"" + groupName + "\" (" + memberListStr + ")", BubbleAlign.CENTER);

            // Gửi gói tin thông báo tạo nhóm tới các thành viên
            try {
                String encoded = "[GROUP_CHAT|" + groupName + "|" + String.join(",", members) + "]👋 Chào mọi người, mình vừa tạo nhóm!";
                Message msg = new Message(MessageType.TEXT_MESSAGE, myUsername, "All", encoded);
                sendTcp(msg);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void resetChatPaneDocument() {
        if (timeline != null) timeline.showChannel(userList.getSelectedValue());
    }

    private JTextField createDarkTextField(String text, int columns) {
        JTextField tf = new JTextField(text, columns);
        tf.setFont(MAIN_FONT);
        tf.setBackground(BG_INPUT);
        tf.setForeground(TEXT_PRIMARY);
        tf.setCaretColor(ThemeManager.TEXT);
        tf.setBorder(new EmptyBorder(8, 12, 8, 12));
        return tf;
    }

    private JButton createStyledButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setFont(getCompositeFont(Font.BOLD, 13));
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setBorderPainted(true);
        btn.setOpaque(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(8, 14, 8, 14));
        return btn;
    }

    // ==========================================
    // RENDERER DANH SÁCH KÊNH, NHÓM & USER
    // ==========================================
    private class DarkModeUserRenderer extends JPanel implements ListCellRenderer<String> {
        private final JLabel lblAvatar, lblName;
        private String targetName = "";

        public DarkModeUserRenderer() {
            setLayout(new BorderLayout(12, 0));
            setBorder(new EmptyBorder(10, 14, 10, 14));
            setOpaque(true);

            lblAvatar = new JLabel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                    Color circleColor = ACCENT_COLOR;
                    String symbol = "?";

                    if (targetName.equals(CH_ALL)) {
                        circleColor = Color.decode("#FAB387");
                        symbol = "T";
                    } else if (targetName.equals(CH_MULTICAST)) {
                        circleColor = Color.decode("#74C7EC");
                        symbol = "M";
                    } else if (targetName.equals(CH_BROADCAST)) {
                        circleColor = Color.decode("#CBA6F7");
                        symbol = "B";
                    } else if (targetName.startsWith(GROUP_PREFIX)) {
                        circleColor = Color.decode("#585B70");
                        symbol = "G";
                    } else {
                        symbol = targetName.isEmpty() ? "?" : targetName.substring(0, 1).toUpperCase();
                    }

                    g2.setColor(circleColor);
                    g2.fillOval(0, 0, 36, 36);

                    g2.setColor(ThemeManager.TEXT);
                    g2.setFont(getCompositeFont(Font.BOLD, 15));
                    FontMetrics fm = g2.getFontMetrics();
                    int x = (36 - fm.stringWidth(symbol)) / 2;
                    int y = ((36 - fm.getHeight()) / 2) + fm.getAscent();
                    g2.drawString(symbol, x, y);
                    g2.dispose();
                }
            };
            lblAvatar.setPreferredSize(new Dimension(36, 36));

            lblName = new JLabel();
            lblName.setFont(BOLD_FONT);

            add(lblAvatar, BorderLayout.WEST);
            add(lblName, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value, int index, boolean isSelected, boolean cellHasFocus) {
            this.targetName = value != null ? value : "";
            lblName.setText(displayNames.getOrDefault(this.targetName, this.targetName).replace(GROUP_PREFIX, "Nhóm: "));
            setToolTipText(this.targetName);

            if (isSelected) {
                setBackground(HOVER_COLOR);
                lblName.setForeground(ACCENT_COLOR);
            } else {
                setBackground(BG_SIDEBAR);
                boolean isSpecialChannel = this.targetName.equals(CH_ALL)
                        || this.targetName.equals(CH_MULTICAST)
                        || this.targetName.equals(CH_BROADCAST)
                        || this.targetName.startsWith(GROUP_PREFIX);
                lblName.setForeground(isSpecialChannel ? TEXT_PRIMARY : TEXT_MUTED);
            }
            return this;
        }
    }

    // ==========================================
    // HỆ THỐNG RENDER BONG BÓNG CHAT + THỜI GIAN + MEDIA CLICKABLE
    // ==========================================
    private String getCurrentTimeStr() {
        return new SimpleDateFormat("HH:mm").format(new Date());
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br>");
    }

    // Giao diện bong bóng Ghi âm kiểu Messenger (cả 2 bên đều bấm vào nghe được)
    private String createVoiceBubbleHtml(String audioId, int byteLength, BubbleAlign align) {
        int seconds = Math.max(1, byteLength / 32000); // Ước tính thời lượng (16kHz 16-bit mono ~ 32KB/s)
        String durationStr = String.format("0:%02d", seconds);
        String linkColor = (align == BubbleAlign.RIGHT) ? "#CDD6F4" : "#89B4FA";
        String subColor = (align == BubbleAlign.RIGHT) ? "#BAC2DE" : "#A6ADC8";

        return "<a href='audio:" + audioId + "' style='text-decoration:none; color:" + linkColor + "; font-weight:bold;'>" +
                "▶ &nbsp;▂▄▆█▇▅▃▂▆█▅▃▂&nbsp; " + durationStr +
                "</a>" +
                "<div style='font-size:11px; color:" + subColor + "; margin-top:3px;'>" +
                "🎙 Tin nhắn thoại (" + (byteLength / 1024) + " KB) — Bấm vào để nghe" +
                "</div>";
    }

    // Giao diện bong bóng File đính kèm (bấm vào để mở thư mục lưu hoặc tải về)
    private String createFileBubbleHtml(String fileId, String fileName, long fileSizeBytes, String statusHint, BubbleAlign align) {
        String linkColor = (align == BubbleAlign.RIGHT) ? "#CDD6F4" : "#89B4FA";
        String subColor = (align == BubbleAlign.RIGHT) ? "#BAC2DE" : "#A6ADC8";
        long sizeKB = Math.max(1, fileSizeBytes / 1024);

        return "<a href='file:" + fileId + "' style='text-decoration:none; color:" + linkColor + "; font-weight:bold;'>" +
                "📄 " + escapeHtml(fileName) + " (" + sizeKB + " KB)" +
                "</a>" +
                "<div style='font-size:11px; color:" + subColor + "; margin-top:3px;'>" +
                escapeHtml(statusHint) + " — Bấm vào để mở thư mục / tải về" +
                "</div>";
    }

    private void saveAndShowBubble(String targetKey, String senderLabel, String plainText, BubbleAlign align) {
        saveAndShowRichBubble(targetKey, senderLabel, escapeHtml(plainText), align);
    }

    private void saveAndShowRichBubble(String targetKey, String senderLabel, String rawHtmlContent, BubbleAlign align) {
        if (!active) return;
        timeline.append(targetKey, displayNames.getOrDefault(senderLabel, senderLabel), rawHtmlContent,
                receivedTimestamp == null ? getCurrentTimeStr() : receivedTimestamp, align == BubbleAlign.RIGHT, align == BubbleAlign.CENTER);
    }

    // ==========================================
    // LOGIC KẾT NỐI VÀ NHẬN/GỬI TIN NHẮN
    // ==========================================
    private void shutdown(boolean returnToLogin) {
        if (!active) return;
        active = false;
        connectionStatus.setText("Disconnected");
        messageField.setEnabled(false);
        for (JButton button : new JButton[]{btnSendText, btnSendFile, btnVoice, btnVideo, btnEmoji, btnCreateGroup})
            button.setEnabled(false);
        connection.onDisconnect(() -> {
        });
        connection.send(new Message(MessageType.LOGOUT, myUsername, "server")).orTimeout(1, java.util.concurrent.TimeUnit.SECONDS).whenComplete((r, e) -> connection.close());
        network.shutdownNow();
        multicastHandler.stop();
        broadcastHandler.stop();
        if (isRecording) {
            AudioUtil.stopRecording();
            isRecording = false;
        }
        try {
            if (peerServer != null) peerServer.close();
        } catch (IOException ignored) {
        }
        for (Socket peer : peerSockets)
            try {
                peer.close();
            } catch (IOException ignored) {
            }
        audioClipsStore.clear();
        fileLinksStore.clear();
        timeline.clear();
        groupMembersMap.clear();
        dispose();
        if (returnToLogin) new LoginGUI().setVisible(true);
    }

    private void sendUdp(Message message, boolean multicast) {
        if (!active || !connection.isConnected()) return;
        network.execute(() -> {
            if (!active) return;
            try {
                if (multicast) multicastHandler.sendMulticast(message);
                else broadcastHandler.sendBroadcast(message);
            } catch (RuntimeException e) {
                SwingUtilities.invokeLater(() -> {
                    if (active) JOptionPane.showMessageDialog(this, e.getMessage(), "UDP", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private void sendTcp(Message message) {
        if (!active || !connection.isConnected()) {
            SwingUtilities.invokeLater(() -> {
                if (active) JOptionPane.showMessageDialog(this, "Disconnected. Vui lòng đăng nhập lại.");
            });
            return;
        }
        if (message.getReceiver() != null && message.getReceiver().startsWith(GROUP_PREFIX))
            message.setPayload(new LinkedHashSet<>(groupMembersMap.getOrDefault(message.getReceiver(), Collections.emptySet())));
        connection.send(message).whenComplete((v, e) -> {
            if (e != null) SwingUtilities.invokeLater(() -> {
                if (active) JOptionPane.showMessageDialog(this, "Không thể gửi dữ liệu. Kết nối đã mất.");
            });
        });
    }

    @SuppressWarnings("unchecked")
    private void handleReceivedMessage(Message msg) {
        if (!active || msg == null) return;
        String sender = msg.getSender();
        if (sender != null && sender.equals(myUsername)) return;

        receivedTimestamp = msg.getTimestamp();
        try {
            switch (msg.getType()) {
                case ERROR:
                    pendingFileReceiver = null;
                    JOptionPane.showMessageDialog(this, msg.getContent(), "Server", JOptionPane.ERROR_MESSAGE);
                    break;
                case TEXT_MESSAGE:
                    String content = msg.getContent() != null ? msg.getContent() : "";
                    // Kiểm tra nếu là tin nhắn thuộc Nhóm Chat ([GROUP_CHAT|TênNhóm|tv1,tv2]nội_dung)
                    if (content.startsWith("[GROUP_CHAT|")) {
                        int endBracket = content.indexOf(']');
                        if (endBracket > 0) {
                            String header = content.substring("[GROUP_CHAT|".length(), endBracket);
                            String realText = content.substring(endBracket + 1);
                            String[] parts = header.split("\\|", 2);
                            if (parts.length == 2) {
                                String groupName = parts[0];
                                Set<String> members = new LinkedHashSet<>(Arrays.asList(parts[1].split(",")));
                                if (members.contains(myUsername)) {
                                    String groupKey = GROUP_PREFIX + groupName;
                                    groupMembersMap.put(groupKey, members);
                                    if (!listModel.contains(groupKey)) {
                                        listModel.add(3, groupKey);
                                    }
                                    saveAndShowBubble(groupKey, sender, realText, BubbleAlign.LEFT);
                                }
                            }
                        }
                    } else if ("All".equalsIgnoreCase(msg.getReceiver())) {
                        saveAndShowBubble(CH_ALL, sender + " (Chung)", content, BubbleAlign.LEFT);
                    } else {
                        saveAndShowBubble(sender, sender, content, BubbleAlign.LEFT);
                    }
                    break;

                case MULTICAST_MESSAGE:
                    saveAndShowBubble(CH_MULTICAST, sender + " [UDP Multicast]", msg.getContent(), BubbleAlign.LEFT);
                    break;

                case BROADCAST_MESSAGE:
                    saveAndShowBubble(CH_BROADCAST, sender + " [UDP Broadcast]", msg.getContent(), BubbleAlign.LEFT);
                    break;

                case AUDIO_MESSAGE:
                    byte[] audioData = msg.getFileData();
                    if (audioData != null && audioData.length > 0) {
                        String audioId = UUID.randomUUID().toString();
                        audioClipsStore.put(audioId, audioData);
                        String voiceHtml = createVoiceBubbleHtml(audioId, audioData.length, BubbleAlign.LEFT);

                        String targetTab = resolveIncomingTargetTab(msg, sender);
                        saveAndShowRichBubble(targetTab, sender, voiceHtml, BubbleAlign.LEFT);
                    }
                    break;

                case VIDEO_MESSAGE:
                    network.execute(() -> {
                        if (!active) return;
                        try {
                            String name = msg.getFileName() == null ? "video.mp4" : new File(msg.getFileName()).getName();
                            File saved = new File("Received_Video_" + UUID.randomUUID() + "_" + name).getAbsoluteFile();
                            if (msg.getFileData() == null) return;
                            Files.write(saved.toPath(), msg.getFileData());
                            SwingUtilities.invokeLater(() -> {
                                if (!active) return;
                                String id = UUID.randomUUID().toString();
                                fileLinksStore.put(id, saved);
                                timeline.append(resolveIncomingTargetTab(msg, sender), displayNames.getOrDefault(sender, sender),
                                        createFileBubbleHtml(id, name, saved.length(), "Đã lưu tại: " + saved.getParent(), BubbleAlign.LEFT),
                                        msg.getTimestamp() == null ? getCurrentTimeStr() : msg.getTimestamp(), false, false);
                            });
                        } catch (IOException e) {
                            SwingUtilities.invokeLater(() -> {
                                if (active) JOptionPane.showMessageDialog(this, "Không thể lưu tệp nhận.");
                            });
                        }
                    });
                    break;

                case USER_LIST_RESPONSE:
                    List<User> onlineUsers = (List<User>) msg.getPayload();
                    String currentSelected = userList.getSelectedValue();

                    currentOnlineUsers.clear();
                    displayNames.clear();
                    for (User online : onlineUsers) displayNames.put(online.getUsername(), online.getDisplayName());
                    listModel.clear();
                    listModel.addElement(CH_ALL);
                    listModel.addElement(CH_MULTICAST);
                    listModel.addElement(CH_BROADCAST);

                    // Giữ lại các nhóm chat đã tham gia
                    for (String groupKey : groupMembersMap.keySet()) {
                        listModel.addElement(groupKey);
                    }

                    for (User u : onlineUsers) {
                        if (!u.getUsername().equals(myUsername)) {
                            currentOnlineUsers.add(u.getUsername());
                            listModel.addElement(u.getUsername());
                        }
                    }

                    if (currentSelected != null && listModel.contains(currentSelected)) {
                        userList.setSelectedValue(currentSelected, true);
                    } else {
                        userList.setSelectedValue(CH_ALL, true);
                    }
                    break;

                case GET_IP_RESPONSE:
                    User targetUser = (User) msg.getPayload();
                    if (!targetUser.getUsername().equals(pendingFileReceiver)) break;
                    String transferPath = pendingFilePath;
                    pendingFileReceiver = null;
                    pendingFilePath = "";
                    saveAndShowBubble(targetUser.getUsername(), "", "⏳ Đang truyền file P2P tới " + targetUser.getUsername() + "...", BubbleAlign.CENTER);
                    sendFileP2P(targetUser.getIpAddress(), targetUser.getPeerPort(), transferPath, targetUser.getUsername());
                    break;
            }
        } finally {
            receivedTimestamp = null;
        }
    }

    private String resolveIncomingTargetTab(Message msg, String sender) {
        String recv = msg.getReceiver();
        if ("All".equalsIgnoreCase(recv)) return CH_ALL;
        if ("Multicast".equalsIgnoreCase(recv)) return CH_MULTICAST;
        if ("Broadcast".equalsIgnoreCase(recv)) return CH_BROADCAST;
        if (recv != null && recv.startsWith(GROUP_PREFIX)) {
            if (!listModel.contains(recv)) {
                listModel.add(3, recv);
            }
            return recv;
        }
        return sender;
    }

    private void sendTextMessage() {
        if (!active || !connection.isConnected()) return;
        String content = messageField.getText().trim();
        String selectedTarget = userList.getSelectedValue();

        if (content.isEmpty() || content.equals("Nhập tin nhắn...") || selectedTarget == null) return;

        if (selectedTarget.equals(CH_ALL)) {
            try {
                Message chatMsg = new Message(MessageType.TEXT_MESSAGE, myUsername, "All", content);
                sendTcp(chatMsg);
                saveAndShowBubble(CH_ALL, "Bạn (Kênh Chung)", content, BubbleAlign.RIGHT);
            } catch (RuntimeException e) {
                saveAndShowBubble(CH_ALL, "", "⚠ Không thể gửi tin nhắn chung.", BubbleAlign.CENTER);
            }
        } else if (selectedTarget.equals(CH_MULTICAST)) {
            Message multiMsg = new Message(MessageType.MULTICAST_MESSAGE, myUsername, "Multicast", content);
            sendUdp(multiMsg, true);
            saveAndShowBubble(CH_MULTICAST, "Bạn [UDP Multicast]", content, BubbleAlign.RIGHT);
        } else if (selectedTarget.equals(CH_BROADCAST)) {
            Message bcastMsg = new Message(MessageType.BROADCAST_MESSAGE, myUsername, "Broadcast", content);
            sendUdp(bcastMsg, false);
            saveAndShowBubble(CH_BROADCAST, "Bạn [UDP Broadcast]", content, BubbleAlign.RIGHT);
        } else if (selectedTarget.startsWith(GROUP_PREFIX)) {
            try {
                String groupName = selectedTarget.substring(GROUP_PREFIX.length());
                Set<String> members = groupMembersMap.getOrDefault(selectedTarget, Collections.singleton(myUsername));
                String encoded = "[GROUP_CHAT|" + groupName + "|" + String.join(",", members) + "]" + content;
                Message groupMsg = new Message(MessageType.TEXT_MESSAGE, myUsername, "All", encoded);
                sendTcp(groupMsg);
                saveAndShowBubble(selectedTarget, "Bạn", content, BubbleAlign.RIGHT);
            } catch (RuntimeException e) {
                saveAndShowBubble(selectedTarget, "", "⚠ Không thể gửi tin nhắn nhóm.", BubbleAlign.CENTER);
            }
        } else {
            try {
                Message chatMsg = new Message(MessageType.TEXT_MESSAGE, myUsername, selectedTarget, content);
                sendTcp(chatMsg);
                saveAndShowBubble(selectedTarget, "Bạn", content, BubbleAlign.RIGHT);
            } catch (RuntimeException e) {
                saveAndShowBubble(selectedTarget, "", "⚠ Không thể gửi tin nhắn.", BubbleAlign.CENTER);
            }
        }

        messageField.setText("");
        messageField.setForeground(TEXT_PRIMARY);
    }

    private void toggleVoiceRecording() {
        String selectedTarget = userList.getSelectedValue();
        if (selectedTarget == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn kênh hoặc người nhận trước!");
            return;
        }

        if (!isRecording) {
            try {
                AudioUtil.startRecording();
                isRecording = true;
                btnVoice.setText("Dừng và gửi");
                btnVoice.setBackground(Color.decode("#F38BA8"));
                btnVoice.setForeground(ThemeManager.TEXT);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Không thể mở micro: " + e.getMessage());
            }
        } else {
            isRecording = false;
            btnVoice.setText("Thu âm");
            btnVoice.setBackground(BG_INPUT);
            btnVoice.setForeground(Color.decode("#F38BA8"));

            byte[] wavBytes = AudioUtil.stopRecording();
            if (wavBytes != null && wavBytes.length > 0) {
                sendAudioMessage(selectedTarget, wavBytes);
            }
        }
    }

    private void sendAudioMessage(String target, byte[] wavBytes) {
        if (!active || !connection.isConnected()) return;
        Message audioMsg = new Message(MessageType.AUDIO_MESSAGE, myUsername, target);
        audioMsg.setFileData(wavBytes);
        audioMsg.setFileName("voice_note_" + System.currentTimeMillis() + ".wav");
        audioMsg.setFileSize(wavBytes.length);

        // Lưu lại vào kho audioClipsStore của chính người gửi để bấm nghe lại như Messenger
        String audioId = UUID.randomUUID().toString();
        audioClipsStore.put(audioId, wavBytes);
        String voiceHtml = createVoiceBubbleHtml(audioId, wavBytes.length, BubbleAlign.RIGHT);

        if (target.equals(CH_MULTICAST)) {
            audioMsg.setReceiver("Multicast");
            sendUdp(audioMsg, true);
            saveAndShowRichBubble(target, "Bạn", voiceHtml, BubbleAlign.RIGHT);
        } else if (target.equals(CH_BROADCAST)) {
            audioMsg.setReceiver("Broadcast");
            sendUdp(audioMsg, false);
            saveAndShowRichBubble(target, "Bạn", voiceHtml, BubbleAlign.RIGHT);
        } else if (target.equals(CH_ALL)) {
            try {
                audioMsg.setReceiver("All");
                sendTcp(audioMsg);
                saveAndShowRichBubble(target, "Bạn", voiceHtml, BubbleAlign.RIGHT);
            } catch (RuntimeException e) {
                saveAndShowBubble(target, "", "⚠ Không thể gửi âm thanh.", BubbleAlign.CENTER);
            }
        } else {
            try {
                sendTcp(audioMsg);
                saveAndShowRichBubble(target, "Bạn", voiceHtml, BubbleAlign.RIGHT);
            } catch (RuntimeException e) {
                saveAndShowBubble(target, "", "⚠ Không thể gửi âm thanh.", BubbleAlign.CENTER);
            }
        }
    }

    private void sendVideoAction() {
        String target = userList.getSelectedValue();
        if (!active || target == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn video");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        Set<String> members = new LinkedHashSet<>(groupMembersMap.getOrDefault(target, Collections.emptySet()));
        network.execute(() -> {
            if (!active) return;
            try {
                if (file.length() > 64L * 1024 * 1024) throw new IOException("Video vượt giới hạn 64 MB.");
                Message m = new Message(MessageType.VIDEO_MESSAGE, myUsername, target);
                m.setFileName(file.getName());
                m.setFileSize(file.length());
                m.setFileData(Files.readAllBytes(file.toPath()));
                if (target.equals(CH_MULTICAST)) {
                    m.setReceiver("Multicast");
                    multicastHandler.sendMulticast(m);
                } else if (target.equals(CH_BROADCAST)) {
                    m.setReceiver("Broadcast");
                    broadcastHandler.sendBroadcast(m);
                } else {
                    if (target.equals(CH_ALL)) m.setReceiver("All");
                    if (target.startsWith(GROUP_PREFIX)) m.setPayload(members);
                    connection.send(m).join();
                }
                SwingUtilities.invokeLater(() -> {
                    if (!active) return;
                    String id = UUID.randomUUID().toString();
                    fileLinksStore.put(id, file);
                    saveAndShowRichBubble(target, "Bạn", createFileBubbleHtml(id, file.getName(), file.length(), "Video đã gửi", BubbleAlign.RIGHT), BubbleAlign.RIGHT);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    if (active)
                        JOptionPane.showMessageDialog(this, "Không thể gửi video. Kiểm tra kích thước và kết nối.");
                });
            }
        });
    }

    private void showEmojiPicker() {
        JPopupMenu popup = new JPopupMenu();
        popup.setBackground(BG_SIDEBAR);
        popup.setBorder(BorderFactory.createLineBorder(ACCENT_COLOR, 1));

        JPanel grid = new JPanel(new GridLayout(4, 8, 4, 4));
        grid.setBackground(BG_SIDEBAR);
        grid.setBorder(new EmptyBorder(8, 8, 8, 8));

        String[] emojis = {
                "😀", "😂", "😍", "🥰", "😎", "👍", "❤️", "🔥",
                "🎉", "👏", "🙌", "🤝", "😢", "😮", "😡", "🥳",
                "🚀", "✨", "⭐", "💯", "💪", "✌️", "🙏", "☕",
                "🍕", "🎵", "💻", "📱", "💡", "✅", "❌", "👋"
        };

        Font emojiItemFont = getCompositeFont(Font.PLAIN, 18);
        for (String emo : emojis) {
            JButton btn = new JButton(emo);
            btn.setFont(emojiItemFont);
            btn.setMargin(new Insets(2, 2, 2, 2));
            btn.setBackground(BG_INPUT);
            btn.setForeground(ThemeManager.TEXT);
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btn.addActionListener(e -> {
                if (messageField.getText().equals("Nhập tin nhắn...")) {
                    messageField.setText("");
                    messageField.setForeground(TEXT_PRIMARY);
                }
                messageField.setText(messageField.getText() + emo);
                messageField.requestFocus();
                popup.setVisible(false);
            });
            grid.add(btn);
        }
        popup.add(grid);
        popup.show(btnEmoji, 0, -popup.getPreferredSize().height - 5);
    }

    private void sendFileAction() {
        if (!active || !connection.isConnected()) return;
        if (pendingFileReceiver != null) {
            JOptionPane.showMessageDialog(this, "Đang chờ thông tin người nhận tệp.");
            return;
        }
        String receiver = userList.getSelectedValue();
        if (receiver == null || receiver.equals(CH_ALL) || receiver.equals(CH_MULTICAST) || receiver.equals(CH_BROADCAST) || receiver.startsWith(GROUP_PREFIX)) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một người dùng cụ thể trong danh bạ để truyền file P2P!");
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            pendingFilePath = selectedFile.getAbsolutePath();
            pendingFileReceiver = receiver;

            try {
                Message ipReqMsg = new Message(MessageType.GET_IP_REQUEST, myUsername, receiver);
                sendTcp(ipReqMsg);
            } catch (RuntimeException e) {
                saveAndShowBubble(receiver, "", "⚠ Không thể yêu cầu truyền file.", BubbleAlign.CENTER);
            }
        }
    }

    private void startPeerServer(int peerPort) {
        Thread p2pServerThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(peerPort)) {
                peerServer = serverSocket;
                if (!active) return;
                System.out.println("[P2P] Đang lắng nghe nhận file tại cổng: " + peerPort);
                while (active) {
                    Socket senderSocket = serverSocket.accept();
                    peerSockets.add(senderSocket);
                    if (!active) {
                        senderSocket.close();
                        break;
                    }
                    receiveFileP2P(senderSocket);
                }
            } catch (IOException e) {
                if (active)
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Không mở được cổng P2P. File vẫn có thể gửi qua relay."));
                System.err.println("[P2P] Lỗi mở cổng nhận file " + peerPort + ": " + e.getMessage());
            }
        });
        p2pServerThread.setDaemon(true);
        p2pServerThread.start();
    }

    private void sendFileP2P(String ip, int port, String filePath, String receiverName) {
        if (!active) return;
        new Thread(() -> {
            File file = new File(filePath);
            if (!file.exists()) {
                SwingUtilities.invokeLater(() ->
                        saveAndShowBubble(receiverName, "", "⚠ Tệp không tồn tại: " + filePath, BubbleAlign.CENTER)
                );
                return;
            }
            long fileSize = file.length();

            // Thử kết nối P2P trực tiếp với timeout 2.5 giây
            Socket transferSocket = new Socket();
            try (Socket socket = transferSocket) {
                peerSockets.add(socket);
                if (!active) return;
                socket.connect(new java.net.InetSocketAddress(ip, port), 2500);
                try (DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                     FileInputStream fis = new FileInputStream(file)) {

                    dos.writeUTF(myUsername);
                    dos.writeUTF(file.getName());
                    dos.writeLong(fileSize);

                    ProgressMonitor pm = new ProgressMonitor(ClientGUI.this, "Đang gửi file P2P...", file.getName(), 0, 100);
                    pm.setMillisToDecideToPopup(0);
                    pm.setMillisToPopup(0);

                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    long totalSent = 0;

                    while ((bytesRead = fis.read(buffer)) > 0) {
                        dos.write(buffer, 0, bytesRead);
                        totalSent += bytesRead;
                        int percent = (int) ((totalSent * 100) / fileSize);
                        SwingUtilities.invokeLater(() -> pm.setProgress(percent));
                        if (pm.isCanceled()) break;
                    }
                    dos.flush();
                    SwingUtilities.invokeLater(pm::close);

                    if (totalSent == fileSize) {
                        SwingUtilities.invokeLater(() -> {
                            if (!active) return;
                            String fileId = UUID.randomUUID().toString();
                            fileLinksStore.put(fileId, file);
                            String fileHtml = createFileBubbleHtml(
                                    fileId,
                                    file.getName(),
                                    fileSize,
                                    "📤 Đã gửi P2P tới " + ip + ":" + port,
                                    BubbleAlign.RIGHT
                            );
                            saveAndShowRichBubble(receiverName, "Bạn", fileHtml, BubbleAlign.RIGHT);
                        });
                    }
                }
            } catch (IOException p2pEx) {
                if (!active) return;
                // Nếu mạng Wi-Fi/Firewall chặn P2P -> Tự động chuyển sang gửi qua Server trung tâm (Relay)
                SwingUtilities.invokeLater(() ->
                        saveAndShowBubble(receiverName, "", "🔄 P2P tới " + ip + ":" + port + " bị chặn (" + p2pEx.getMessage() + "), đang gửi qua Server...", BubbleAlign.CENTER)
                );
                try {
                    if (fileSize > 64L * 1024 * 1024)
                        throw new IOException("Relay hỗ trợ tệp tối đa 64 MB. Hãy kiểm tra kết nối P2P.");
                    byte[] fileBytes = Files.readAllBytes(file.toPath());
                    Message relayFileMsg = new Message(MessageType.VIDEO_MESSAGE, myUsername, receiverName);
                    relayFileMsg.setFileName(file.getName());
                    relayFileMsg.setFileSize(fileSize);
                    relayFileMsg.setFileData(fileBytes);

                    connection.send(relayFileMsg).join();

                    SwingUtilities.invokeLater(() -> {
                        if (!active) return;
                        String fileId = UUID.randomUUID().toString();
                        fileLinksStore.put(fileId, file);
                        String fileHtml = createFileBubbleHtml(
                                fileId,
                                file.getName(),
                                fileSize,
                                "📤 Đã gửi qua Server Relay",
                                BubbleAlign.RIGHT
                        );
                        saveAndShowRichBubble(receiverName, "Bạn", fileHtml, BubbleAlign.RIGHT);
                    });
                } catch (Exception relayEx) {
                    SwingUtilities.invokeLater(() ->
                            saveAndShowBubble(receiverName, "", "⚠ Gửi file thất bại: " + relayEx.getMessage(), BubbleAlign.CENTER)
                    );
                }
            } finally {
                peerSockets.remove(transferSocket);
            }
        }).start();
    }

    private void receiveFileP2P(Socket socket) {
        new Thread(() -> {
            try (DataInputStream dis = new DataInputStream(socket.getInputStream())) {

                socket.setSoTimeout(10000);
                if (!active) return;
                String senderName = dis.readUTF();
                String fileName = new File(dis.readUTF()).getName();
                if (fileName.isBlank()) throw new IOException("Tên tệp không hợp lệ.");
                long fileSize = dis.readLong();

                if (fileSize < 0) throw new IOException("Kích thước tệp không hợp lệ.");
                File saveFile = new File("Received_" + UUID.randomUUID() + "_" + fileName).getAbsoluteFile();

                ProgressMonitor pm = new ProgressMonitor(ClientGUI.this, "Đang nhận file từ " + senderName, fileName, 0, 100);
                pm.setMillisToDecideToPopup(0);
                pm.setMillisToPopup(0);

                try (FileOutputStream fos = new FileOutputStream(saveFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    long totalRead = 0;

                    while (totalRead < fileSize && (bytesRead = dis.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalRead))) > 0) {
                        fos.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                        int percent = (int) ((totalRead * 100) / fileSize);
                        SwingUtilities.invokeLater(() -> pm.setProgress(percent));
                        if (pm.isCanceled()) break;
                    }
                    SwingUtilities.invokeLater(pm::close);

                    if (totalRead == fileSize) {
                        SwingUtilities.invokeLater(() -> {
                            String fileId = UUID.randomUUID().toString();
                            if (!active) return;
                            fileLinksStore.put(fileId, saveFile);
                            String fileHtml = createFileBubbleHtml(
                                    fileId,
                                    saveFile.getName(),
                                    fileSize,
                                    "📥 Đã lưu tại: " + saveFile.getParentFile().getAbsolutePath(),
                                    BubbleAlign.LEFT
                            );
                            saveAndShowRichBubble(senderName, senderName, fileHtml, BubbleAlign.LEFT);
                        });
                    }
                    if (totalRead != fileSize && !pm.isCanceled()) throw new EOFException("Tệp nhận chưa đầy đủ.");
                }

            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    if (active) JOptionPane.showMessageDialog(this, "Nhận tệp P2P bị gián đoạn hoặc không hợp lệ.");
                });
            } finally {
                peerSockets.remove(socket);
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }).start();
    }

    public static void main(String[] args) {
        LoginGUI.main(args);
    }
}
