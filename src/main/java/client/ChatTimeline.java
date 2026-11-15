package client;

import util.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Swing-native rounded bubbles with HTML only for media hyperlinks.
 */
final class ChatTimeline extends JPanel implements Scrollable {
    private record Bubble(String sender, String html, String time, boolean own, boolean system) {
    }

    private final Map<String, List<Bubble>> history = new HashMap<>();
    private final Consumer<String> link;
    private String channel;
    private int lastWidth;

    ChatTimeline(Consumer<String> link) {
        this.link = link;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ThemeManager.BASE);
        addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                if (getWidth() != lastWidth) {
                    lastWidth = getWidth();
                    rebuild(false);
                }
            }
        });
    }

    void showChannel(String channel) {
        this.channel = channel;
        rebuild(true);
    }

    void append(String channel, String sender, String html, String time, boolean own, boolean system) {
        boolean follow = atBottom();
        history.computeIfAbsent(channel, k -> new ArrayList<>()).add(new Bubble(sender, html, time, own, system));
        if (Objects.equals(channel, this.channel)) {
            add(row(new Bubble(sender, html, time, own, system)));
            revalidate();
            repaint();
            if (follow) scrollBottom();
        }
    }

    void clear() {
        history.clear();
        removeAll();
        revalidate();
        repaint();
    }

    private boolean atBottom() {
        JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        if (scroll == null) return true;
        JScrollBar b = scroll.getVerticalScrollBar();
        return b.getValue() + b.getVisibleAmount() >= b.getMaximum() - 40;
    }

    private void scrollBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
            if (scroll != null) scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum());
        });
    }

    private void rebuild(boolean bottom) {
        removeAll();
        for (Bubble b : history.getOrDefault(channel, Collections.emptyList())) add(row(b));
        revalidate();
        repaint();
        if (bottom) scrollBottom();
    }

    private JPanel row(Bubble b) {
        int width = Math.max(220, Math.min(680, (int) (Math.max(320, getWidth() - 48) * .76)));
        JPanel row = new JPanel(new FlowLayout(b.system ? FlowLayout.CENTER : b.own ? FlowLayout.RIGHT : FlowLayout.LEFT, 12, 5));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        JPanel bubble = new JPanel(new BorderLayout(0, 5)) {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(b.system ? ThemeManager.MANTLE : b.own ? Color.decode("#45475A") : ThemeManager.SURFACE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        bubble.setOpaque(false);
        bubble.setBorder(new EmptyBorder(10, 14, 10, 14));
        JLabel sender = new JLabel(b.sender);
        sender.setForeground(b.own ? ThemeManager.MAUVE : ThemeManager.TEXT);
        sender.setFont(sender.getFont().deriveFont(Font.BOLD, 12));
        bubble.add(sender, BorderLayout.NORTH);
        JTextPane body = new JTextPane();
        body.setEditable(false);
        body.setOpaque(false);
        body.setEditorKit(new javax.swing.text.html.HTMLEditorKit() {
            public javax.swing.text.ViewFactory getViewFactory() {
                return element -> {
                    javax.swing.text.View view = super.getViewFactory().create(element);
                    if (view instanceof javax.swing.text.html.InlineView) {
                        return new javax.swing.text.html.InlineView(element) {
                            public float getMinimumSpan(int axis) {
                                return axis == X_AXIS ? 0 : super.getMinimumSpan(axis);
                            }
                        };
                    }
                    return view;
                };
            }
        });
        body.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        body.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        body.setText("<html><body style='color:#CDD6F4;margin:0;font-family:Segoe UI'>" + b.html + "</body></html>");
        body.setBorder(null);
        body.setSize(width - 28, 32767);
        Dimension preferred = body.getPreferredSize();
        body.setPreferredSize(new Dimension(width - 28, preferred.height));
        ((javax.swing.text.DefaultCaret) body.getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
        body.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) link.accept(e.getDescription());
        });
        bubble.add(body, BorderLayout.CENTER);
        JLabel time = new JLabel(b.time, SwingConstants.RIGHT);
        time.setForeground(ThemeManager.MUTED);
        time.setFont(time.getFont().deriveFont(11f));
        bubble.add(time, BorderLayout.SOUTH);
        bubble.setPreferredSize(new Dimension(width, preferred.height + 64));
        row.add(bubble);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height + 74));
        return row;
    }

    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(600, 400);
    }

    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return 24;
    }

    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return Math.max(24, visible.height - 48);
    }

    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
