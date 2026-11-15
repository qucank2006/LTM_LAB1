package client;

import model.Message;
import util.AppConfig;
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
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class ClientGUI extends JFrame {
    private static final int SERVER_PORT = AppConfig.serverPort();

    // Các kênh chat hệ thống đặc biệt
    public static final String CH_ALL = "📢 Kênh Chung (All - TCP)";
    public static final String CH_MULTICAST = "🌐 Kênh Multicast (UDP 230.0.0.1:8888)";
    public static final String CH_BROADCAST = "📡 Kênh Broadcast (UDP Port 9999)";
    public static final String GROUP_PREFIX = "👥 Nhóm: ";

    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String myUsername;
    private int myPeerPort;
    private String pendingFilePath = "";

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
    private JTextPane chatPane;
    private HTMLEditorKit htmlKit;
    private JTextField messageField;
    private JButton btnSendText, btnSendFile, btnConnect, btnVoice, btnVideo, btnEmoji, btnCreateGroup;
    private JTextField txtUsername, txtServerIp;
    private JLabel lblChatTitle;
    private JPanel chatHeaderPanel;

    // Danh sách User online & Nhóm và Bộ nhớ lịch sử chat
    private DefaultListModel<String> listModel;
    private JList<String> userList;
    private final Map<String, StringBuilder> chatHistories = new HashMap<>();

    // --- BỘ MÀU SẮC PREMIUM DARK MODE ---
    private final Color BG_TOP_BAR = new Color(30, 31, 34);       // Đen sâu cho Header
    private final Color BG_SIDEBAR = new Color(43, 45, 49);       // Xám đậm cho Danh bạ
    private final Color BG_CHAT = new Color(49, 51, 56);          // Xám nhạt cho khung Chat
    private final Color BG_INPUT = new Color(56, 58, 64);         // Màu nền ô nhập chữ
    private final Color ACCENT_COLOR = new Color(88, 101, 242);   // Xanh Tím Discord/Messenger
    private final Color HOVER_COLOR = new Color(63, 65, 71);      // Xám sáng khi hover
    private final Color TEXT_PRIMARY = new Color(242, 243, 245);  // Chữ trắng sáng
    private final Color TEXT_MUTED = new Color(148, 155, 164);    // Chữ xám mờ

    // Composite Font chuẩn của Java: hiển thị sắc nét cả Tiếng Việt có dấu và Emoji
    private final Font MAIN_FONT = getCompositeFont(Font.PLAIN, 14);
    private final Font BOLD_FONT = getCompositeFont(Font.BOLD, 14);

    private enum BubbleAlign {
        LEFT,   // Người nhận (bên trái)
        RIGHT,  // Người gửi (bên phải)
        CENTER  // Thông báo hệ thống (ở giữa)
    }

    private static Font getCompositeFont(int style, int size) {
        return new Font(Font.DIALOG, style, size);
    }

    public ClientGUI() {
        setTitle("Lab 1 Chat - TCP/UDP, Multicast, Broadcast, Group, Audio/Video & P2P");
        setSize(1100, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
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
        topPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(20, 20, 20)));

        JPanel topLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 12));
        topLeft.setOpaque(false);

        txtServerIp = createDarkTextField(AppConfig.serverHost(), 11);
        txtUsername = createDarkTextField("Alice", 11);
        btnConnect = createStyledButton("🔌 Kết Nối Server", ACCENT_COLOR, Color.WHITE);

        JLabel lblIp = new JLabel("IP Server:");
        lblIp.setForeground(TEXT_MUTED);
        JLabel lblName = new JLabel("Tên hiển thị:");
        lblName.setForeground(TEXT_MUTED);

        topLeft.add(lblIp);
        topLeft.add(txtServerIp);
        topLeft.add(lblName);
        topLeft.add(txtUsername);
        topLeft.add(btnConnect);

        JPanel topRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 12));
        topRight.setOpaque(false);
        btnCreateGroup = createStyledButton("👥 + Tạo Nhóm Chat", new Color(16, 185, 129), Color.WHITE);
        btnCreateGroup.setEnabled(false);
        btnCreateGroup.setToolTipText("Tạo phòng chat nhóm với các thành viên đang online");
        topRight.add(btnCreateGroup);

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
        userScroll.setPreferredSize(new Dimension(315, 0));
        userScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        userScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(30, 31, 34)));
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
        chatHeaderPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 42, 45)));

        JPanel headerLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 12));
        headerLeft.setOpaque(false);
        lblChatTitle = new JLabel("Chọn một kênh, nhóm hoặc bạn chat bên trái để bắt đầu");
        lblChatTitle.setFont(getCompositeFont(Font.BOLD, 16));
        lblChatTitle.setForeground(TEXT_MUTED);
        headerLeft.add(lblChatTitle);

        chatHeaderPanel.add(headerLeft, BorderLayout.CENTER);
        chatPanel.add(chatHeaderPanel, BorderLayout.NORTH);

        // --- Khung hiển thị tin nhắn HTML (Hỗ trợ click Audio & File) ---
        chatPane = new JTextPane();
        chatPane.setEditable(false);
        chatPane.setContentType("text/html");
        chatPane.setBackground(BG_CHAT);
        chatPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        chatPane.setFont(MAIN_FONT);

        htmlKit = new HTMLEditorKit();
        chatPane.setEditorKit(htmlKit);
        resetChatPaneDocument();

        // Lắng nghe sự kiện bấm vào bong bóng Ghi âm hoặc bong bóng File
        chatPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                handleBubbleLinkClick(e.getDescription());
            }
        });

        JScrollPane chatScroll = new JScrollPane(chatPane);
        chatScroll.setBorder(BorderFactory.createEmptyBorder());
        chatScroll.getViewport().setBackground(BG_CHAT);
        chatScroll.getVerticalScrollBar().setUnitIncrement(16);
        chatPanel.add(chatScroll, BorderLayout.CENTER);

        // --- Khung nhập tin nhắn & nút chức năng ---
        JPanel bottomChatPanel = new JPanel(new BorderLayout(8, 8));
        bottomChatPanel.setBackground(BG_CHAT);
        bottomChatPanel.setBorder(new EmptyBorder(12, 14, 12, 14));

        messageField = createDarkTextField("Nhập tin nhắn...", 0);
        messageField.setEnabled(false);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttonPanel.setBackground(BG_CHAT);

        btnEmoji = createStyledButton("😊", BG_INPUT, new Color(250, 204, 21));
        btnEmoji.setFont(getCompositeFont(Font.PLAIN, 17));
        btnEmoji.setToolTipText("Chọn Icon biểu cảm (32 Emojis)");
        btnEmoji.setEnabled(false);

        btnVoice = createStyledButton("🎙 Thu Âm", BG_INPUT, new Color(248, 113, 113));
        btnVoice.setToolTipText("Bấm để bắt đầu thu âm, bấm lần nữa để dừng và gửi");
        btnVoice.setEnabled(false);

        btnVideo = createStyledButton("🎬 Video", BG_INPUT, new Color(192, 132, 252));
        btnVideo.setToolTipText("Gửi tệp video (.mp4/.avi) tới đối tác");
        btnVideo.setEnabled(false);

        btnSendFile = createStyledButton("📎 Đính Kèm", BG_INPUT, TEXT_PRIMARY);
        btnSendFile.setToolTipText("Truyền file P2P trực tiếp");
        btnSendFile.setEnabled(false);

        btnSendText = createStyledButton("Gửi ➤", ACCENT_COLOR, Color.WHITE);
        btnSendText.setEnabled(false);

        buttonPanel.add(btnEmoji);
        buttonPanel.add(btnVoice);
        buttonPanel.add(btnVideo);
        buttonPanel.add(btnSendFile);
        buttonPanel.add(btnSendText);

        bottomChatPanel.add(messageField, BorderLayout.CENTER);
        bottomChatPanel.add(buttonPanel, BorderLayout.EAST);
        chatPanel.add(bottomChatPanel, BorderLayout.SOUTH);

        mainPanel.add(userScroll, BorderLayout.WEST);
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

        btnConnect.addActionListener(e -> connectToServer());
        btnCreateGroup.addActionListener(e -> showCreateGroupDialog());
        btnSendText.addActionListener(e -> sendTextMessage());
        messageField.addActionListener(e -> sendTextMessage());
        btnSendFile.addActionListener(e -> sendFileAction());
        btnVoice.addActionListener(e -> toggleVoiceRecording());
        btnVideo.addActionListener(e -> sendVideoAction());
        btnEmoji.addActionListener(e -> showEmojiPicker());

        userList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedTarget = userList.getSelectedValue();
                if (selectedTarget != null) {
                    if (selectedTarget.startsWith(GROUP_PREFIX) && groupMembersMap.containsKey(selectedTarget)) {
                        lblChatTitle.setText(selectedTarget + "  (" + groupMembersMap.get(selectedTarget).size() + " thành viên)");
                    } else {
                        lblChatTitle.setText(selectedTarget);
                    }
                    lblChatTitle.setForeground(TEXT_PRIMARY);
                    messageField.setEnabled(true);
                    btnSendText.setEnabled(true);
                    btnSendFile.setEnabled(true);
                    btnVoice.setEnabled(true);
                    btnVideo.setEnabled(true);
                    btnEmoji.setEnabled(true);

                    resetChatPaneDocument();
                    if (chatHistories.containsKey(selectedTarget)) {
                        appendHtmlRaw(chatHistories.get(selectedTarget).toString());
                    }
                }
            }
        });

        multicastHandler = new MulticastHandler(msg -> SwingUtilities.invokeLater(() -> handleReceivedMessage(msg)));
        broadcastHandler = new BroadcastHandler(msg -> SwingUtilities.invokeLater(() -> handleReceivedMessage(msg)));
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
            String groupName = txtGroupName.getText().trim().replace("|", "-").replace("]", "");
            if (groupName.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Tên nhóm không được để trống!");
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
                out.writeObject(msg);
                out.flush();
            } catch (IOException ignored) {}
        }
    }

    private void resetChatPaneDocument() {
        HTMLDocument doc = (HTMLDocument) htmlKit.createDefaultDocument();
        chatPane.setDocument(doc);
        chatPane.setText(
                "<html><body style='background-color:#313338; font-family:\"Segoe UI\", \"Segoe UI Emoji\", sans-serif; margin:12px;'>" +
                        "<div id='chat-root'></div>" +
                        "</body></html>"
        );
    }

    private JTextField createDarkTextField(String text, int columns) {
        JTextField tf = new JTextField(text, columns);
        tf.setFont(MAIN_FONT);
        tf.setBackground(BG_INPUT);
        tf.setForeground(TEXT_PRIMARY);
        tf.setCaretColor(Color.WHITE);
        tf.setBorder(new EmptyBorder(8, 12, 8, 12));
        return tf;
    }

    private JButton createStyledButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setFont(getCompositeFont(Font.BOLD, 13));
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
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

                    if (targetName.startsWith("📢")) {
                        circleColor = new Color(245, 158, 11);
                        symbol = "📢";
                    } else if (targetName.startsWith("🌐")) {
                        circleColor = new Color(14, 165, 233);
                        symbol = "🌐";
                    } else if (targetName.startsWith("📡")) {
                        circleColor = new Color(168, 85, 247);
                        symbol = "📡";
                    } else if (targetName.startsWith(GROUP_PREFIX)) {
                        circleColor = new Color(16, 185, 129);
                        symbol = "👥";
                    } else {
                        symbol = targetName.isEmpty() ? "?" : targetName.substring(0, 1).toUpperCase();
                    }

                    g2.setColor(circleColor);
                    g2.fillOval(0, 0, 36, 36);

                    g2.setColor(Color.WHITE);
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
            lblName.setText(this.targetName);

            if (isSelected) {
                setBackground(HOVER_COLOR);
                lblName.setForeground(Color.WHITE);
            } else {
                setBackground(BG_SIDEBAR);
                boolean isSpecialChannel = this.targetName.startsWith("📢")
                        || this.targetName.startsWith("🌐")
                        || this.targetName.startsWith("📡")
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

    private String buildBubbleHtml(String senderLabel, String rawHtmlContent, BubbleAlign align, String timeStr) {
        String safeSender = escapeHtml(senderLabel);

        if (align == BubbleAlign.CENTER) {
            return "<table width='100%' cellspacing='0' cellpadding='4' style='margin-bottom:6px;'>" +
                    "<tr><td align='center'>" +
                    "<span style='background-color:#2B2D31; color:#949BA4; font-size:12px; padding:4px 10px;'>" +
                    rawHtmlContent + " (" + timeStr + ")" +
                    "</span></td></tr></table>";
        }

        if (align == BubbleAlign.RIGHT) {
            // Người gửi: Căn phải, màu xanh Messenger/Discord, kèm thời gian gửi phía dưới
            return "<table width='100%' cellspacing='0' cellpadding='2' style='margin-bottom:10px;'>" +
                    "<tr>" +
                    "<td width='26%'></td>" +
                    "<td width='74%' align='right'>" +
                    "<div style='font-size:11px; color:#949BA4; margin-bottom:2px;'>" + safeSender + "</div>" +
                    "<table cellspacing='0' cellpadding='9' style='background-color:#5865F2; color:#FFFFFF;'>" +
                    "<tr><td align='left' style='font-size:14px; color:#FFFFFF;'>" + rawHtmlContent + "</td></tr>" +
                    "</table>" +
                    "<div style='font-size:10px; color:#949BA4; margin-top:2px;'>✓ Đã gửi • " + timeStr + "</div>" +
                    "</td>" +
                    "</tr></table>";
        } else {
            // Người nhận: Căn trái, nền xám đậm, kèm thời gian nhận phía dưới
            return "<table width='100%' cellspacing='0' cellpadding='2' style='margin-bottom:10px;'>" +
                    "<tr>" +
                    "<td width='74%' align='left'>" +
                    "<div style='font-size:11px; color:#B5BAC1; font-weight:bold; margin-bottom:2px;'>" + safeSender + "</div>" +
                    "<table cellspacing='0' cellpadding='9' style='background-color:#383A40; color:#F2F3F5;'>" +
                    "<tr><td align='left' style='font-size:14px; color:#F2F3F5;'>" + rawHtmlContent + "</td></tr>" +
                    "</table>" +
                    "<div style='font-size:10px; color:#949BA4; margin-top:2px;'>" + timeStr + "</div>" +
                    "</td>" +
                    "<td width='26%'></td>" +
                    "</tr></table>";
        }
    }

    // Giao diện bong bóng Ghi âm kiểu Messenger (cả 2 bên đều bấm vào nghe được)
    private String createVoiceBubbleHtml(String audioId, int byteLength, BubbleAlign align) {
        int seconds = Math.max(1, byteLength / 32000); // Ước tính thời lượng (16kHz 16-bit mono ~ 32KB/s)
        String durationStr = String.format("0:%02d", seconds);
        String linkColor = (align == BubbleAlign.RIGHT) ? "#FFFFFF" : "#38BDF8";
        String subColor = (align == BubbleAlign.RIGHT) ? "#E0E7FF" : "#949BA4";

        return "<a href='audio:" + audioId + "' style='text-decoration:none; color:" + linkColor + "; font-weight:bold;'>" +
                "▶ &nbsp;▂▄▆█▇▅▃▂▆█▅▃▂&nbsp; " + durationStr +
                "</a>" +
                "<div style='font-size:11px; color:" + subColor + "; margin-top:3px;'>" +
                "🎙 Tin nhắn thoại (" + (byteLength / 1024) + " KB) — Bấm vào để nghe" +
                "</div>";
    }

    // Giao diện bong bóng File đính kèm (bấm vào để mở thư mục lưu hoặc tải về)
    private String createFileBubbleHtml(String fileId, String fileName, long fileSizeBytes, String statusHint, BubbleAlign align) {
        String linkColor = (align == BubbleAlign.RIGHT) ? "#FFFFFF" : "#38BDF8";
        String subColor = (align == BubbleAlign.RIGHT) ? "#E0E7FF" : "#949BA4";
        long sizeKB = Math.max(1, fileSizeBytes / 1024);

        return "<a href='file:" + fileId + "' style='text-decoration:none; color:" + linkColor + "; font-weight:bold;'>" +
                "📄 " + escapeHtml(fileName) + " (" + sizeKB + " KB)" +
                "</a>" +
                "<div style='font-size:11px; color:" + subColor + "; margin-top:3px;'>" +
                escapeHtml(statusHint) + " — Bấm vào để mở thư mục / tải về" +
                "</div>";
    }

    private void appendHtmlRaw(String htmlFragment) {
        try {
            HTMLDocument doc = (HTMLDocument) chatPane.getDocument();
            javax.swing.text.Element root = doc.getElement("chat-root");
            if (root != null) {
                doc.insertBeforeEnd(root, htmlFragment);
            } else {
                htmlKit.insertHTML(doc, doc.getLength(), htmlFragment, 0, 0, null);
            }
            chatPane.setCaretPosition(doc.getLength());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveAndShowBubble(String targetKey, String senderLabel, String plainText, BubbleAlign align) {
        saveAndShowRichBubble(targetKey, senderLabel, escapeHtml(plainText), align);
    }

    private void saveAndShowRichBubble(String targetKey, String senderLabel, String rawHtmlContent, BubbleAlign align) {
        String bubbleHtml = buildBubbleHtml(senderLabel, rawHtmlContent, align, getCurrentTimeStr());
        chatHistories.putIfAbsent(targetKey, new StringBuilder());
        chatHistories.get(targetKey).append(bubbleHtml);

        String currentlySelected = userList.getSelectedValue();
        if (targetKey.equals(currentlySelected)) {
            appendHtmlRaw(bubbleHtml);
        }
    }

    // ==========================================
    // LOGIC KẾT NỐI VÀ NHẬN/GỬI TIN NHẮN
    // ==========================================
    private void connectToServer() {
        myUsername = txtUsername.getText().trim();
        String serverIp = txtServerIp.getText().trim();

        if (myUsername.isEmpty() || myUsername.equals("Tên của bạn...") || serverIp.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập IP Server và Tên hiển thị!");
            return;
        }

        btnConnect.setText("Đang kết nối...");
        btnConnect.setEnabled(false);
        txtUsername.setEnabled(false);
        txtServerIp.setEnabled(false);

        new Thread(() -> {
            try {
                myPeerPort = 9000 + (int) (Math.random() * 1000);
                startPeerServer(myPeerPort);

                multicastHandler.start();
                broadcastHandler.start();

                Socket socket = new Socket(serverIp, SERVER_PORT);
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());

                User myUser = new User(myUsername, socket.getLocalAddress().getHostAddress(), myPeerPort);
                Message loginMsg = new Message(MessageType.LOGIN, myUsername, "Server");
                loginMsg.setPayload(myUser);
                out.writeObject(loginMsg);
                out.flush();

                SwingUtilities.invokeLater(() -> {
                    setTitle("Lab 1 Chat - [Online: " + myUsername + "] (P2P Port: " + myPeerPort + ")");
                    btnConnect.setText("✅ Đã Kết Nối");
                    btnConnect.setBackground(new Color(35, 165, 89));
                    btnCreateGroup.setEnabled(true);
                    userList.setSelectedIndex(0);
                });

                startReadThread();

            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> {
                    btnConnect.setText("🔌 Kết Nối Server");
                    btnConnect.setEnabled(true);
                    txtUsername.setEnabled(true);
                    txtServerIp.setEnabled(true);
                    JOptionPane.showMessageDialog(ClientGUI.this,
                            "Không thể kết nối tới Server TCP: " + serverIp + ":" + SERVER_PORT + "\nLỗi: " + ex.getMessage(),
                            "Lỗi Kết Nối", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void startReadThread() {
        new Thread(() -> {
            try {
                while (true) {
                    Message msg = (Message) in.readObject();
                    SwingUtilities.invokeLater(() -> handleReceivedMessage(msg));
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Đã mất kết nối với Server TCP!"));
            }
        }).start();
    }

    @SuppressWarnings("unchecked")
    private void handleReceivedMessage(Message msg) {
        if (msg == null) return;
        String sender = msg.getSender();
        if (sender != null && sender.equals(myUsername)) return;

        switch (msg.getType()) {
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
                try {
                    String vidName = msg.getFileName() != null ? msg.getFileName() : "video.mp4";
                    File saveVid = new File("Received_Video_" + vidName).getAbsoluteFile();
                    if (msg.getFileData() != null) {
                        Files.write(saveVid.toPath(), msg.getFileData());
                    }
                    String fileId = UUID.randomUUID().toString();
                    fileLinksStore.put(fileId, saveVid);

                    String fileHtml = createFileBubbleHtml(
                            fileId,
                            saveVid.getName(),
                            saveVid.length(),
                            "📥 Đã lưu tại: " + saveVid.getParentFile().getAbsolutePath(),
                            BubbleAlign.LEFT
                    );
                    String targetTab = resolveIncomingTargetTab(msg, sender);
                    saveAndShowRichBubble(targetTab, sender, fileHtml, BubbleAlign.LEFT);
                } catch (Exception ex) {
                    System.err.println("Lỗi lưu video: " + ex.getMessage());
                }
                break;

            case USER_LIST_RESPONSE:
                List<User> onlineUsers = (List<User>) msg.getPayload();
                String currentSelected = userList.getSelectedValue();

                currentOnlineUsers.clear();
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
                saveAndShowBubble(targetUser.getUsername(), "", "⏳ Đang truyền file P2P tới " + targetUser.getUsername() + "...", BubbleAlign.CENTER);
                sendFileP2P(targetUser.getIpAddress(), targetUser.getPeerPort(), pendingFilePath, targetUser.getUsername());
                break;
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
        String content = messageField.getText().trim();
        String selectedTarget = userList.getSelectedValue();

        if (content.isEmpty() || content.equals("Nhập tin nhắn...") || selectedTarget == null) return;

        if (selectedTarget.equals(CH_ALL)) {
            try {
                Message chatMsg = new Message(MessageType.TEXT_MESSAGE, myUsername, "All", content);
                out.writeObject(chatMsg);
                out.flush();
                saveAndShowBubble(CH_ALL, "Bạn (Kênh Chung)", content, BubbleAlign.RIGHT);
            } catch (IOException e) {
                saveAndShowBubble(CH_ALL, "", "⚠ Không thể gửi tin nhắn chung.", BubbleAlign.CENTER);
            }
        } else if (selectedTarget.equals(CH_MULTICAST)) {
            Message multiMsg = new Message(MessageType.MULTICAST_MESSAGE, myUsername, "Multicast", content);
            multicastHandler.sendMulticast(multiMsg);
            saveAndShowBubble(CH_MULTICAST, "Bạn [UDP Multicast]", content, BubbleAlign.RIGHT);
        } else if (selectedTarget.equals(CH_BROADCAST)) {
            Message bcastMsg = new Message(MessageType.BROADCAST_MESSAGE, myUsername, "Broadcast", content);
            broadcastHandler.sendBroadcast(bcastMsg);
            saveAndShowBubble(CH_BROADCAST, "Bạn [UDP Broadcast]", content, BubbleAlign.RIGHT);
        } else if (selectedTarget.startsWith(GROUP_PREFIX)) {
            try {
                String groupName = selectedTarget.substring(GROUP_PREFIX.length());
                Set<String> members = groupMembersMap.getOrDefault(selectedTarget, Collections.singleton(myUsername));
                String encoded = "[GROUP_CHAT|" + groupName + "|" + String.join(",", members) + "]" + content;
                Message groupMsg = new Message(MessageType.TEXT_MESSAGE, myUsername, "All", encoded);
                out.writeObject(groupMsg);
                out.flush();
                saveAndShowBubble(selectedTarget, "Bạn", content, BubbleAlign.RIGHT);
            } catch (IOException e) {
                saveAndShowBubble(selectedTarget, "", "⚠ Không thể gửi tin nhắn nhóm.", BubbleAlign.CENTER);
            }
        } else {
            try {
                Message chatMsg = new Message(MessageType.TEXT_MESSAGE, myUsername, selectedTarget, content);
                out.writeObject(chatMsg);
                out.flush();
                saveAndShowBubble(selectedTarget, "Bạn", content, BubbleAlign.RIGHT);
            } catch (IOException e) {
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
                btnVoice.setText("🔴 Đang Thu (Gửi)");
                btnVoice.setBackground(new Color(239, 68, 68));
                btnVoice.setForeground(Color.WHITE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Không thể mở micro: " + e.getMessage());
            }
        } else {
            isRecording = false;
            btnVoice.setText("🎙 Thu Âm");
            btnVoice.setBackground(BG_INPUT);
            btnVoice.setForeground(new Color(248, 113, 113));

            byte[] wavBytes = AudioUtil.stopRecording();
            if (wavBytes != null && wavBytes.length > 0) {
                sendAudioMessage(selectedTarget, wavBytes);
            }
        }
    }

    private void sendAudioMessage(String target, byte[] wavBytes) {
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
            multicastHandler.sendMulticast(audioMsg);
            saveAndShowRichBubble(target, "Bạn", voiceHtml, BubbleAlign.RIGHT);
        } else if (target.equals(CH_BROADCAST)) {
            audioMsg.setReceiver("Broadcast");
            broadcastHandler.sendBroadcast(audioMsg);
            saveAndShowRichBubble(target, "Bạn", voiceHtml, BubbleAlign.RIGHT);
        } else if (target.equals(CH_ALL)) {
            try {
                audioMsg.setReceiver("All");
                out.writeObject(audioMsg);
                out.flush();
                saveAndShowRichBubble(target, "Bạn", voiceHtml, BubbleAlign.RIGHT);
            } catch (IOException e) {
                saveAndShowBubble(target, "", "⚠ Không thể gửi âm thanh.", BubbleAlign.CENTER);
            }
        } else {
            try {
                out.writeObject(audioMsg);
                out.flush();
                saveAndShowRichBubble(target, "Bạn", voiceHtml, BubbleAlign.RIGHT);
            } catch (IOException e) {
                saveAndShowBubble(target, "", "⚠ Không thể gửi âm thanh.", BubbleAlign.CENTER);
            }
        }
    }

    private void sendVideoAction() {
        String target = userList.getSelectedValue();
        if (target == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn người nhận hoặc kênh chat!");
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Chọn tệp Video để gửi (.mp4, .avi, .mkv)");
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File videoFile = fileChooser.getSelectedFile();
            try {
                byte[] videoBytes = Files.readAllBytes(videoFile.toPath());
                Message videoMsg = new Message(MessageType.VIDEO_MESSAGE, myUsername, target);
                videoMsg.setFileName(videoFile.getName());
                videoMsg.setFileSize(videoFile.length());
                videoMsg.setFileData(videoBytes);

                if (target.equals(CH_MULTICAST)) {
                    videoMsg.setReceiver("Multicast");
                    multicastHandler.sendMulticast(videoMsg);
                } else if (target.equals(CH_BROADCAST)) {
                    videoMsg.setReceiver("Broadcast");
                    broadcastHandler.sendBroadcast(videoMsg);
                } else if (target.equals(CH_ALL)) {
                    videoMsg.setReceiver("All");
                    out.writeObject(videoMsg);
                    out.flush();
                } else {
                    out.writeObject(videoMsg);
                    out.flush();
                }

                String fileId = UUID.randomUUID().toString();
                fileLinksStore.put(fileId, videoFile);
                String fileHtml = createFileBubbleHtml(
                        fileId,
                        videoFile.getName(),
                        videoFile.length(),
                        "🎬 Video gốc tại: " + videoFile.getParentFile().getAbsolutePath(),
                        BubbleAlign.RIGHT
                );
                saveAndShowRichBubble(target, "Bạn", fileHtml, BubbleAlign.RIGHT);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Lỗi đọc tệp video: " + e.getMessage());
            }
        }
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
            btn.setForeground(Color.WHITE);
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
        String receiver = userList.getSelectedValue();
        if (receiver == null || receiver.startsWith("📢") || receiver.startsWith("🌐") || receiver.startsWith("📡") || receiver.startsWith(GROUP_PREFIX)) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một người dùng cụ thể trong danh bạ để truyền file P2P!");
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            pendingFilePath = selectedFile.getAbsolutePath();

            try {
                Message ipReqMsg = new Message(MessageType.GET_IP_REQUEST, myUsername, receiver);
                out.writeObject(ipReqMsg);
                out.flush();
            } catch (IOException e) {
                saveAndShowBubble(receiver, "", "⚠ Không thể yêu cầu truyền file.", BubbleAlign.CENTER);
            }
        }
    }

    private void startPeerServer(int peerPort) {
        Thread p2pServerThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(peerPort)) {
                System.out.println("[P2P] Đang lắng nghe nhận file tại cổng: " + peerPort);
                while (true) {
                    Socket senderSocket = serverSocket.accept();
                    receiveFileP2P(senderSocket);
                }
            } catch (IOException e) {
                System.err.println("[P2P] Lỗi mở cổng nhận file " + peerPort + ": " + e.getMessage());
            }
        });
        p2pServerThread.setDaemon(true);
        p2pServerThread.start();
    }

    private void sendFileP2P(String ip, int port, String filePath, String receiverName) {
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
            try (Socket socket = new Socket()) {
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
                    pm.close();

                    if (totalSent == fileSize) {
                        SwingUtilities.invokeLater(() -> {
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
                // Nếu mạng Wi-Fi/Firewall chặn P2P -> Tự động chuyển sang gửi qua Server trung tâm (Relay)
                SwingUtilities.invokeLater(() ->
                        saveAndShowBubble(receiverName, "", "🔄 P2P tới " + ip + ":" + port + " bị chặn (" + p2pEx.getMessage() + "), đang gửi qua Server...", BubbleAlign.CENTER)
                );
                try {
                    byte[] fileBytes = Files.readAllBytes(file.toPath());
                    Message relayFileMsg = new Message(MessageType.VIDEO_MESSAGE, myUsername, receiverName);
                    relayFileMsg.setFileName(file.getName());
                    relayFileMsg.setFileSize(fileSize);
                    relayFileMsg.setFileData(fileBytes);

                    synchronized (out) {
                        out.writeObject(relayFileMsg);
                        out.flush();
                        out.reset();
                    }

                    SwingUtilities.invokeLater(() -> {
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
            }
        }).start();
    }

    private void receiveFileP2P(Socket socket) {
        new Thread(() -> {
            try (DataInputStream dis = new DataInputStream(socket.getInputStream())) {

                String senderName = dis.readUTF();
                String fileName = dis.readUTF();
                long fileSize = dis.readLong();

                File saveFile = new File("Received_" + fileName).getAbsoluteFile();

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
                    pm.close();

                    if (totalRead == fileSize) {
                        SwingUtilities.invokeLater(() -> {
                            String fileId = UUID.randomUUID().toString();
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
                }

            } catch (IOException ignored) {}
        }).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ClientGUI gui = new ClientGUI();
            if (args != null && args.length > 0 && !args[0].trim().isEmpty()) {
                gui.txtUsername.setText(args[0].trim());
            }
            gui.setVisible(true);
        });
    }
}