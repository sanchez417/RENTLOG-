package com.rentlog;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;

/**
 * RentLog desktop app (Swing). Run this class: it has its own main.
 *
 * Startup asks whether you are a Landlord or a Tenant (the same two portals
 * as the console menu in Main). Both use the same JSON files and the same
 * model classes, so every rule shown here is the rule enforced by the code:
 *   - login uses User.authenticate() (polymorphism), with the same lockout as Main
 *   - billing math lives in Payment.calculateBalance()
 *   - moved-out tenants stay on file, deposits can only be applied/refunded after move-out
 *
 * logo.png is looked up in the working folder, in src/, on the classpath,
 * and next to the compiled classes.
 */
public class RentLogUI extends JFrame {

    // ==========================================
    // DESIGN TOKENS
    // ==========================================

    static final Color INK = new Color(0x0A0A0A);
    static final Color SIDE_ON = new Color(0x26262A);
    static final Color BG = new Color(0xF4F4F5);
    static final Color LINE = new Color(0xE7E7EA);
    static final Color MUTED = new Color(0x6B6B72);
    static final Color OK = new Color(0x15803D);
    static final Color BAD = new Color(0xDC2626);
    static final Color WARN = new Color(0xB45309);
    static final Color DARK_CARD = new Color(0x111113);
    static final Color DARK_LINE = new Color(0x2A2A2E);
    static final Color DARK_MUTED = new Color(0x9A9AA3);
    static final Color SELECT = new Color(0xF1F1F3);
    static final String FONT = pickFont();

    // Same login protection as Main
    private static final int MAX_LOGIN_ATTEMPTS = 3;
    private static final long LOCKOUT_MILLIS = 60_000L;
    private static final int LEASE_WARNING_DAYS = 30;

    private enum Role { LANDLORD, TENANT }

    // ==========================================
    // STATE
    // ==========================================

    private final DataStore db = new DataStore();
    private final CardLayout screens = new CardLayout();
    private final JPanel rootPanel = new JPanel(screens);
    private BufferedImage logo;

    private Role role = Role.LANDLORD;
    private User currentUser;                       // Admin or Tenant (polymorphic)
    private String currentUserId = "";

    private final Map<String, Integer> loginFailures = new HashMap<>();
    private final Map<String, Long> lockUntil = new HashMap<>();

    // login screen
    private Pill loginPill;
    private JLabel loginTitle;
    private JLabel loginSub;
    private JTextField loginUser;
    private JPasswordField loginPass;
    private JLabel loginMsg;

    // landlord shell
    private final JPanel landlordContent = new JPanel(new BorderLayout());
    private final Map<String, FlatButton> landlordNav = new LinkedHashMap<>();
    private BellButton bell;
    private SearchField search;
    private Avatar landlordAvatar;
    private YearMonth dashMonth = YearMonth.now();
    private YearMonth reportMonth = YearMonth.now();
    private int chartMode = 0;

    // tenant shell
    private final JPanel tenantContent = new JPanel(new BorderLayout());
    private final Map<String, FlatButton> tenantNav = new LinkedHashMap<>();
    private JLabel tenantGreeting;
    private Pill tenantRoomPill;
    private Avatar tenantAvatar;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new RentLogUI().setVisible(true));
    }

    public RentLogUI() {
        super("RentLog");

        logo = loadLogo();

        if (logo != null) {
            setIconImage(logo);
        }

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1140, 740));
        setSize(1320, 840);
        setLocationRelativeTo(null);

        db.ensureDefaultAdmin();

        rootPanel.add(buildRoleChooser(), "role");
        rootPanel.add(buildLogin(), "login");
        rootPanel.add(buildLandlordApp(), "landlord");
        rootPanel.add(buildTenantApp(), "tenant");
        setContentPane(rootPanel);

        screens.show(rootPanel, "role");
    }

    // ==========================================
    // SMALL HELPERS
    // ==========================================

    private static String pickFont() {
        // Distinctive, clean UI & editorial/geometric families across Windows, macOS, and Linux
        String[] want = {
                "Plus Jakarta Sans",
                "DM Sans",
                "Outfit",
                "Space Grotesk",
                "Segoe UI Variable",
                "Segoe UI",
                "SF Pro Display",
                "Inter",
                "Bahnschrift",
                "Trebuchet MS",
                "Candara",
                "Roboto",
                "Ubuntu"
        };

        try {
            Set<String> have = new HashSet<>(Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));

            for (String w : want) {
                if (have.contains(w)) {
                    return w;
                }
            }
        } catch (RuntimeException e) {
            // fall through to the logical font
        }
        return Font.SANS_SERIF;
    }

    static Font f(int style, float size) {
        return new Font(FONT, style, 13).deriveFont(style, size);
    }

    static String peso(BigDecimal v) {
        return String.format(Locale.US, "\u20B1%,.2f", v);
    }

    static String pesoShort(BigDecimal v) {
        boolean whole = v.stripTrailingZeros().scale() <= 0;
        return String.format(Locale.US, whole ? "\u20B1%,.0f" : "\u20B1%,.2f", v);
    }

    static String monthName(YearMonth m) {
        return m.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + m.getYear();
    }

    static String monthShort(YearMonth m) {
        return m.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    }

    static void fixH(JComponent c, int h) {
        c.setPreferredSize(new Dimension(10, h));
        c.setMinimumSize(new Dimension(10, h));
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
    }

    static JLabel label(String text, Color color, int style, float size) {
        JLabel l = new JLabel(text);
        l.setForeground(color);
        l.setFont(f(style, size));
        return l;
    }

    static String html(String text) {
        String safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return safe.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "<br>");
    }

    static String clean(String text) {
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }

    static Color statusColor(String s) {
        switch (s) {
            case "Overdue": case "Expired": case "Unpaid": case "Inactive":
            case "Overdue rent": case "Lease expired":
                return BAD;
            case "Active": case "Paid": case "Held": case "Occupied":
                return OK;
            case "Partial": case "Expiring soon": case "Lease ending soon": case "Due soon":
                return WARN;
            default:
                return MUTED;
        }
    }

    static Color tint(Color c) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), 30);
    }

    static Color mix(Color a, Color b, double t) {
        return new Color(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    static void smooth(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    static Cursor hand() {
        return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
    }

    static String initials(String name) {
        String[] parts = name.trim().split("\\s+");
        String a = parts[0].isEmpty() ? "?" : parts[0].substring(0, 1);
        String b = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (a + b).toUpperCase(Locale.ROOT);
    }

    // ==========================================
    // LOGO
    // ==========================================

    private static BufferedImage loadLogo() {
        String[] names = {
                "logo.png",
                "src/logo.png",
                "src/main/resources/logo.png",
                "src/com/rentlog/logo.png",
                "resources/logo.png",
                "assets/logo.png",
                "images/logo.png"
        };

        for (String n : names) {
            File file = new File(n);

            if (file.isFile()) {
                try {
                    BufferedImage img = ImageIO.read(file);
                    if (img != null) {
                        return img;
                    }
                } catch (IOException e) {
                    // try the next place
                }
            }
        }

        String[] resourcePaths = {"/logo.png", "/com/rentlog/logo.png", "logo.png"};
        for (String rp : resourcePaths) {
            try (InputStream in = RentLogUI.class.getResourceAsStream(rp)) {
                if (in != null) {
                    BufferedImage img = ImageIO.read(in);
                    if (img != null) {
                        return img;
                    }
                }
            } catch (IOException e) {
                // try the next place
            }
        }

        try {
            File base = new File(RentLogUI.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File dir = base.isFile() ? base.getParentFile() : base;

            for (int i = 0; i < 5 && dir != null; i++, dir = dir.getParentFile()) {
                for (String n : names) {
                    File file = new File(dir, n);

                    if (file.isFile()) {
                        BufferedImage img = ImageIO.read(file);
                        if (img != null) {
                            return img;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // no logo found: the text fallback is used
        }

        return null;
    }

    private JLabel logoLabel(int width) {
        if (logo == null || logo.getWidth() <= 0 || logo.getHeight() <= 0) {
            JLabel fallback = label("RentLog", Color.WHITE, Font.BOLD, 25);
            return fallback;
        }

        int h = Math.max(24, width * logo.getHeight() / logo.getWidth());
        BufferedImage scaled = new BufferedImage(width, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = scaled.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(logo, 0, 0, width, h, null);
        g2.dispose();
        return new JLabel(new javax.swing.ImageIcon(scaled));
    }

    // ==========================================
    // ICONS (drawn, so no extra image files are needed)
    // ==========================================

    static void drawIcon(Graphics2D g0, String name, double x, double y, double size, Color color) {
        Graphics2D g = (Graphics2D) g0.create();
        smooth(g);
        g.translate(x, y);
        double s = size / 24.0;
        g.scale(s, s);
        g.setColor(color);
        g.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        switch (name) {
            case "home": {
                Path2D roof = new Path2D.Double();
                roof.moveTo(3, 11);
                roof.lineTo(12, 3);
                roof.lineTo(21, 11);
                g.draw(roof);
                Path2D walls = new Path2D.Double();
                walls.moveTo(5, 9.5);
                walls.lineTo(5, 21);
                walls.lineTo(19, 21);
                walls.lineTo(19, 9.5);
                g.draw(walls);
                Path2D door = new Path2D.Double();
                door.moveTo(10, 21);
                door.lineTo(10, 14);
                door.lineTo(14, 14);
                door.lineTo(14, 21);
                g.draw(door);
                break;
            }
            case "building": {
                g.draw(new RoundRectangle2D.Double(5, 3, 10, 18, 2, 2));
                Path2D wing = new Path2D.Double();
                wing.moveTo(15, 9);
                wing.lineTo(19, 9);
                wing.lineTo(19, 21);
                wing.lineTo(15, 21);
                g.draw(wing);
                g.fill(new Rectangle2D.Double(8, 7, 1.8, 1.8));
                g.fill(new Rectangle2D.Double(8, 11, 1.8, 1.8));
                g.fill(new Rectangle2D.Double(8, 15, 1.8, 1.8));
                g.fill(new Rectangle2D.Double(11, 7, 1.8, 1.8));
                g.fill(new Rectangle2D.Double(11, 11, 1.8, 1.8));
                break;
            }
            case "user": {
                g.draw(new Ellipse2D.Double(8, 3.5, 8, 8));
                Path2D body = new Path2D.Double();
                body.moveTo(4.5, 21);
                body.curveTo(4.5, 16, 8, 14, 12, 14);
                body.curveTo(16, 14, 19.5, 16, 19.5, 21);
                g.draw(body);
                break;
            }
            case "users": {
                g.draw(new Ellipse2D.Double(5, 4, 7, 7));
                Path2D body = new Path2D.Double();
                body.moveTo(2.5, 20.5);
                body.curveTo(2.5, 16, 5.5, 14, 8.5, 14);
                body.curveTo(11.5, 14, 14.5, 16, 14.5, 20.5);
                g.draw(body);
                g.draw(new Arc2D.Double(14, 4.5, 6.5, 6.5, 100, -200, Arc2D.OPEN));
                Path2D body2 = new Path2D.Double();
                body2.moveTo(17, 14.3);
                body2.curveTo(19.8, 14.8, 21.5, 16.6, 21.5, 20.5);
                g.draw(body2);
                break;
            }
            case "doc": {
                Path2D page = new Path2D.Double();
                page.moveTo(6, 3);
                page.lineTo(14, 3);
                page.lineTo(19, 8);
                page.lineTo(19, 21);
                page.lineTo(6, 21);
                page.closePath();
                g.draw(page);
                Path2D fold = new Path2D.Double();
                fold.moveTo(14, 3);
                fold.lineTo(14, 8);
                fold.lineTo(19, 8);
                g.draw(fold);
                g.draw(new Line2D.Double(9, 13, 16, 13));
                g.draw(new Line2D.Double(9, 17, 16, 17));
                break;
            }
            case "card": {
                g.draw(new RoundRectangle2D.Double(3, 5, 18, 14, 3, 3));
                g.draw(new Line2D.Double(3, 10, 21, 10));
                g.draw(new Line2D.Double(7, 15, 10.5, 15));
                break;
            }
            case "shield": {
                Path2D sh = new Path2D.Double();
                sh.moveTo(12, 3);
                sh.lineTo(20, 6);
                sh.lineTo(20, 12);
                sh.curveTo(20, 17, 16, 20, 12, 21.5);
                sh.curveTo(8, 20, 4, 17, 4, 12);
                sh.lineTo(4, 6);
                sh.closePath();
                g.draw(sh);
                break;
            }
            case "logout": {
                Path2D br = new Path2D.Double();
                br.moveTo(10, 4);
                br.lineTo(5, 4);
                br.lineTo(5, 20);
                br.lineTo(10, 20);
                g.draw(br);
                g.draw(new Line2D.Double(10, 12, 20, 12));
                Path2D head = new Path2D.Double();
                head.moveTo(16, 8);
                head.lineTo(20, 12);
                head.lineTo(16, 16);
                g.draw(head);
                break;
            }
            case "bell": {
                Path2D b = new Path2D.Double();
                b.moveTo(5.5, 17);
                b.curveTo(6.5, 16, 7, 14, 7, 10);
                b.curveTo(7, 6.5, 9, 4, 12, 4);
                b.curveTo(15, 4, 17, 6.5, 17, 10);
                b.curveTo(17, 14, 17.5, 16, 18.5, 17);
                b.closePath();
                g.draw(b);
                g.draw(new Line2D.Double(10, 20.5, 14, 20.5));
                break;
            }
            case "search": {
                g.draw(new Ellipse2D.Double(4, 4, 11, 11));
                g.draw(new Line2D.Double(13.5, 13.5, 19.5, 19.5));
                break;
            }
            case "clock": {
                g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                Path2D hands = new Path2D.Double();
                hands.moveTo(12, 7);
                hands.lineTo(12, 12);
                hands.lineTo(15.5, 14);
                g.draw(hands);
                break;
            }
            case "lock": {
                g.draw(new RoundRectangle2D.Double(5, 11, 14, 10, 3, 3));
                Path2D sh = new Path2D.Double();
                sh.moveTo(8, 11);
                sh.lineTo(8, 8);
                sh.curveTo(8, 2.5, 16, 2.5, 16, 8);
                sh.lineTo(16, 11);
                g.draw(sh);
                break;
            }
            case "back": {
                Path2D p = new Path2D.Double();
                p.moveTo(15, 5);
                p.lineTo(8, 12);
                p.lineTo(15, 19);
                g.draw(p);
                break;
            }
            case "chart": {
                g.draw(new Line2D.Double(6, 20, 6, 12));
                g.draw(new Line2D.Double(12, 20, 12, 5));
                g.draw(new Line2D.Double(18, 20, 18, 15));
                break;
            }
            case "peso": {
                g.setFont(f(Font.BOLD, 20));
                FontMetrics fm = g.getFontMetrics();
                String t = "\u20B1";
                g.drawString(t, (24 - fm.stringWidth(t)) / 2f, 12 + fm.getAscent() / 2f - 2);
                break;
            }
            case "plus": {
                g.draw(new Line2D.Double(12, 5, 12, 19));
                g.draw(new Line2D.Double(5, 12, 19, 12));
                break;
            }
            default:
                break;
        }

        g.dispose();
    }

    // ==========================================
    // CUSTOM COMPONENTS
    // ==========================================

    static class FlatButton extends JButton {
        Color fill;
        Color outline;
        String icon;
        boolean hover;

        FlatButton(String text, Color fill, Color fg) {
            super(text);
            this.fill = fill;
            setForeground(fg);
            setFont(f(Font.BOLD, 13));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(hand());
            setBorder(new EmptyBorder(10, 18, 10, 18));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        static Color hoverOf(Color c) {
            double lum = 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
            return lum < 128 ? mix(c, Color.WHITE, 0.18) : mix(c, Color.BLACK, 0.06);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);

            Color c = fill;

            if (c != null) {
                g2.setColor(hover ? hoverOf(c) : c);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
            } else if (hover) {
                g2.setColor(new Color(0x1A1A1D));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
            }

            if (outline != null) {
                g2.setColor(outline);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            }

            if (icon != null) {
                drawIcon(g2, icon, 16, (getHeight() - 20) / 2.0, 20, getForeground());
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }

    static class Card extends JPanel {
        Card(LayoutManager lm) {
            super(lm);
            setOpaque(false);
            setBorder(new EmptyBorder(20, 22, 20, 22));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
            g2.setColor(LINE);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
            g2.dispose();
        }
    }

    static class Pill extends JLabel {
        Color bg;

        Pill(String text, Color bg, Color fg) {
            super(text, SwingConstants.CENTER);
            this.bg = bg;
            setForeground(fg);
            setFont(f(Font.BOLD, 11));
            setBorder(new EmptyBorder(5, 13, 5, 13));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static class Badge extends JComponent {
        final String icon;
        final Color bg;
        final Color fg;
        final int size;

        Badge(String icon, Color bg, Color fg, int size) {
            this.icon = icon;
            this.bg = bg;
            this.fg = fg;
            this.size = size;
            setPreferredSize(new Dimension(size, size));
            setMinimumSize(new Dimension(size, size));
            setMaximumSize(new Dimension(size, size));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            g2.setColor(bg);
            g2.fillOval(0, 0, size - 1, size - 1);
            double is = size * 0.46;
            drawIcon(g2, icon, (size - is) / 2.0, (size - is) / 2.0, is, fg);
            g2.dispose();
        }
    }

    static class Avatar extends JComponent {
        String text = "?";
        JPopupMenu menu;

        Avatar() {
            setPreferredSize(new Dimension(44, 44));
            setCursor(hand());
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (menu != null) {
                        menu.show(Avatar.this, getWidth() - 190, getHeight() + 6);
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            g2.setColor(INK);
            g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
            g2.setColor(Color.WHITE);
            g2.setFont(f(Font.BOLD, 14));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, (getWidth() - fm.stringWidth(text)) / 2f,
                    (getHeight() - fm.getHeight()) / 2f + fm.getAscent());
            g2.dispose();
        }
    }

    static class BellButton extends JComponent {
        int count;

        BellButton(Runnable onClick) {
            setPreferredSize(new Dimension(54, 54));
            setCursor(hand());
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    onClick.run();
                }
            });
        }

        void setCount(int count) {
            this.count = count;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(4, 8, 42, 42, 14, 14);
            g2.setColor(INK);
            g2.setStroke(new BasicStroke(1.8f));
            g2.drawRoundRect(4, 8, 42, 42, 14, 14);
            drawIcon(g2, "bell", 14, 18, 22, INK);

            if (count > 0) {
                String t = count > 99 ? "99+" : String.valueOf(count);
                g2.setFont(f(Font.BOLD, 10));
                FontMetrics fm = g2.getFontMetrics();
                int w = Math.max(18, fm.stringWidth(t) + 10);
                int x = 48 - w + 4;
                g2.setColor(BAD);
                g2.fillRoundRect(x, 0, w, 18, 18, 18);
                g2.setColor(Color.WHITE);
                g2.drawString(t, x + (w - fm.stringWidth(t)) / 2f, 13);
            }
            g2.dispose();
        }
    }

    static class SearchField extends JTextField {
        final String hint;
        final boolean outlined;

        SearchField(String hint, boolean outlined) {
            this.hint = hint;
            this.outlined = outlined;
            setOpaque(false);
            setFont(f(Font.PLAIN, 14));
            setForeground(INK);
            setCaretColor(INK);
            setBorder(new EmptyBorder(0, 46, 0, 16));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            g2.setColor(outlined ? Color.WHITE : new Color(0xEFEFF1));
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);

            if (outlined) {
                g2.setColor(LINE);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            }

            drawIcon(g2, "search", 16, (getHeight() - 18) / 2.0, 18, MUTED);
            g2.dispose();

            super.paintComponent(g);

            if (getText().isEmpty()) {
                Graphics2D h = (Graphics2D) g.create();
                smooth(h);
                h.setColor(new Color(0x8A8A92));
                h.setFont(getFont());
                FontMetrics fm = h.getFontMetrics();
                h.drawString(hint, 46, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                h.dispose();
            }
        }
    }

    static class Segmented extends JComponent {
        final String[] items;
        int sel;

        Segmented(String[] items, int sel, IntConsumer onChange) {
            this.items = items;
            this.sel = sel;
            setPreferredSize(new Dimension(items.length * 86 + 8, 36));
            setMinimumSize(new Dimension(items.length * 78 + 8, 36));
            setCursor(hand());

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int w = (getWidth() - 8) / Segmented.this.items.length;
                    int i = Math.max(0, Math.min(Segmented.this.items.length - 1, (e.getX() - 4) / Math.max(1, w)));

                    if (i != Segmented.this.sel) {
                        Segmented.this.sel = i;
                        repaint();
                        onChange.accept(i);
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            g2.setColor(new Color(0xF1F1F3));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());

            int w = (getWidth() - 8) / items.length;
            g2.setFont(f(Font.BOLD, 12));
            FontMetrics fm = g2.getFontMetrics();

            for (int i = 0; i < items.length; i++) {
                int x = 4 + i * w;

                if (i == sel) {
                    g2.setColor(INK);
                    g2.fillRoundRect(x, 4, w, getHeight() - 8, getHeight() - 8, getHeight() - 8);
                }

                g2.setColor(i == sel ? Color.WHITE : MUTED);
                g2.drawString(items[i], x + (w - fm.stringWidth(items[i])) / 2f,
                        (getHeight() - fm.getHeight()) / 2f + fm.getAscent());
            }
            g2.dispose();
        }
    }

    static class MonthPicker extends JComponent {
        YearMonth month;

        MonthPicker(YearMonth month, Consumer<YearMonth> onChange) {
            this.month = month;
            setPreferredSize(new Dimension(236, 42));
            setMaximumSize(new Dimension(236, 42));
            setCursor(hand());

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    YearMonth now = YearMonth.now();
                    YearMonth next = MonthPicker.this.month;

                    if (e.getX() < 42) {
                        next = next.minusMonths(1);
                    } else if (e.getX() > getWidth() - 42) {
                        if (next.isBefore(now)) {
                            next = next.plusMonths(1);
                        }
                    } else {
                        next = now;
                    }

                    if (!next.equals(MonthPicker.this.month)) {
                        MonthPicker.this.month = next;
                        repaint();
                        onChange.accept(next);
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            g2.setColor(LINE);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);

            boolean canNext = month.isBefore(YearMonth.now());
            int cy = getHeight() / 2;

            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(INK);
            Path2D left = new Path2D.Double();
            left.moveTo(24, cy - 5);
            left.lineTo(18, cy);
            left.lineTo(24, cy + 5);
            g2.draw(left);

            g2.setColor(canNext ? INK : new Color(0xC8C8CE));
            Path2D right = new Path2D.Double();
            right.moveTo(getWidth() - 24, cy - 5);
            right.lineTo(getWidth() - 18, cy);
            right.lineTo(getWidth() - 24, cy + 5);
            g2.draw(right);

            String t = monthName(month);
            g2.setFont(f(Font.BOLD, 13));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(INK);
            g2.drawString(t, (getWidth() - fm.stringWidth(t)) / 2f,
                    (getHeight() - fm.getHeight()) / 2f + fm.getAscent());
            g2.dispose();
        }
    }

    static class Donut extends JComponent {
        final double pct;

        Donut(double pct) {
            this.pct = pct;
            setPreferredSize(new Dimension(150, 150));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            int s = Math.min(getWidth(), getHeight()) - 24;
            int x = (getWidth() - s) / 2;
            int y = (getHeight() - s) / 2;
            g2.setStroke(new BasicStroke(16f));
            g2.setColor(LINE);
            g2.draw(new Ellipse2D.Double(x, y, s, s));
            g2.setColor(INK);
            g2.draw(new Arc2D.Double(x, y, s, s, 90, -360 * pct, Arc2D.OPEN));

            String big = Math.round(pct * 100) + "%";
            g2.setFont(f(Font.BOLD, 23));
            g2.setColor(INK);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(big, (getWidth() - fm.stringWidth(big)) / 2f, getHeight() / 2f + 4);
            g2.setFont(f(Font.PLAIN, 11));
            g2.setColor(MUTED);
            fm = g2.getFontMetrics();
            g2.drawString("Occupancy", (getWidth() - fm.stringWidth("Occupancy")) / 2f, getHeight() / 2f + 20);
            g2.dispose();
        }
    }

    static class LineChart extends JComponent {
        double[] v = new double[0];
        String[] names = new String[0];
        double[] px = new double[0];
        int hover = -1;

        LineChart(double[] v, String[] names) {
            setData(v, names);

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int best = -1;
                    double bestDist = 36;

                    for (int i = 0; i < px.length; i++) {
                        double d = Math.abs(e.getX() - px[i]);

                        if (d < bestDist) {
                            bestDist = d;
                            best = i;
                        }
                    }

                    if (best != hover) {
                        hover = best;
                        repaint();
                    }
                }
            });

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    hover = -1;
                    repaint();
                }
            });
        }

        void setData(double[] v, String[] names) {
            this.v = v;
            this.names = names;
            this.px = new double[v.length];
            this.hover = -1;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            int n = v.length;

            if (n < 2) {
                return;
            }

            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);

            int w = getWidth(), h = getHeight();
            int left = 58, right = 56, top = 44, bottom = 26;

            double max = 0;
            for (double d : v) {
                max = Math.max(max, d);
            }
            double ceiling = Math.max(1000, Math.ceil(max * 1.15 / 1000) * 1000);

            g2.setFont(f(Font.PLAIN, 11));
            for (int i = 0; i <= 3; i++) {
                double val = ceiling * i / 3;
                int y = (int) (top + (h - top - bottom) * (1 - val / ceiling));
                g2.setColor(LINE);
                g2.drawLine(left, y, w - right + 10, y);
                g2.setColor(MUTED);
                String t = "\u20B1" + (val >= 1000 ? Math.round(val / 1000) + "k" : String.valueOf(Math.round(val)));
                g2.drawString(t, 4, y + 4);
            }

            double[] py = new double[n];
            for (int i = 0; i < n; i++) {
                px[i] = left + i * (double) (w - left - right) / (n - 1);
                py[i] = top + (h - top - bottom) * (1 - v[i] / ceiling);
            }

            Path2D area = new Path2D.Double();
            area.moveTo(px[0], h - bottom);
            Path2D line = new Path2D.Double();
            for (int i = 0; i < n; i++) {
                area.lineTo(px[i], py[i]);
                if (i == 0) {
                    line.moveTo(px[i], py[i]);
                } else {
                    line.lineTo(px[i], py[i]);
                }
            }
            area.lineTo(px[n - 1], h - bottom);
            area.closePath();
            g2.setColor(new Color(0, 0, 0, 18));
            g2.fill(area);

            g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(INK);
            g2.draw(line);

            int focus = hover >= 0 ? hover : n - 1;

            for (int i = 0; i < n; i++) {
                boolean on = i == focus;
                double r = on ? 6 : 4;
                g2.setColor(on ? INK : Color.WHITE);
                g2.fill(new Ellipse2D.Double(px[i] - r, py[i] - r, 2 * r, 2 * r));
                g2.setColor(INK);
                g2.setStroke(new BasicStroke(2f));
                g2.draw(new Ellipse2D.Double(px[i] - r, py[i] - r, 2 * r, 2 * r));
                g2.setColor(on ? INK : MUTED);
                g2.setFont(f(on ? Font.BOLD : Font.PLAIN, 11));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(names[i], (int) px[i] - fm.stringWidth(names[i]) / 2, h - 6);
            }

            String tip = String.format(Locale.US, "\u20B1%,.0f", v[focus]);
            g2.setFont(f(Font.BOLD, 12));
            FontMetrics fm = g2.getFontMetrics();
            int bw = fm.stringWidth(tip) + 22;
            int bx = Math.max(4, Math.min(w - bw - 4, (int) px[focus] - bw / 2));
            int by = Math.max(4, (int) py[focus] - 40);
            g2.setColor(INK);
            g2.fillRoundRect(bx, by, bw, 26, 26, 26);
            g2.setColor(Color.WHITE);
            g2.drawString(tip, bx + (bw - fm.stringWidth(tip)) / 2f, by + 17);
            g2.dispose();
        }
    }

    static class RoleCard extends JPanel {
        boolean hover;

        RoleCard(String icon, String title, String tag, String desc, Runnable onClick) {
            setOpaque(false);
            setLayout(new GridBagLayout());
            setBorder(new EmptyBorder(26, 26, 24, 26));
            setCursor(hand());

            VStack col = new VStack();
            col.put(new Badge(icon, Color.WHITE, INK, 48), 0, VStack.LEFT);
            col.put(label(title, Color.WHITE, Font.BOLD, 21), 16, VStack.LEFT);
            col.put(label(tag, DARK_MUTED, Font.PLAIN, 12), 4, VStack.LEFT);
            col.put(label("<html><body style='width:210px'>" + desc + "</body></html>",
                    new Color(0xBDBDC4), Font.PLAIN, 13), 12, VStack.LEFT);

            GridBagConstraints g = new GridBagConstraints();
            g.weightx = 1;
            g.weighty = 1;
            g.fill = GridBagConstraints.BOTH;
            add(col, g);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    onClick.run();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            g2.setColor(hover ? new Color(0x1B1B1E) : DARK_CARD);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 26, 26);
            g2.setColor(hover ? Color.WHITE : DARK_LINE);
            g2.setStroke(new BasicStroke(hover ? 1.6f : 1f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 26, 26);
            g2.dispose();
        }
    }

    static class VStack extends JPanel {
        static final int CENTER = 0;
        static final int STRETCH = 1;
        static final int LEFT = 2;

        private int row;

        VStack() {
            super(new GridBagLayout());
            setOpaque(false);
        }

        VStack put(JComponent c, int gapTop, int mode) {
            GridBagConstraints g = new GridBagConstraints();
            g.gridx = 0;
            g.gridy = row++;
            g.weightx = 1;
            g.insets = new Insets(gapTop, 0, 0, 0);
            g.fill = mode == STRETCH ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
            g.anchor = mode == CENTER ? GridBagConstraints.NORTH : GridBagConstraints.NORTHWEST;
            add(c, g);
            return this;
        }

        VStack filler() {
            GridBagConstraints g = new GridBagConstraints();
            g.gridx = 0;
            g.gridy = row++;
            g.weighty = 1;
            g.fill = GridBagConstraints.VERTICAL;
            JPanel p = new JPanel();
            p.setOpaque(false);
            add(p, g);
            return this;
        }
    }

    static class Form extends JPanel {
        private final int width;
        private int y;

        Form(int width) {
            super(new GridBagLayout());
            this.width = width;
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(width, super.getPreferredSize().height);
        }

        Form one(String cap, JComponent c) {
            return two(cap, c, null, null);
        }

        Form two(String cap1, JComponent c1, String cap2, JComponent c2) {
            GridBagConstraints g = new GridBagConstraints();
            g.gridy = y++;
            g.fill = GridBagConstraints.HORIZONTAL;
            g.anchor = GridBagConstraints.NORTHWEST;
            g.weightx = 1;
            g.gridx = 0;
            g.insets = new Insets(0, 0, 14, c2 == null ? 0 : 10);

            if (c2 == null) {
                g.gridwidth = 2;
            }

            add(wrap(cap1, c1), g);

            if (c2 != null) {
                g.gridx = 1;
                g.gridwidth = 1;
                g.insets = new Insets(0, 10, 14, 0);
                add(wrap(cap2, c2), g);
            }
            return this;
        }

        Form note(JComponent c) {
            GridBagConstraints g = new GridBagConstraints();
            g.gridy = y++;
            g.gridx = 0;
            g.gridwidth = 2;
            g.weightx = 1;
            g.fill = GridBagConstraints.HORIZONTAL;
            g.anchor = GridBagConstraints.NORTHWEST;
            g.insets = new Insets(0, 0, 14, 0);
            add(c, g);
            return this;
        }

        private static JPanel wrap(String cap, JComponent c) {
            JPanel p = new JPanel(new BorderLayout(0, 6));
            p.setOpaque(false);
            p.add(label(cap, MUTED, Font.BOLD, 12), BorderLayout.NORTH);
            p.add(c, BorderLayout.CENTER);
            return p;
        }
    }

    // ==========================================
    // FIELDS AND DIALOGS
    // ==========================================

    static JTextField field(String text) {
        JTextField t = new JTextField(text);
        styleField(t);
        return t;
    }

    static JPasswordField passField() {
        JPasswordField p = new JPasswordField();
        styleField(p);
        return p;
    }

    static void styleField(JTextField t) {
        t.setFont(f(Font.PLAIN, 14));
        t.setForeground(INK);
        t.setBackground(Color.WHITE);
        t.setCaretColor(INK);
        t.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xD9D9DE)),
                new EmptyBorder(8, 12, 8, 12)));
        t.setPreferredSize(new Dimension(200, 42));
    }

    static <T> JComboBox<T> combo(List<T> items) {
        JComboBox<T> c = new JComboBox<>();
        for (T item : items) {
            c.addItem(item);
        }
        c.setFont(f(Font.PLAIN, 14));
        c.setBackground(Color.WHITE);
        c.setPreferredSize(new Dimension(200, 42));
        return c;
    }

    static void onChange(JTextField tf, Runnable r) {
        tf.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                r.run();
            }
        });
    }

    static JComponent textBody(String text) {
        JLabel l = new JLabel("<html><body style='width:380px'>" + html(text) + "</body></html>");
        l.setFont(f(Font.PLAIN, 14));
        l.setForeground(INK);
        return l;
    }

    static JComponent monoBody(String text) {
        String t = clean(text);
        JTextArea a = new JTextArea(t);
        a.setEditable(false);
        a.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        a.setForeground(INK);
        a.setBackground(new Color(0xF7F7F8));
        a.setBorder(new EmptyBorder(14, 16, 14, 16));
        a.setRows(Math.max(1, t.split("\n", -1).length));
        a.setColumns(46);
        return a;
    }

    private boolean dialog(String title, String sub, JComponent body, String ok, String cancel) {
        JDialog d = new JDialog(this, title, true);
        boolean[] result = {false};

        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBackground(Color.WHITE);
        root.setBorder(new EmptyBorder(24, 26, 20, 26));

        JPanel head = new JPanel(new BorderLayout(0, 4));
        head.setOpaque(false);
        head.add(label(title, INK, Font.BOLD, 19), BorderLayout.NORTH);

        if (sub != null) {
            head.add(label("<html><body style='width:380px'>" + html(sub) + "</body></html>",
                    MUTED, Font.PLAIN, 13), BorderLayout.CENTER);
        }

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.setBorder(new EmptyBorder(6, 0, 0, 0));

        if (cancel != null) {
            FlatButton c = new FlatButton(cancel, Color.WHITE, INK);
            c.outline = new Color(0xD9D9DE);
            c.addActionListener(e -> d.dispose());
            buttons.add(c);
        }

        FlatButton o = new FlatButton(ok, INK, Color.WHITE);
        o.addActionListener(e -> {
            result[0] = true;
            d.dispose();
        });
        buttons.add(o);

        root.add(head, BorderLayout.NORTH);
        root.add(body, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);

        d.setContentPane(root);
        d.getRootPane().setDefaultButton(o);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.pack();
        d.setLocationRelativeTo(this);
        d.setVisible(true);

        return result[0];
    }

    private void message(String title, String text) {
        dialog(title, null, textBody(text), "OK", null);
    }

    private boolean confirmBox(String title, String text, String okText) {
        return dialog(title, null, textBody(text), okText, "Cancel");
    }

    private void showText(String title, String sub, String text) {
        dialog(title, sub, monoBody(text), "Close", null);
    }

    private boolean formLoop(String title, String sub, JComponent body, String ok, Runnable action) {
        while (dialog(title, sub, body, ok, "Cancel")) {
            try {
                action.run();
                return true;
            } catch (RuntimeException e) {
                message("Please check your input", e.getMessage() == null ? e.toString() : e.getMessage());
            }
        }
        return false;
    }

    // ---- input parsing (messages match the model classes) ----

    static BigDecimal parseMoney(String text, String name, boolean allowZero) {
        String cleaned = text.trim().replace(",", "").replace("\u20B1", "").replace("PHP", "").trim();
        BigDecimal v;

        try {
            v = new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a number.");
        }

        return Payment.normalizeMoney(v, name, allowZero);
    }

    static LocalDate parseDate(String text, String name) {
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(name + " must use YYYY-MM-DD.");
        }
    }

    static int parseDay(String text) {
        try {
            int d = Integer.parseInt(text.trim());

            if (d < 1 || d > 31) {
                throw new IllegalArgumentException("Due day must be between 1 and 31.");
            }
            return d;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Due day must be a number from 1 to 31.");
        }
    }

    // ==========================================
    // STARTUP: LANDLORD OR TENANT
    // ==========================================

    private JComponent buildRoleChooser() {
        JPanel page = new JPanel(new GridBagLayout());
        page.setBackground(Color.BLACK);

        RoleCard landlord = new RoleCard("building", "I\u2019m a Landlord", "Administrator portal",
                "Manage apartments, tenants, leases, payments and deposits.",
                () -> openLogin(Role.LANDLORD));
        RoleCard tenant = new RoleCard("user", "I\u2019m a Tenant", "Tenant portal (view mode)",
                "See your room, your lease, your payment history and your balance.",
                () -> openLogin(Role.TENANT));

        JPanel cards = new JPanel(new GridLayout(1, 2, 22, 0));
        cards.setOpaque(false);
        cards.add(landlord);
        cards.add(tenant);
        cards.setPreferredSize(new Dimension(620, 240));

        FlatButton exit = new FlatButton("Exit", null, DARK_MUTED);
        exit.addActionListener(e -> {
            dispose();
            System.exit(0);
        });

        VStack box = new VStack();
        box.put(logoLabel(260), 0, VStack.CENTER);
        box.put(label("Welcome to RentLog", Color.WHITE, Font.BOLD, 29), 24, VStack.CENTER);
        box.put(label("Smart rental management. Made easy.", DARK_MUTED, Font.PLAIN, 14), 6, VStack.CENTER);
        box.put(label("How would you like to continue?", new Color(0xD4D4D8), Font.BOLD, 13), 32, VStack.CENTER);
        box.put(cards, 16, VStack.STRETCH);
        box.put(exit, 20, VStack.CENTER);

        page.add(box);
        return page;
    }

    // ==========================================
    // LOGIN (shared by both portals, like Main.login)
    // ==========================================

    private JComponent buildLogin() {
        JPanel page = new JPanel(new GridBagLayout());
        page.setBackground(Color.BLACK);

        FlatButton back = new FlatButton("Back", null, DARK_MUTED);
        back.icon = "back";
        back.setBorder(new EmptyBorder(0, 40, 0, 16));
        back.setPreferredSize(new Dimension(100, 38));
        back.addActionListener(e -> screens.show(rootPanel, "role"));

        loginPill = new Pill("", new Color(0x222226), Color.WHITE);
        loginTitle = label("", Color.WHITE, Font.BOLD, 28);
        loginSub = label("", DARK_MUTED, Font.PLAIN, 14);

        loginUser = new JTextField();
        loginPass = new JPasswordField();
        loginMsg = label(" ", new Color(0xF87171), Font.PLAIN, 12);

        for (JTextField tf : new JTextField[]{loginUser, loginPass}) {
            tf.setBackground(new Color(0x141416));
            tf.setForeground(Color.WHITE);
            tf.setCaretColor(Color.WHITE);
            tf.setFont(f(Font.PLAIN, 14));
            tf.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(DARK_LINE),
                    new EmptyBorder(8, 12, 8, 12)));
            tf.setPreferredSize(new Dimension(360, 44));
        }

        FlatButton go = new FlatButton("Sign in", new Color(0xF2F2F4), Color.BLACK);
        go.setPreferredSize(new Dimension(360, 46));

        ActionListener signIn = e -> attemptLogin();
        go.addActionListener(signIn);
        loginUser.addActionListener(signIn);
        loginPass.addActionListener(signIn);

        JPanel pillRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        pillRow.setOpaque(false);
        pillRow.add(loginPill);

        VStack box = new VStack();
        box.put(back, 0, VStack.LEFT);
        box.put(logoLabel(190), 8, VStack.CENTER);
        box.put(pillRow, 24, VStack.STRETCH);
        box.put(loginTitle, 12, VStack.LEFT);
        box.put(loginSub, 6, VStack.LEFT);
        box.put(label("Username", DARK_MUTED, Font.PLAIN, 12), 24, VStack.LEFT);
        box.put(loginUser, 6, VStack.STRETCH);
        box.put(label("Password", DARK_MUTED, Font.PLAIN, 12), 16, VStack.LEFT);
        box.put(loginPass, 6, VStack.STRETCH);
        box.put(go, 22, VStack.STRETCH);
        box.put(loginMsg, 12, VStack.LEFT);

        page.add(box);
        return page;
    }

    private void openLogin(Role r) {
        role = r;
        boolean landlord = r == Role.LANDLORD;

        loginPill.setText(landlord ? "LANDLORD / ADMIN PORTAL" : "TENANT PORTAL");
        loginTitle.setText(landlord ? "Administrator sign in" : "Tenant sign in");
        loginSub.setText(landlord
                ? "Manage your apartments, tenants and payments."
                : "View your room, lease and payment history.");
        loginUser.setText("");
        loginPass.setText("");
        loginMsg.setText(" ");

        screens.show(rootPanel, "login");
        loginUser.requestFocusInWindow();
    }

    private long lockSecondsRemaining(String key) {
        Long until = lockUntil.get(key);

        if (until == null) {
            return 0;
        }

        long remaining = until - System.currentTimeMillis();

        if (remaining <= 0) {
            lockUntil.remove(key);
            return 0;
        }

        return (remaining + 999) / 1000;
    }

    private void attemptLogin() {
        String username = loginUser.getText().trim();
        String password = new String(loginPass.getPassword());

        if (username.isEmpty()) {
            loginMsg.setText("Enter your username.");
            return;
        }

        String key = (role == Role.LANDLORD ? "admin" : "tenant") + ":" + username.toLowerCase(Locale.ROOT);
        long locked = lockSecondsRemaining(key);

        if (locked > 0) {
            loginMsg.setText("Too many failed attempts. Try again in " + locked + " seconds.");
            return;
        }

        db.reload();
        List<? extends User> users = role == Role.LANDLORD ? db.admins : db.tenants;

        for (User user : users) {
            if (user.authenticate(username, password)) {
                loginFailures.remove(key);
                lockUntil.remove(key);
                loginPass.setText("");
                loginMsg.setText(" ");

                currentUser = user;
                currentUserId = user.getUserID();

                if (user instanceof Tenant) {
                    openTenantPortal();
                } else {
                    openLandlordPortal();
                }
                return;
            }
        }

        int failures = loginFailures.merge(key, 1, Integer::sum);

        if (failures >= MAX_LOGIN_ATTEMPTS) {
            lockUntil.put(key, System.currentTimeMillis() + LOCKOUT_MILLIS);
            loginFailures.remove(key);
            loginMsg.setText("Too many failed attempts. This account is locked for "
                    + (LOCKOUT_MILLIS / 1000) + " seconds.");
        } else {
            loginMsg.setText("Invalid credentials. Attempts remaining: "
                    + (MAX_LOGIN_ATTEMPTS - failures));
        }

        loginPass.setText("");
    }

    private void logout() {
        currentUser = null;
        currentUserId = "";
        landlordContent.removeAll();
        tenantContent.removeAll();
        screens.show(rootPanel, "role");
    }

    private void applyPasswordChange(String current, String next, String confirm) {
        db.reload();
        User user = role == Role.LANDLORD ? db.admin(currentUserId) : db.tenant(currentUserId);

        if (user == null) {
            throw new IllegalStateException("Your account could not be found.");
        }

        if (!user.verifyPassword(current)) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }

        if (!next.equals(confirm)) {
            throw new IllegalArgumentException("The new passwords do not match.");
        }

        user.setPassword(next);

        if (role == Role.LANDLORD) {
            db.saveAdmins();
        } else {
            db.saveTenants();
        }
    }

    private void changePasswordDialog() {
        JPasswordField cur = passField();
        JPasswordField nw = passField();
        JPasswordField cf = passField();

        Form form = new Form(400);
        form.one("Current password", cur);
        form.one("New password (min 6 characters)", nw);
        form.one("Confirm new password", cf);

        boolean ok = formLoop("Change password", null, form, "Update password",
                () -> applyPasswordChange(new String(cur.getPassword()),
                        new String(nw.getPassword()), new String(cf.getPassword())));

        if (ok) {
            message("Password changed", "Password changed successfully!");
        }
    }

    private void accountDialog() {
        if (currentUser == null) {
            return;
        }

        showText("Account", "Signed in as " + currentUser.getFullName(), currentUser.getRoleDetails());
    }

    private JPopupMenu accountMenu(Runnable goProfile) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem a = new JMenuItem(goProfile == null ? "Account details" : "My profile");
        a.addActionListener(e -> {
            if (goProfile == null) {
                accountDialog();
            } else {
                goProfile.run();
            }
        });
        menu.add(a);

        JMenuItem p = new JMenuItem("Change password");
        p.addActionListener(e -> changePasswordDialog());
        menu.add(p);

        menu.addSeparator();

        JMenuItem o = new JMenuItem("Log out");
        o.addActionListener(e -> logout());
        menu.add(o);

        return menu;
    }

    // ==========================================
    // SHARED SHELL PIECES
    // ==========================================

    private JPanel buildSidebar(String[][] items, Map<String, FlatButton> navMap,
                                Consumer<String> go, String workspace) {
        JPanel side = new JPanel(new BorderLayout());
        side.setBackground(Color.BLACK);
        side.setPreferredSize(new Dimension(264, 0));

        VStack col = new VStack();
        col.setBorder(new EmptyBorder(24, 16, 20, 16));
        col.put(logoLabel(156), 0, VStack.CENTER);

        boolean first = true;
        for (String[] item : items) {
            FlatButton b = navButton(item[0], item[1]);
            String name = item[0];
            b.addActionListener(e -> go.accept(name));
            navMap.put(name, b);
            col.put(b, first ? 32 : 8, VStack.STRETCH);
            first = false;
        }

        col.filler();

        JPanel rule = new JPanel();
        rule.setBackground(new Color(0x222226));
        rule.setPreferredSize(new Dimension(10, 1));
        col.put(rule, 0, VStack.STRETCH);

        JLabel tag = new JLabel("<html>Smart rental management.<br>Made easy.</html>");
        tag.setForeground(Color.WHITE);
        tag.setFont(f(Font.BOLD, 18));
        col.put(tag, 20, VStack.LEFT);
        col.put(label(workspace, DARK_MUTED, Font.PLAIN, 12), 6, VStack.LEFT);

        FlatButton out = navButton("Log out", "logout");
        out.addActionListener(e -> logout());
        col.put(out, 18, VStack.STRETCH);

        side.add(col, BorderLayout.CENTER);
        return side;
    }

    private FlatButton navButton(String text, String icon) {
        FlatButton b = new FlatButton(text, null, new Color(0xE9E9EC));
        b.icon = icon;
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setBorder(new EmptyBorder(0, 48, 0, 14));
        b.setPreferredSize(new Dimension(228, 48));
        return b;
    }

    private void markNav(Map<String, FlatButton> navMap, String active) {
        for (Map.Entry<String, FlatButton> e : navMap.entrySet()) {
            boolean on = e.getKey().equals(active);
            e.getValue().fill = on ? SIDE_ON : null;
            e.getValue().setForeground(on ? Color.WHITE : new Color(0xE9E9EC));
            e.getValue().repaint();
        }
    }

    private JPanel topBar() {
        JPanel bar = new JPanel(new BorderLayout(20, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(LINE);
                g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            }
        };
        bar.setBorder(new EmptyBorder(16, 32, 16, 32));
        bar.setPreferredSize(new Dimension(10, 82));
        return bar;
    }

    private JPanel header(String title, String sub, JComponent action) {
        JPanel h = new JPanel(new BorderLayout(16, 0));
        h.setOpaque(false);
        h.setBorder(new EmptyBorder(0, 0, 20, 0));

        JPanel t = new JPanel(new BorderLayout(0, 5));
        t.setOpaque(false);
        t.add(label(title, INK, Font.BOLD, 30), BorderLayout.NORTH);
        t.add(label(sub, MUTED, Font.PLAIN, 14), BorderLayout.CENTER);
        h.add(t, BorderLayout.CENTER);

        if (action != null) {
            JPanel wrap = new JPanel(new GridBagLayout());
            wrap.setOpaque(false);
            wrap.add(action);
            h.add(wrap, BorderLayout.EAST);
        }
        return h;
    }

    private JPanel row(JComponent l, JComponent r, double wl, double wr, int h) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        l.setPreferredSize(new Dimension(1, h));
        r.setPreferredSize(new Dimension(1, h));

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.gridx = 0;
        c.weightx = wl;
        c.insets = new Insets(0, 0, 0, 10);
        p.add(l, c);
        c.gridx = 1;
        c.weightx = wr;
        c.insets = new Insets(0, 10, 0, 0);
        p.add(r, c);

        fixH(p, h);
        return p;
    }

    // Updated so labels and peso numbers never get clipped on narrow stat cards
    private Card stat(String name, String value, String note, String icon, Color noteColor) {
        Card c = new Card(new BorderLayout(12, 0));
        c.setBorder(new EmptyBorder(16, 16, 16, 16));
        c.add(new Badge(icon, INK, Color.WHITE, 46), BorderLayout.WEST);

        JPanel t = new JPanel();
        t.setOpaque(false);
        t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));

        JLabel lName = label(name, MUTED, Font.PLAIN, 12);
        lName.setToolTipText(name);

        JLabel lVal = label(value, INK, Font.BOLD, 21);
        lVal.setToolTipText(value);

        JLabel lNote = label(note, noteColor, Font.PLAIN, 11);
        lNote.setToolTipText(note);

        t.add(lName);
        t.add(Box.createVerticalStrut(3));
        t.add(lVal);
        t.add(Box.createVerticalStrut(3));
        t.add(lNote);

        JPanel mid = new JPanel(new BorderLayout());
        mid.setOpaque(false);
        mid.add(t, BorderLayout.CENTER);
        c.add(mid, BorderLayout.CENTER);
        return c;
    }

    private JPanel listRow(String main, String sub, JComponent right) {
        JPanel r = new JPanel(new BorderLayout(12, 0));
        r.setOpaque(false);
        r.setBorder(new EmptyBorder(8, 0, 8, 0));

        JPanel t = new JPanel();
        t.setOpaque(false);
        t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
        t.add(label(main, INK, Font.BOLD, 13));
        t.add(Box.createVerticalStrut(2));
        t.add(label(sub, MUTED, Font.PLAIN, 12));
        r.add(t, BorderLayout.CENTER);
        r.add(right, BorderLayout.EAST);
        fixH(r, 56);
        return r;
    }

    private JPanel legend(String name, int n) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(label(name, INK, Font.PLAIN, 14), BorderLayout.WEST);
        p.add(label(String.valueOf(n), INK, Font.BOLD, 14), BorderLayout.EAST);
        return p;
    }

    private JPanel infoRow(String key, String value) {
        JPanel r = new JPanel(new BorderLayout(12, 0));
        r.setOpaque(false);
        r.setBorder(new MatteBorder(0, 0, 1, 0, LINE));
        r.add(label(key, MUTED, Font.PLAIN, 13), BorderLayout.WEST);
        r.add(label(value, INK, Font.BOLD, 13), BorderLayout.EAST);
        fixH(r, 44);
        return r;
    }

    private JPanel pageColumn() {
        JPanel page = new JPanel();
        page.setBackground(BG);
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        page.setBorder(new EmptyBorder(28, 32, 32, 32));
        return page;
    }

    private JScrollPane scroll(JComponent page) {
        JScrollPane sp = new JScrollPane(page);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.getViewport().setBackground(BG);
        return sp;
    }

    // ==========================================
    // TABLES (one builder for every list screen)
    // ==========================================

    static class Act {
        final String label;
        final boolean primary;
        final boolean needsRow;
        final Consumer<Object> run;

        Act(String label, boolean primary, boolean needsRow, Consumer<Object> run) {
            this.label = label;
            this.primary = primary;
            this.needsRow = needsRow;
            this.run = run;
        }
    }

    static class TableSpec {
        final String title;
        final String sub;
        final String[] cols;
        final List<Object[]> rows = new ArrayList<>();
        final List<Object> keys = new ArrayList<>();
        final List<Act> acts = new ArrayList<>();
        Consumer<Object> open;
        JComponent above;
        int statusCol;
        String empty = "Nothing here yet.";

        TableSpec(String title, String sub, String... cols) {
            this.title = title;
            this.sub = sub;
            this.cols = cols;
            this.statusCol = cols.length - 1;
        }

        TableSpec row(Object key, Object... cells) {
            keys.add(key);
            rows.add(cells);
            return this;
        }

        TableSpec act(String label, boolean primary, boolean needsRow, Consumer<Object> run) {
            acts.add(new Act(label, primary, needsRow, run));
            return this;
        }
    }

    private JTable buildTable(String[] cols, List<Object[]> rows, int statusCol) {
        DefaultTableModel model = new DefaultTableModel(rows.toArray(new Object[0][]), cols) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        JTable table = new JTable(model);
        table.setRowHeight(48);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setGridColor(LINE);
        table.setFont(f(Font.PLAIN, 13));
        table.setSelectionBackground(SELECT);
        table.setSelectionForeground(INK);
        table.setFillsViewportHeight(true);

        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        for (int i = 0; i < cols.length; i++) {
            sorter.setSortable(i, false);
        }
        table.setRowSorter(sorter);

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tb, Object val, boolean sel,
                                                           boolean foc, int r, int c) {
                String text = String.valueOf(val);

                if (c == statusCol) {
                    JPanel p = new JPanel(new GridBagLayout());
                    p.setOpaque(true);
                    p.setBackground(sel ? SELECT : Color.WHITE);
                    Color sc = statusColor(text);
                    GridBagConstraints g = new GridBagConstraints();
                    g.insets = new Insets(0, 14, 0, 0);
                    g.anchor = GridBagConstraints.WEST;
                    g.weightx = 1;
                    p.add(new Pill(text, tint(sc), sc), g);
                    return p;
                }

                JLabel l = (JLabel) super.getTableCellRendererComponent(tb, val, sel, false, r, c);
                l.setBorder(new EmptyBorder(0, 14, 0, 14));
                l.setFont(f(c == 0 ? Font.BOLD : Font.PLAIN, 13));
                l.setForeground(INK);
                l.setBackground(sel ? SELECT : Color.WHITE);
                return l;
            }
        });

        JTableHeader hd = table.getTableHeader();
        hd.setReorderingAllowed(false);
        hd.setPreferredSize(new Dimension(0, 42));
        hd.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tb, Object val, boolean sel,
                                                           boolean foc, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(tb, val, false, false, r, c);
                l.setBorder(BorderFactory.createCompoundBorder(
                        new MatteBorder(0, 0, 1, 0, LINE), new EmptyBorder(0, 14, 0, 14)));
                l.setBackground(Color.WHITE);
                l.setForeground(MUTED);
                l.setFont(f(Font.BOLD, 12));
                l.setHorizontalAlignment(SwingConstants.LEFT);
                return l;
            }
        });

        return table;
    }

    private Card tableCard(JTable table, String emptyText) {
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(null);
        sp.getViewport().setBackground(Color.WHITE);

        Card card = new Card(new BorderLayout());
        card.setBorder(new EmptyBorder(10, 10, 10, 10));

        if (table.getModel().getRowCount() == 0) {
            JPanel empty = new JPanel(new GridBagLayout());
            empty.setOpaque(false);
            empty.add(label(emptyText, MUTED, Font.PLAIN, 14));
            card.add(table.getTableHeader(), BorderLayout.NORTH);
            card.add(empty, BorderLayout.CENTER);
        } else {
            card.add(sp, BorderLayout.CENTER);
        }
        return card;
    }

    private static Object selectedKey(JTable table, List<Object> keys) {
        int r = table.getSelectedRow();

        if (r < 0) {
            return null;
        }

        int m = table.convertRowIndexToModel(r);
        return m < keys.size() ? keys.get(m) : null;
    }

    private JComponent tableView(TableSpec s) {
        JTable table = buildTable(s.cols, s.rows, s.statusCol);
        Card card = tableCard(table, s.empty);

        JPanel headerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        headerActions.setOpaque(false);
        JPanel rowActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rowActions.setOpaque(false);

        for (Act a : s.acts) {
            FlatButton b = new FlatButton(a.label, a.primary ? INK : Color.WHITE, a.primary ? Color.WHITE : INK);

            if (!a.primary) {
                b.outline = new Color(0xD9D9DE);
            }

            b.addActionListener(e -> {
                Object key = null;

                if (a.needsRow) {
                    key = selectedKey(table, s.keys);

                    if (key == null) {
                        message("Select a row", "Click a row in the table first, then press \u201C" + a.label + "\u201D.");
                        return;
                    }
                }

                try {
                    a.run.accept(key);
                } catch (RuntimeException ex) {
                    message("Something went wrong", ex.getMessage() == null ? ex.toString() : ex.getMessage());
                }
            });

            (a.needsRow ? rowActions : headerActions).add(b);
        }

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && s.open != null) {
                    Object key = selectedKey(table, s.keys);

                    if (key != null) {
                        s.open.accept(key);
                    }
                }
            }
        });

        SearchField filter = new SearchField("Filter this list", true);
        filter.setPreferredSize(new Dimension(320, 42));

        @SuppressWarnings("unchecked")
        TableRowSorter<DefaultTableModel> sorter = (TableRowSorter<DefaultTableModel>) table.getRowSorter();
        onChange(filter, () -> {
            String text = filter.getText().trim();

            if (text.isEmpty()) {
                sorter.setRowFilter(null);
            } else {
                sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
            }
        });

        JPanel toolbar = new JPanel(new BorderLayout(14, 0));
        toolbar.setOpaque(false);
        toolbar.add(filter, BorderLayout.WEST);

        if (rowActions.getComponentCount() > 0) {
            toolbar.add(rowActions, BorderLayout.EAST);
        }

        JPanel north = new JPanel(new BorderLayout(0, 16));
        north.setOpaque(false);

        if (s.above != null) {
            north.add(s.above, BorderLayout.NORTH);
        }
        north.add(toolbar, BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout(0, 14));
        center.setOpaque(false);
        center.add(north, BorderLayout.NORTH);
        center.add(card, BorderLayout.CENTER);

        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(BG);
        page.setBorder(new EmptyBorder(28, 32, 32, 32));
        page.add(header(s.title, s.sub, headerActions.getComponentCount() > 0 ? headerActions : null),
                BorderLayout.NORTH);
        page.add(center, BorderLayout.CENTER);
        return page;
    }

    // ==========================================
    // LANDLORD PORTAL: SHELL
    // ==========================================

    private JComponent buildLandlordApp() {
        String[][] items = {
                {"Dashboard", "home"}, {"Apartments", "building"}, {"Tenants", "user"},
                {"Rentals", "doc"}, {"Payments", "card"}, {"Deposits", "shield"}, {"Reports", "chart"}
        };

        JPanel side = buildSidebar(items, landlordNav, this::goTo, "Landlord workspace");

        search = new SearchField("Search apartments, tenants, or payments", false);
        search.addActionListener(e -> runSearch(search.getText()));

        bell = new BellButton(this::showAlerts);

        landlordAvatar = new Avatar();
        landlordAvatar.menu = accountMenu(null);

        JPanel cluster = new JPanel(new GridBagLayout());
        cluster.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(0, 0, 0, 14);
        cluster.add(bell, g);
        g.insets = new Insets(0, 0, 0, 0);
        cluster.add(landlordAvatar, g);

        JPanel bar = topBar();
        bar.add(search, BorderLayout.CENTER);
        bar.add(cluster, BorderLayout.EAST);

        landlordContent.setBackground(BG);

        JPanel right = new JPanel(new BorderLayout());
        right.add(bar, BorderLayout.NORTH);
        right.add(landlordContent, BorderLayout.CENTER);

        JPanel app = new JPanel(new BorderLayout());
        app.add(side, BorderLayout.WEST);
        app.add(right, BorderLayout.CENTER);
        return app;
    }

    private void openLandlordPortal() {
        dashMonth = YearMonth.now();
        reportMonth = YearMonth.now();
        chartMode = 0;
        search.setText("");
        landlordAvatar.text = initials(currentUser.getFullName());
        screens.show(rootPanel, "landlord");
        goTo("Dashboard");
    }

    private void goTo(String name) {
        db.reload();

        JComponent view;

        switch (name) {
            case "Apartments": view = apartmentsView(); break;
            case "Tenants": view = tenantsView(); break;
            case "Rentals": view = rentalsView(); break;
            case "Payments": view = paymentsView(); break;
            case "Deposits": view = depositsView(); break;
            case "Reports": view = reportsView(); break;
            default: view = dashboardView();
        }

        showLandlord(name, view);
    }

    private void showLandlord(String navName, JComponent view) {
        markNav(landlordNav, navName);
        landlordContent.removeAll();
        landlordContent.add(view, BorderLayout.CENTER);
        landlordContent.revalidate();
        landlordContent.repaint();
        bell.setCount(computeAlerts().size());
    }

    // ==========================================
    // ALERTS (same rules as Main.showAlerts)
    // ==========================================

    private List<String[]> computeAlerts() {
        List<String[]> out = new ArrayList<>();
        LocalDate today = LocalDate.now();
        YearMonth thisMonth = YearMonth.from(today);

        for (Tenant t : db.tenants) {
            if (!t.isActive()) {
                continue;
            }

            Rental r = db.rental(t.getTenantID());

            if (r == null) {
                continue;
            }

            String who = t.getFullName() + " (" + t.getTenantID() + ", Room " + t.getRoomNumber() + ")";

            if ("Expired".equals(r.checkLeaseStatus())) {
                out.add(new String[]{"Lease expired", who + " \u2013 ended " + r.getLeaseExpirationDate()});
            } else if (r.isExpiringWithin(LEASE_WARNING_DAYS)) {
                out.add(new String[]{"Lease ending soon", who + " \u2013 ends " + r.getLeaseExpirationDate()});
            }

            LocalDate moveIn = LocalDate.parse(t.getMoveInDate());
            LocalDate due = r.dueDateFor(thisMonth);

            if (YearMonth.from(moveIn).isAfter(thisMonth) || due.isBefore(moveIn)) {
                continue;
            }

            BigDecimal balance = r.getMonthlyRent().subtract(db.paidFor(t.getTenantID(), thisMonth));

            if (balance.signum() > 0 && today.isAfter(due)) {
                long late = ChronoUnit.DAYS.between(due, today);
                out.add(new String[]{"Overdue rent", who + " \u2013 " + peso(balance) + " unpaid for "
                        + thisMonth + " (due " + due + ", " + late + " day(s) late)"});
            }
        }
        return out;
    }

    private void showAlerts() {
        db.reload();
        List<String[]> alerts = computeAlerts();
        bell.setCount(alerts.size());

        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));

        if (alerts.isEmpty()) {
            list.add(label("No alerts. Everything looks good!", MUTED, Font.PLAIN, 14));
        }

        for (String[] a : alerts) {
            Color c = statusColor(a[0]);
            JPanel row = new JPanel(new BorderLayout(12, 0));
            row.setOpaque(false);
            row.setBorder(new EmptyBorder(8, 0, 8, 0));

            JPanel pillWrap = new JPanel(new GridBagLayout());
            pillWrap.setOpaque(false);
            pillWrap.setPreferredSize(new Dimension(136, 10));
            GridBagConstraints g = new GridBagConstraints();
            g.anchor = GridBagConstraints.NORTHWEST;
            g.weighty = 1;
            g.weightx = 1;
            pillWrap.add(new Pill(a[0], tint(c), c), g);

            row.add(pillWrap, BorderLayout.WEST);
            row.add(label("<html><body style='width:300px'>" + html(a[1]) + "</body></html>",
                    INK, Font.PLAIN, 13), BorderLayout.CENTER);
            list.add(row);
        }

        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.setPreferredSize(new Dimension(520, Math.min(380, Math.max(70, alerts.size() * 64 + 12))));

        dialog("Alerts", "Overdue rent and lease expiry", sp, "Close", null);
    }

    // ==========================================
    // SEARCH (top bar)
    // ==========================================

    private void runSearch(String query) {
        String q = query.trim();

        if (q.isEmpty()) {
            goTo("Dashboard");
            return;
        }

        db.reload();
        String needle = q.toLowerCase(Locale.ROOT);

        TableSpec spec = new TableSpec("Search results", "", "Type", "Match", "Details", "Status");

        for (Apartment a : db.apartments) {
            Tenant who = db.occupant(a.getRoomNumber());
            String hay = (a.getRoomNumber() + " " + a.getRoomStatus() + " " + peso(a.getMonthlyRentalPrice())
                    + " " + (who == null ? "" : who.getFullName())).toLowerCase(Locale.ROOT);

            if (hay.contains(needle)) {
                spec.row("Apartments", "Apartment", "Room " + a.getRoomNumber(),
                        peso(a.getMonthlyRentalPrice()) + " / month" + (who == null ? "" : " \u00B7 " + who.getFullName()),
                        a.getRoomStatus());
            }
        }

        for (Tenant t : db.tenants) {
            String hay = (t.getTenantID() + " " + t.getFullName() + " " + t.getContactNumber() + " "
                    + t.getRoomNumber() + " " + t.getStatus()).toLowerCase(Locale.ROOT);

            if (hay.contains(needle)) {
                spec.row("Tenants", "Tenant", t.getFullName(),
                        t.getTenantID() + " \u00B7 Room " + t.getRoomNumber() + " \u00B7 " + t.getContactNumber(),
                        t.getStatus());
            }
        }

        for (Payment p : db.payments) {
            YearMonth m = p.getBillingYearMonth();
            String hay = (p.getPaymentID() + " " + db.tenantName(p.getTenantID()) + " " + p.getTenantID() + " "
                    + p.getPaymentMethod() + " " + (p.getReferenceNumber() == null ? "" : p.getReferenceNumber())
                    + " " + (m == null ? "" : m.toString()) + " " + p.getPaymentStatus()).toLowerCase(Locale.ROOT);

            if (hay.contains(needle)) {
                spec.row("Payments", "Payment", p.getPaymentID(),
                        db.tenantName(p.getTenantID()) + " \u00B7 " + (m == null ? "?" : m.toString())
                                + " \u00B7 " + peso(p.getAmountPaid()) + " \u00B7 " + p.getPaymentMethod(),
                        p.getPaymentStatus());
            }
        }

        TableSpec shown = new TableSpec("Search results",
                spec.rows.size() + (spec.rows.size() == 1 ? " result for \u201C" : " results for \u201C") + q + "\u201D",
                spec.cols);
        shown.rows.addAll(spec.rows);
        shown.keys.addAll(spec.keys);
        shown.empty = "Nothing matched \u201C" + q + "\u201D.";
        shown.open = key -> goTo((String) key);
        shown.act("Open section", true, true, key -> goTo((String) key));

        showLandlord("", tableView(shown));
    }

    // ==========================================
    // DASHBOARD
    // ==========================================

    private List<Object[]> unpaidFor(YearMonth month) {
        List<Object[]> out = new ArrayList<>();

        for (Tenant t : db.tenants) {
            if (!db.activeDuring(t, month)) {
                continue;
            }

            Rental r = db.rental(t.getTenantID());

            if (r == null) {
                continue;
            }

            LocalDate due = r.dueDateFor(month);

            if (due.isBefore(LocalDate.parse(t.getMoveInDate()))) {
                continue;
            }

            BigDecimal left = r.getMonthlyRent().subtract(db.paidFor(t.getTenantID(), month));

            if (left.signum() > 0) {
                out.add(new Object[]{due, t, r, left});
            }
        }

        out.sort(Comparator.comparing((Object[] o) -> (LocalDate) o[0]));
        return out;
    }

    private double[] chartValues(int mode, String[] labels) {
        double[] out = new double[labels.length];

        for (int i = 0; i < labels.length; i++) {
            int back = labels.length - 1 - i;
            BigDecimal sum = BigDecimal.ZERO;

            if (mode == 0) {
                YearMonth m = dashMonth.minusMonths(back);
                sum = db.collectedIn(m);
                labels[i] = monthShort(m);
            } else if (mode == 1) {
                int q = (dashMonth.getMonthValue() - 1) / 3;
                YearMonth start = YearMonth.of(dashMonth.getYear(), q * 3 + 1).minusMonths(3L * back);

                for (int k = 0; k < 3; k++) {
                    sum = sum.add(db.collectedIn(start.plusMonths(k)));
                }
                labels[i] = "Q" + ((start.getMonthValue() - 1) / 3 + 1) + " " + start.getYear();
            } else {
                int year = dashMonth.getYear() - back;

                for (int k = 1; k <= 12; k++) {
                    sum = sum.add(db.collectedIn(YearMonth.of(year, k)));
                }
                labels[i] = String.valueOf(year);
            }

            out[i] = sum.doubleValue();
        }
        return out;
    }

    private JComponent dashboardView() {
        YearMonth month = dashMonth;
        LocalDate today = LocalDate.now();

        int total = db.apartments.size();
        int vacant = 0;
        for (Apartment ap : db.apartments) {
            if (ap.isVacant()) {
                vacant++;
            }
        }
        int occupied = total - vacant;

        int active = 0;
        int movedOutThisYear = 0;
        for (Tenant t : db.tenants) {
            if (t.isActive()) {
                active++;
            } else if (t.getMoveOutDate() != null
                    && t.getMoveOutDate().startsWith(String.valueOf(today.getYear()))) {
                movedOutThisYear++;
            }
        }

        List<Object[]> dues = unpaidFor(month);
        BigDecimal unpaid = BigDecimal.ZERO;
        for (Object[] d : dues) {
            unpaid = unpaid.add((BigDecimal) d[3]);
        }

        BigDecimal collected = db.collectedIn(month);
        BigDecimal before = db.collectedIn(month.minusMonths(1));
        String change;
        Color changeColor = OK;

        if (before.signum() > 0) {
            double pct = collected.subtract(before).multiply(BigDecimal.valueOf(100))
                    .divide(before, 1, RoundingMode.HALF_UP).doubleValue();
            change = String.format(Locale.US, "%+.1f%% vs %s", pct,
                    month.minusMonths(1).getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            changeColor = pct < 0 ? BAD : OK;
        } else {
            change = "For " + month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            changeColor = MUTED;
        }

        JPanel stats = new JPanel(new GridLayout(1, 4, 16, 0));
        stats.setOpaque(false);
        stats.add(stat("Apartments", String.valueOf(total), vacant + " vacant", "home", OK));
        stats.add(stat("Active tenants", String.valueOf(active), movedOutThisYear + " moved out this yr", "users", OK));
        stats.add(stat("Collected", pesoShort(collected), change, "peso", changeColor));
        stats.add(stat("Unpaid balance", pesoShort(unpaid),
                dues.isEmpty() ? "All paid up" : dues.size() + (dues.size() == 1 ? " account" : " accounts"),
                "clock", dues.isEmpty() ? OK : BAD));

        // Income chart with Monthly / Quarterly / Yearly
        String[] labels = new String[chartMode == 2 ? 3 : chartMode == 1 ? 4 : 6];
        double[] vals = chartValues(chartMode, labels);
        LineChart line = new LineChart(vals, labels);

        Segmented seg = new Segmented(new String[]{"Monthly", "Quarterly", "Yearly"}, chartMode, i -> {
            chartMode = i;
            String[] ls = new String[i == 2 ? 3 : i == 1 ? 4 : 6];
            line.setData(chartValues(i, ls), ls);
        });

        JPanel chartHead = new JPanel(new BorderLayout(16, 0));
        chartHead.setOpaque(false);
        JPanel chartTitle = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        chartTitle.setOpaque(false);
        chartTitle.add(new Badge("chart", Color.WHITE, INK, 24));
        chartTitle.add(label("Rental income overview", INK, Font.BOLD, 16));
        chartHead.add(chartTitle, BorderLayout.CENTER);
        chartHead.add(seg, BorderLayout.EAST);

        Card chart = new Card(new BorderLayout(0, 12));
        chart.add(chartHead, BorderLayout.NORTH);
        chart.add(line, BorderLayout.CENTER);

        // Recent payments
        FlatButton viewAll = new FlatButton("View all", Color.WHITE, INK);
        viewAll.outline = new Color(0xD9D9DE);
        viewAll.setBorder(new EmptyBorder(7, 14, 7, 14));
        viewAll.addActionListener(e -> goTo("Payments"));

        JPanel recentHead = new JPanel(new BorderLayout(12, 0));
        recentHead.setOpaque(false);
        recentHead.add(label("Recent payments", INK, Font.BOLD, 16), BorderLayout.CENTER);
        recentHead.add(viewAll, BorderLayout.EAST);

        Card recent = new Card(new BorderLayout(0, 10));
        recent.add(recentHead, BorderLayout.NORTH);
        JPanel rl = new JPanel();
        rl.setOpaque(false);
        rl.setLayout(new BoxLayout(rl, BoxLayout.Y_AXIS));

        List<Payment> sorted = new ArrayList<>(db.payments);
        sorted.sort((p1, p2) -> p2.getPaymentDate().compareTo(p1.getPaymentDate()));

        for (int i = 0; i < Math.min(4, sorted.size()); i++) {
            Payment p = sorted.get(i);
            rl.add(listRow(db.tenantName(p.getTenantID()),
                    p.getPaymentMethod() + " \u00B7 " + p.getPaymentDate(),
                    label("+" + peso(p.getAmountPaid()), OK, Font.BOLD, 13)));
        }
        if (sorted.isEmpty()) {
            rl.add(label("No payments recorded yet.", MUTED, Font.PLAIN, 13));
        }
        recent.add(rl, BorderLayout.CENTER);

        // Occupancy
        Card occ = new Card(new BorderLayout(0, 10));
        occ.add(label("Occupancy", INK, Font.BOLD, 16), BorderLayout.NORTH);
        JPanel body = new JPanel(new GridLayout(1, 2, 14, 0));
        body.setOpaque(false);
        body.add(new Donut(total == 0 ? 0 : (double) occupied / total));
        JPanel leg = new JPanel(new GridLayout(3, 1));
        leg.setOpaque(false);
        leg.add(legend("Occupied", occupied));
        leg.add(legend("Vacant", vacant));
        leg.add(legend("Total", total));
        body.add(leg);
        occ.add(body, BorderLayout.CENTER);

        // Upcoming due dates
        Card due = new Card(new BorderLayout(0, 10));
        due.add(label("Upcoming due dates", INK, Font.BOLD, 16), BorderLayout.NORTH);
        JPanel dl = new JPanel();
        dl.setOpaque(false);
        dl.setLayout(new BoxLayout(dl, BoxLayout.Y_AXIS));

        for (int i = 0; i < Math.min(3, dues.size()); i++) {
            LocalDate d = (LocalDate) dues.get(i)[0];
            Tenant t = (Tenant) dues.get(i)[1];
            boolean late = d.isBefore(today);
            JPanel r = listRow(t.getFullName(), "Room " + t.getRoomNumber(),
                    late ? new Pill("Overdue", tint(BAD), BAD)
                         : new Pill("Due soon", new Color(0xF0F0F2), INK));
            JLabel date = new JLabel("<html><center>" + monthShort(YearMonth.from(d)).toUpperCase(Locale.ROOT)
                    + "<br><b style='font-size:14px'>" + d.getDayOfMonth() + "</b></center></html>");
            date.setForeground(MUTED);
            date.setFont(f(Font.PLAIN, 10));
            r.add(date, BorderLayout.WEST);
            dl.add(r);
        }
        if (dues.isEmpty()) {
            dl.add(label("Everyone is paid up for this month.", MUTED, Font.PLAIN, 13));
        }
        due.add(dl, BorderLayout.CENTER);

        MonthPicker picker = new MonthPicker(month, m -> {
            dashMonth = m;
            goTo("Dashboard");
        });

        JPanel pickerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        pickerRow.setOpaque(false);
        pickerRow.add(picker);

        JPanel page = pageColumn();
        JPanel head = header("Dashboard",
                "Welcome back, " + currentUser.getFullName() + ". Here\u2019s what\u2019s happening with your rentals today.",
                null);
        fixH(head, 74);
        page.add(head);
        fixH(pickerRow, 44);
        page.add(pickerRow);
        page.add(Box.createVerticalStrut(18));
        fixH(stats, 114);
        page.add(stats);
        page.add(Box.createVerticalStrut(18));
        page.add(row(chart, recent, 2, 1, 330));
        page.add(Box.createVerticalStrut(18));
        page.add(row(occ, due, 2, 1, 240));

        return scroll(page);
    }

    // ==========================================
    // APARTMENTS
    // ==========================================

    private JComponent apartmentsView() {
        TableSpec s = new TableSpec("Apartments", "Rooms, monthly rent, and availability.",
                "Room", "Monthly rent", "Tenant", "Status");

        for (Apartment a : db.apartments) {
            Tenant who = db.occupant(a.getRoomNumber());
            s.row(a, a.getRoomNumber(), peso(a.getMonthlyRentalPrice()),
                    who == null ? "\u2014" : who.getFullName(), a.getRoomStatus());
        }

        s.empty = "No apartments yet. Use \u201CAdd apartment\u201D to create the first one.";
        s.act("Add apartment", true, false, k -> addApartmentDialog());
        s.act("Change rent", false, true, k -> changeRentDialog((Apartment) k));
        s.act("Remove", false, true, k -> removeApartmentDialog((Apartment) k));
        s.open = k -> showText("Apartment", null, ((Apartment) k).getRoomDetails());
        return tableView(s);
    }

    private void addApartmentDialog() {
        JTextField room = field("");
        JTextField rent = field("");

        Form form = new Form(400);
        form.two("Room number", room, "Monthly rent (PHP)", rent);

        boolean ok = formLoop("Add apartment", "New rooms start as Vacant.", form, "Add apartment", () -> {
            String number = room.getText().trim();

            if (number.isEmpty()) {
                throw new IllegalArgumentException("Room number cannot be empty.");
            }

            if (db.apartment(number) != null) {
                throw new IllegalArgumentException("Room number already exists.");
            }

            db.apartments.add(new Apartment(number, parseMoney(rent.getText(), "Monthly rent", false)));
            db.saveApartments();
        });

        if (ok) {
            goTo("Apartments");
        }
    }

    private void changeRentDialog(Apartment apartment) {
        JTextField rent = field(apartment.getMonthlyRentalPrice().toPlainString());

        Form form = new Form(400);
        form.note(label("Room " + apartment.getRoomNumber() + " \u00B7 current rent "
                + peso(apartment.getMonthlyRentalPrice()), INK, Font.BOLD, 13));
        form.one("New monthly rent (PHP)", rent);

        boolean ok = formLoop("Change rent",
                "This applies to NEW leases only. Current tenants keep the rent in their lease, "
                        + "and past payments are unchanged.",
                form, "Save rent", () -> {
                    apartment.setMonthlyRentalPrice(parseMoney(rent.getText(), "Monthly rent", false));
                    db.saveApartments();
                });

        if (ok) {
            goTo("Apartments");
        }
    }

    private void removeApartmentDialog(Apartment apartment) {
        String room = apartment.getRoomNumber();

        if (!apartment.isVacant()) {
            message("Cannot remove", "Occupied apartments cannot be removed.");
            return;
        }

        if (db.roomHasHistory(room)) {
            message("Cannot remove", "This room has tenant history and cannot be removed.");
            return;
        }

        if (confirmBox("Remove apartment", "Remove room " + room + "?", "Remove")) {
            db.apartments.remove(apartment);
            db.saveApartments();
            goTo("Apartments");
        }
    }

    // ==========================================
    // TENANTS
    // ==========================================

    private JComponent tenantsView() {
        TableSpec s = new TableSpec("Tenants", "Everyone renting from you, past and present.",
                "Tenant ID", "Name", "Contact", "Room", "Move-in", "Status");

        for (Tenant t : db.tenants) {
            s.row(t, t.getTenantID(), t.getFullName(), t.getContactNumber(),
                    t.getRoomNumber(), t.getMoveInDate(), t.getStatus());
        }

        s.empty = "No tenants yet. Use \u201CRegister tenant\u201D to add one.";
        s.act("Register tenant", true, false, k -> registerTenantDialog());
        s.act("Details", false, true, k -> tenantDetails((Tenant) k));
        s.act("Update contact", false, true, k -> updateContactDialog((Tenant) k));
        s.act("Move out", false, true, k -> moveOutDialog((Tenant) k));
        s.open = k -> tenantDetails((Tenant) k);
        return tableView(s);
    }

    private void tenantDetails(Tenant t) {
        Rental r = db.rental(t.getTenantID());
        String text = t.getTenantInfo();

        if (r != null) {
            text += "\n\n--- LEASE ---\n" + r.getRentalSummary();
        } else {
            text += "\n\nNo lease record found.";
        }

        showText("Tenant details", t.getFullName() + " \u00B7 Room " + t.getRoomNumber(), text);
    }

    private void registerTenantDialog() {
        List<Apartment> vacant = new ArrayList<>();
        for (Apartment a : db.apartments) {
            if (a.isVacant()) {
                vacant.add(a);
            }
        }

        if (vacant.isEmpty()) {
            message("No vacant rooms", "Every apartment is occupied. Add an apartment or move a tenant out first.");
            return;
        }

        List<String> roomLabels = new ArrayList<>();
        for (Apartment a : vacant) {
            roomLabels.add("Room " + a.getRoomNumber() + " \u2013 " + peso(a.getMonthlyRentalPrice()) + " / month");
        }

        LocalDate today = LocalDate.now();

        JTextField id = field(db.nextTenantId());
        JTextField name = field("");
        JTextField contact = field("");
        JComboBox<String> roomBox = combo(roomLabels);
        JTextField moveIn = field(today.toString());
        JTextField leaseEnd = field(today.plusYears(1).toString());
        JTextField dueDay = field(String.valueOf(Math.min(today.getDayOfMonth(), 28)));
        JTextField user = field("");
        JPasswordField pass = passField();
        JTextField security = field(vacant.get(0).getMonthlyRentalPrice().toPlainString());
        JTextField advance = field(vacant.get(0).getMonthlyRentalPrice().toPlainString());

        roomBox.addActionListener(e -> {
            Apartment a = vacant.get(roomBox.getSelectedIndex());
            security.setText(a.getMonthlyRentalPrice().toPlainString());
            advance.setText(a.getMonthlyRentalPrice().toPlainString());
        });

        Form form = new Form(520);
        form.two("Tenant ID", id, "Full name", name);
        form.two("Contact number", contact, "Room", roomBox);
        form.two("Move-in date (YYYY-MM-DD)", moveIn, "Lease expiration (YYYY-MM-DD)", leaseEnd);
        form.two("Rent due day (1-31)", dueDay, "Portal username", user);
        form.two("Portal password (min 6)", pass, "Security deposit (PHP)", security);
        form.one("Advance payment (PHP)", advance);

        JScrollPane sp = new JScrollPane(form);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.setPreferredSize(new Dimension(548, 470));

        boolean ok = formLoop("Register new tenant",
                "Creates the tenant, the lease and the deposit record together.",
                sp, "Register tenant", () -> {
                    String tenantId = id.getText().trim();

                    if (tenantId.isEmpty()) {
                        throw new IllegalArgumentException("Tenant ID cannot be empty.");
                    }

                    if (db.isTenantIdTaken(tenantId)) {
                        throw new IllegalArgumentException(
                                "Tenant ID already exists (IDs of moved-out tenants cannot be reused).");
                    }

                    String fullName = name.getText().trim();

                    if (fullName.isEmpty()) {
                        throw new IllegalArgumentException("Full name cannot be empty.");
                    }

                    if (!User.isValidContactNumber(contact.getText())) {
                        throw new IllegalArgumentException(
                                "Invalid contact number. Use 7-20 digits, spaces, +, ( ), or -.");
                    }

                    Apartment room = vacant.get(roomBox.getSelectedIndex());

                    if (!room.isVacant()) {
                        throw new IllegalArgumentException("Apartment is already occupied.");
                    }

                    LocalDate in = parseDate(moveIn.getText(), "Move-in date");
                    LocalDate out = parseDate(leaseEnd.getText(), "Lease expiration date");

                    if (!out.isAfter(in)) {
                        throw new IllegalArgumentException("Lease expiration must be after the move-in date.");
                    }

                    int due = parseDay(dueDay.getText());
                    String username = user.getText().trim();

                    if (username.isEmpty()) {
                        throw new IllegalArgumentException("Username cannot be empty.");
                    }

                    if (db.isUsernameTaken(username)) {
                        throw new IllegalArgumentException("Username is already being used.");
                    }

                    String password = new String(pass.getPassword());

                    if (!User.isValidPassword(password)) {
                        throw new IllegalArgumentException("Password must contain at least 6 characters.");
                    }

                    BigDecimal sec = parseMoney(security.getText(), "Security deposit", true);
                    BigDecimal adv = parseMoney(advance.getText(), "Advance payment", true);

                    Tenant newTenant = new Tenant(tenantId, fullName, contact.getText(),
                            room.getRoomNumber(), in.toString(), username, password);
                    Deposit newDeposit = new Deposit(tenantId, sec, adv);
                    Rental newRental = new Rental(tenantId, room.getRoomNumber(),
                            room.getMonthlyRentalPrice(), due, in.toString(), out.toString());

                    db.tenants.add(newTenant);
                    db.deposits.add(newDeposit);
                    db.rentals.add(newRental);
                    room.markOccupied();
                    db.saveAll();
                });

        if (ok) {
            goTo("Tenants");
        }
    }

    private void updateContactDialog(Tenant tenant) {
        if (!tenant.isActive()) {
            message("Cannot update", "Only active tenants can be updated.");
            return;
        }

        JTextField contact = field(tenant.getContactNumber());

        Form form = new Form(400);
        form.note(label(tenant.getFullName() + " \u00B7 " + tenant.getTenantID(), INK, Font.BOLD, 13));
        form.one("Contact number", contact);

        boolean ok = formLoop("Update contact", null, form, "Save contact", () -> {
            if (!User.isValidContactNumber(contact.getText())) {
                throw new IllegalArgumentException("Invalid contact number. Use 7-20 digits, spaces, +, ( ), or -.");
            }

            tenant.updateContact(contact.getText());
            db.saveTenants();
        });

        if (ok) {
            goTo("Tenants");
        }
    }

    private void moveOutDialog(Tenant tenant) {
        if (!tenant.isActive()) {
            message("Cannot move out", "This tenant has already moved out.");
            return;
        }

        JTextField date = field(LocalDate.now().toString());

        Form form = new Form(400);
        form.note(label(tenant.getFullName() + " \u00B7 Room " + tenant.getRoomNumber(), INK, Font.BOLD, 13));
        form.one("Move-out date (YYYY-MM-DD)", date);

        boolean ok = formLoop("Move out tenant",
                "The tenant record, payment history and deposit are kept.",
                form, "Confirm move-out", () -> {
                    LocalDate out = parseDate(date.getText(), "Move-out date");
                    LocalDate in = LocalDate.parse(tenant.getMoveInDate());

                    if (out.isBefore(in)) {
                        throw new IllegalArgumentException(
                                "Move-out date cannot be before the move-in date (" + in + ").");
                    }

                    tenant.moveOut(out.toString());

                    Apartment room = db.apartment(tenant.getRoomNumber());

                    if (room != null && room.isOccupied()) {
                        room.markVacant();
                    }

                    db.saveAll();
                });

        if (!ok) {
            return;
        }

        String text = "Tenant successfully moved out.\nRoom " + tenant.getRoomNumber() + " is now Vacant.";
        Deposit deposit = db.deposit(tenant.getTenantID());

        if (deposit != null && Deposit.STATUS_HELD.equalsIgnoreCase(deposit.getDepositStatus())) {
            text += "\n\nReminder: the deposit is still Held (" + peso(deposit.getRefundableAmount())
                    + "). Use Deposits to apply or refund it.";
        }

        message("Move-out complete", text);
        goTo("Tenants");
    }

    // ==========================================
    // RENTALS
    // ==========================================

    private JComponent rentalsView() {
        TableSpec s = new TableSpec("Rentals", "One lease per tenant, with the rent agreed at move-in.",
                "Tenant", "Room", "Monthly rent", "Due day", "Lease start", "Lease end", "Next due", "Lease");

        for (Rental r : db.rentals) {
            Tenant t = db.tenant(r.getTenantID());
            boolean active = t == null || t.isActive();
            String status = r.checkLeaseStatus();

            if (!active) {
                status = "Moved out";
            } else if ("Active".equals(status) && r.isExpiringWithin(LEASE_WARNING_DAYS)) {
                status = "Expiring soon";
            }

            s.row(r, db.tenantName(r.getTenantID()), r.getRoomNumber(), peso(r.getMonthlyRent()), r.getDueDay(),
                    r.getLeaseStartDate(), r.getLeaseExpirationDate(),
                    active ? r.calculateNextDueDate() : "\u2014", status);
        }

        s.empty = "No leases yet. A lease is created when you register a tenant.";
        s.act("Lease details", false, true, k -> showRental((Rental) k));
        s.open = k -> showRental((Rental) k);
        return tableView(s);
    }

    private void showRental(Rental r) {
        showText("Lease", db.tenantName(r.getTenantID()) + " \u00B7 Room " + r.getRoomNumber(), r.getRentalSummary());
    }

    // ==========================================
    // PAYMENTS (Payment does all the billing math)
    // ==========================================

    private JComponent paymentsView() {
        List<Payment> sorted = new ArrayList<>(db.payments);
        sorted.sort((p1, p2) -> p2.getPaymentDate().compareTo(p1.getPaymentDate()));

        TableSpec s = new TableSpec("Payments", "Every payment, matched to the month it covers.",
                "Payment ID", "Tenant", "Billing month", "Paid on", "Rent", "Paid", "Method", "Balance", "Status");

        for (Payment p : sorted) {
            YearMonth m = p.getBillingYearMonth();
            s.row(p, p.getPaymentID(), db.tenantName(p.getTenantID()), m == null ? "Unknown" : m.toString(),
                    p.getPaymentDate(), peso(p.getMonthlyRent()), peso(p.getAmountPaid()), p.getPaymentMethod(),
                    p.getRemainingBalance() == null ? "-" : peso(p.getRemainingBalance()), p.getPaymentStatus());
        }

        s.empty = "No payments recorded yet.";
        s.act("Record payment", true, false, k -> recordPaymentDialog());
        s.act("View receipt", false, true, k -> showReceipt((Payment) k));
        s.open = k -> showReceipt((Payment) k);
        return tableView(s);
    }

    private void showReceipt(Payment p) {
        showText("Payment receipt", db.tenantName(p.getTenantID()), p.getPaymentReceipt());
    }

    private void recordPaymentDialog() {
        List<Tenant> activeTenants = new ArrayList<>();
        for (Tenant t : db.tenants) {
            if (t.isActive()) {
                activeTenants.add(t);
            }
        }

        if (activeTenants.isEmpty()) {
            message("No active tenants", "There are no active tenants yet.");
            return;
        }

        List<String> tenantLabels = new ArrayList<>();
        for (Tenant t : activeTenants) {
            tenantLabels.add(t.getTenantID() + " \u2013 " + t.getFullName() + " (Room " + t.getRoomNumber() + ")");
        }

        LocalDate today = LocalDate.now();

        JComboBox<String> tenantBox = combo(tenantLabels);
        JTextField date = field(today.toString());
        JTextField month = field(YearMonth.from(today).toString());
        JComboBox<String> method = combo(Arrays.asList(
                Payment.METHOD_CASH, Payment.METHOD_GCASH, Payment.METHOD_BANK, Payment.METHOD_ADVANCE));
        JTextField amount = field("");
        JTextField ref = field("");

        JLabel info = label(" ", INK, Font.BOLD, 13);
        JPanel infoBox = new JPanel(new BorderLayout());
        infoBox.setBackground(new Color(0xF7F7F8));
        infoBox.setBorder(new EmptyBorder(10, 14, 10, 14));
        infoBox.add(info, BorderLayout.CENTER);

        Runnable refresh = () -> {
            Tenant t = activeTenants.get(tenantBox.getSelectedIndex());
            Rental r = db.rental(t.getTenantID());

            ref.setEnabled(Payment.requiresReference(String.valueOf(method.getSelectedItem())));

            if (r == null) {
                info.setText("No lease record for this tenant.");
                return;
            }

            YearMonth bm;
            try {
                String raw = month.getText().trim();
                String dRaw = date.getText().trim();
                if (raw.isEmpty()) {
                    if (dRaw.length() < 7) {
                        info.setText("Enter the billing month as YYYY-MM.");
                        return;
                    }
                    bm = YearMonth.parse(dRaw.substring(0, 7));
                } else {
                    bm = YearMonth.parse(raw);
                }
            } catch (RuntimeException e) {
                info.setText("Enter the billing month as YYYY-MM.");
                return;
            }

            BigDecimal paid = db.paidFor(t.getTenantID(), bm);
            BigDecimal left = r.getMonthlyRent().subtract(paid).max(BigDecimal.ZERO);
            info.setText("<html>" + bm + " \u00B7 Rent " + peso(r.getMonthlyRent()) + " \u00B7 Paid " + peso(paid)
                    + " \u00B7 <span style='color:" + (left.signum() > 0 ? "#B45309" : "#15803D") + "'>Remaining "
                    + peso(left) + "</span></html>");
        };

        tenantBox.addActionListener(e -> refresh.run());
        method.addActionListener(e -> refresh.run());
        onChange(month, refresh);
        onChange(date, refresh);
        refresh.run();

        Form form = new Form(480);
        form.one("Tenant", tenantBox);
        form.two("Payment date (YYYY-MM-DD)", date, "Billing month (YYYY-MM)", month);
        form.note(infoBox);
        form.two("Amount (PHP)", amount, "Method", method);
        form.one("Reference no. (GCash / bank transfer)", ref);

        boolean ok = formLoop("Record payment",
                "Payment calculates the remaining balance and status for you.",
                form, "Save payment", () -> {
                    Tenant tenant = activeTenants.get(tenantBox.getSelectedIndex());
                    Rental rental = db.rental(tenant.getTenantID());

                    if (rental == null) {
                        throw new IllegalArgumentException("No lease record found for this tenant.");
                    }

                    LocalDate paidOn = parseDate(date.getText(), "Payment date");
                    YearMonth billing;

                    try {
                        String raw = month.getText().trim();
                        billing = raw.isEmpty() ? YearMonth.from(paidOn) : YearMonth.parse(raw);
                    } catch (DateTimeParseException e) {
                        throw new IllegalArgumentException("Billing month must use YYYY-MM.");
                    }

                    YearMonth firstMonth = YearMonth.from(LocalDate.parse(tenant.getMoveInDate()));

                    if (billing.isBefore(firstMonth)) {
                        throw new IllegalArgumentException(
                                "Billing month is before the tenant moved in (" + firstMonth + ").");
                    }

                    BigDecimal previouslyPaid = db.paidFor(tenant.getTenantID(), billing);
                    BigDecimal remaining = rental.getMonthlyRent().subtract(previouslyPaid);

                    if (remaining.signum() <= 0) {
                        throw new IllegalArgumentException("Rent for " + billing + " is already fully paid.");
                    }

                    BigDecimal amt = parseMoney(amount.getText(), "Amount", false);

                    if (amt.compareTo(remaining) > 0) {
                        throw new IllegalArgumentException(
                                "Payment exceeds remaining balance of " + peso(remaining));
                    }

                    String chosen = String.valueOf(method.getSelectedItem());
                    Deposit deposit = null;

                    if (Payment.METHOD_ADVANCE.equalsIgnoreCase(chosen)) {
                        deposit = db.deposit(tenant.getTenantID());

                        if (deposit == null
                                || !Deposit.STATUS_HELD.equalsIgnoreCase(deposit.getDepositStatus())
                                || deposit.getAdvanceRemaining().compareTo(amt) < 0) {
                            throw new IllegalArgumentException("Not enough advance payment available "
                                    + (deposit == null ? "(no deposit record)."
                                    : "(" + peso(deposit.getAdvanceRemaining()) + " left)."));
                        }
                    }

                    Payment p = new Payment(db.nextPaymentId(), tenant.getTenantID(), billing.toString(),
                            rental.getMonthlyRent(), previouslyPaid, amt, chosen, ref.getText(), paidOn.toString());

                    if (deposit != null) {
                        deposit.useAdvance(amt);
                    }

                    db.payments.add(p);
                    db.saveAll();
                    lastReceipt = p;
                });

        if (ok && lastReceipt != null) {
            Payment p = lastReceipt;
            lastReceipt = null;
            showText("Payment recorded", "Payment successfully recorded!", p.getPaymentReceipt());
            goTo("Payments");
        }
    }

    private Payment lastReceipt;

    // ==========================================
    // DEPOSITS
    // ==========================================

    private JComponent depositsView() {
        TableSpec s = new TableSpec("Deposits", "Security deposits and advance payments.",
                "Tenant", "Security deposit", "Advance", "Advance left", "Refundable now", "Status");

        for (Deposit d : db.deposits) {
            s.row(d, db.tenantName(d.getTenantID()), peso(d.getSecurityDeposit()), peso(d.getAdvancePayment()),
                    peso(d.getAdvanceRemaining()), peso(d.getRefundableAmount()), d.getDepositStatus());
        }

        s.empty = "No deposit records yet.";
        s.act("Details", false, true, k -> depositDetails((Deposit) k));
        s.act("Apply deposit", false, true, k -> depositAction((Deposit) k, true));
        s.act("Refund deposit", false, true, k -> depositAction((Deposit) k, false));
        s.open = k -> depositDetails((Deposit) k);
        return tableView(s);
    }

    private void depositDetails(Deposit d) {
        Tenant t = db.tenant(d.getTenantID());

        Reportable item = d;
        String text = item.getReportSummary();

        if (t == null) {
            text += "\nTenant Status      : Unknown (no tenant record)";
        } else {
            text += "\nTenant Name        : " + t.getFullName()
                    + "\nTenant Status      : " + (t.isActive() ? "Active" : "Moved Out (" + t.getMoveOutDate() + ")");
        }

        showText("Deposit", db.tenantName(d.getTenantID()), text);
    }

    private void depositAction(Deposit deposit, boolean apply) {
        Tenant t = db.tenant(deposit.getTenantID());

        if (t != null && t.isActive()) {
            message("Not yet", "A deposit can only be " + (apply ? "applied" : "refunded")
                    + " after the tenant has moved out.");
            return;
        }

        String question = apply
                ? "Mark this deposit as Applied?"
                : "Refund " + peso(deposit.getRefundableAmount()) + " to the tenant?";

        boolean ok = dialog(apply ? "Apply deposit" : "Refund deposit", question,
                monoBody(deposit.getDepositSummary()), apply ? "Mark as Applied" : "Mark as Refunded", "Cancel");

        if (!ok) {
            return;
        }

        try {
            if (apply) {
                deposit.applyDeposit();
            } else {
                deposit.refundDeposit();
            }

            db.saveDeposits();
            goTo("Deposits");
        } catch (RuntimeException e) {
            message("Cannot update deposit", e.getMessage());
        }
    }

    // ==========================================
    // REPORTS
    // ==========================================

    private JComponent reportsView() {
        YearMonth month = reportMonth;

        BigDecimal cash = BigDecimal.ZERO;
        int transactions = 0;
        Map<String, BigDecimal> byMethod = new TreeMap<>();

        for (Payment p : db.payments) {
            try {
                if (YearMonth.from(LocalDate.parse(p.getPaymentDate())).equals(month)) {
                    cash = cash.add(p.getAmountPaid());
                    transactions++;
                    byMethod.merge(p.getPaymentMethod(), p.getAmountPaid(), BigDecimal::add);
                }
            } catch (DateTimeParseException e) {
                // skip records with a broken date, like Main does
            }
        }

        BigDecimal collectedFor = db.collectedIn(month);
        BigDecimal expected = BigDecimal.ZERO;
        List<Object[]> tenantRows = new ArrayList<>();

        for (Tenant t : db.tenants) {
            if (db.activeDuring(t, month)) {
                Rental r = db.rental(t.getTenantID());

                if (r != null) {
                    expected = expected.add(r.getMonthlyRent());
                }

                BigDecimal paid = db.paidFor(t.getTenantID(), month);
                String state = r == null ? "No lease"
                        : paid.compareTo(r.getMonthlyRent()) >= 0 ? "Paid"
                        : paid.signum() > 0 ? "Partial" : "Unpaid";
                tenantRows.add(new Object[]{t.getTenantID(), t.getFullName(), t.getRoomNumber(),
                        peso(paid), state});
            }
        }

        int occupiedRooms = 0;
        List<Object[]> roomRows = new ArrayList<>();
        for (Apartment a : db.apartments) {
            boolean occupied = db.roomOccupiedDuring(a, month);

            if (occupied) {
                occupiedRooms++;
            }
            roomRows.add(new Object[]{a.getRoomNumber(), peso(a.getMonthlyRentalPrice()),
                    occupied ? "Occupied" : "Vacant"});
        }

        String efficiency = expected.signum() > 0
                ? collectedFor.multiply(BigDecimal.valueOf(100)).divide(expected, 1, RoundingMode.HALF_UP) + "%"
                : "\u2014";

        JPanel stats = new JPanel(new GridLayout(1, 4, 16, 0));
        stats.setOpaque(false);
        stats.add(stat("Cash received", pesoShort(cash), transactions + " transaction" + (transactions == 1 ? "" : "s"), "peso", MUTED));
        stats.add(stat("Rent expected", pesoShort(expected), tenantRows.size() + " tenants in " + monthShort(month), "doc", MUTED));
        stats.add(stat("Rent collected", pesoShort(collectedFor), "For " + monthName(month), "card", OK));
        stats.add(stat("Collection efficiency", efficiency, occupiedRooms + " / " + db.apartments.size() + " rooms occupied",
                "chart", OK));

        Card methods = new Card(new BorderLayout(0, 10));
        methods.add(label("Cash received by method", INK, Font.BOLD, 16), BorderLayout.NORTH);
        JPanel ml = new JPanel();
        ml.setOpaque(false);
        ml.setLayout(new BoxLayout(ml, BoxLayout.Y_AXIS));
        for (Map.Entry<String, BigDecimal> e : byMethod.entrySet()) {
            ml.add(infoRow(e.getKey(), peso(e.getValue())));
        }
        if (byMethod.isEmpty()) {
            ml.add(label("No payments were received in " + monthName(month) + ".", MUTED, Font.PLAIN, 13));
        }
        methods.add(ml, BorderLayout.CENTER);

        Card rooms = tableCard(buildTable(new String[]{"Room", "Monthly rent", "Status"}, roomRows, 2), "No apartments.");

        JPanel roomsWrap = new JPanel(new BorderLayout(0, 10));
        roomsWrap.setOpaque(false);
        roomsWrap.add(label("Apartment occupancy (" + month + ")", INK, Font.BOLD, 16), BorderLayout.NORTH);
        roomsWrap.add(rooms, BorderLayout.CENTER);

        Card tenantsCard = tableCard(buildTable(
                new String[]{"Tenant ID", "Name", "Room", "Paid for " + month, "Status"}, tenantRows, 4),
                "No tenants lived here in " + monthName(month) + ".");

        MonthPicker picker = new MonthPicker(month, m -> {
            reportMonth = m;
            goTo("Reports");
        });

        JPanel pickerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        pickerRow.setOpaque(false);
        pickerRow.add(picker);

        JPanel page = pageColumn();
        JPanel head = header("Reports", "Monthly financial report: cash received, rent collected and occupancy.", null);
        fixH(head, 74);
        page.add(head);
        fixH(pickerRow, 44);
        page.add(pickerRow);
        page.add(Box.createVerticalStrut(18));
        fixH(stats, 114);
        page.add(stats);
        page.add(Box.createVerticalStrut(18));

        JPanel two = new JPanel(new GridBagLayout());
        two.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.weightx = 1;
        c.gridx = 0;
        c.insets = new Insets(0, 0, 0, 10);
        methods.setPreferredSize(new Dimension(1, 305));
        two.add(methods, c);
        c.gridx = 1;
        c.insets = new Insets(0, 10, 0, 0);
        roomsWrap.setPreferredSize(new Dimension(1, 305));
        two.add(roomsWrap, c);
        fixH(two, 305);
        page.add(two);

        page.add(Box.createVerticalStrut(18));
        JLabel tt = label("Tenants in " + monthName(month), INK, Font.BOLD, 16);
        tt.setBorder(new EmptyBorder(0, 0, 10, 0));
        page.add(tt);
        fixH(tenantsCard, Math.max(155, Math.min(tenantRows.size(), 6) * 49 + 72));
        page.add(tenantsCard);

        return scroll(page);
    }

    // ==========================================
    // TENANT PORTAL (view mode)
    // ==========================================

    private JComponent buildTenantApp() {
        String[][] items = {
                {"My Profile", "user"}, {"My Payments", "card"}, {"Password", "lock"}
        };

        JPanel side = buildSidebar(items, tenantNav, this::goTenant, "Tenant workspace");

        tenantGreeting = label("", INK, Font.BOLD, 18);
        tenantRoomPill = new Pill("", new Color(0xF0F0F2), INK);

        tenantAvatar = new Avatar();
        tenantAvatar.menu = accountMenu(() -> goTenant("My Profile"));

        JPanel left = new JPanel(new GridBagLayout());
        left.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(0, 0, 0, 12);
        left.add(tenantGreeting, g);
        g.insets = new Insets(0, 0, 0, 0);
        left.add(tenantRoomPill, g);

        JPanel avatarWrap = new JPanel(new GridBagLayout());
        avatarWrap.setOpaque(false);
        avatarWrap.add(tenantAvatar);

        JPanel bar = topBar();
        bar.add(left, BorderLayout.WEST);
        bar.add(avatarWrap, BorderLayout.EAST);

        tenantContent.setBackground(BG);

        JPanel right = new JPanel(new BorderLayout());
        right.add(bar, BorderLayout.NORTH);
        right.add(tenantContent, BorderLayout.CENTER);

        JPanel app = new JPanel(new BorderLayout());
        app.add(side, BorderLayout.WEST);
        app.add(right, BorderLayout.CENTER);
        return app;
    }

    private void openTenantPortal() {
        tenantAvatar.text = initials(currentUser.getFullName());
        screens.show(rootPanel, "tenant");
        goTenant("My Profile");
    }

    private Tenant me() {
        return db.tenant(currentUserId);
    }

    private void goTenant(String name) {
        db.reload();
        Tenant t = me();

        if (t == null || !t.isActive()) {
            logout();
            message("Signed out", "This tenant account is no longer active.");
            return;
        }

        tenantGreeting.setText("Hello, " + t.getFullName().split("\\s+")[0]);
        tenantRoomPill.setText("Room " + t.getRoomNumber());

        JComponent view;

        switch (name) {
            case "My Payments": view = tenantPaymentsView(t); break;
            case "Password": view = tenantPasswordView(); break;
            default: view = tenantProfileView(t);
        }

        markNav(tenantNav, name);
        tenantContent.removeAll();
        tenantContent.add(view, BorderLayout.CENTER);
        tenantContent.revalidate();
        tenantContent.repaint();
    }

    private JComponent tenantProfileView(Tenant t) {
        Rental r = db.rental(t.getTenantID());

        Card personal = new Card(new BorderLayout(0, 12));
        personal.add(label("My rental information", INK, Font.BOLD, 16), BorderLayout.NORTH);
        JPanel pl = new JPanel();
        pl.setOpaque(false);
        pl.setLayout(new BoxLayout(pl, BoxLayout.Y_AXIS));
        pl.add(infoRow("Tenant ID", t.getTenantID()));
        pl.add(infoRow("Full name", t.getFullName()));
        pl.add(infoRow("Contact number", t.getContactNumber()));
        pl.add(infoRow("Room number", t.getRoomNumber()));
        pl.add(infoRow("Move-in date", t.getMoveInDate()));
        pl.add(infoRow("Account status", t.getStatus()));
        personal.add(pl, BorderLayout.CENTER);

        Card lease = new Card(new BorderLayout(0, 12));
        lease.add(label("My lease", INK, Font.BOLD, 16), BorderLayout.NORTH);
        JPanel ll = new JPanel();
        ll.setOpaque(false);
        ll.setLayout(new BoxLayout(ll, BoxLayout.Y_AXIS));

        if (r != null) {
            String status = r.checkLeaseStatus();
            if ("Active".equals(status) && r.isExpiringWithin(LEASE_WARNING_DAYS)) {
                status = "Expiring soon";
            }

            ll.add(infoRow("Monthly rent", peso(r.getMonthlyRent())));
            ll.add(infoRow("Rent due day", r.getDueDay() + " of every month"));
            ll.add(infoRow("Next due date", r.calculateNextDueDate()));
            ll.add(infoRow("Lease start", r.getLeaseStartDate()));
            ll.add(infoRow("Lease expiration", r.getLeaseExpirationDate()));
            ll.add(infoRow("Lease status", status));
        } else {
            ll.add(label("Lease information is unavailable.", MUTED, Font.PLAIN, 14));
        }
        lease.add(ll, BorderLayout.CENTER);

        JPanel two = new JPanel(new GridLayout(1, 2, 18, 0));
        two.setOpaque(false);
        two.add(personal);
        two.add(lease);
        fixH(two, 350);

        JPanel page = pageColumn();
        JPanel head = header("My Profile", "Your rental information and lease details.", null);
        fixH(head, 74);
        page.add(head);
        page.add(two);
        return scroll(page);
    }

    private JComponent tenantPaymentsView(Tenant t) {
        Rental r = db.rental(t.getTenantID());
        YearMonth now = YearMonth.now();

        JPanel stats = new JPanel(new GridLayout(1, 4, 16, 0));
        stats.setOpaque(false);

        if (r != null) {
            BigDecimal paid = db.paidFor(t.getTenantID(), now);
            BigDecimal balance = r.getMonthlyRent().subtract(paid).max(BigDecimal.ZERO);
            String next = r.calculateNextDueDate();
            boolean none = next.startsWith("None");

            stats.add(stat("Monthly rent", pesoShort(r.getMonthlyRent()), "For " + monthName(now), "home", MUTED));
            stats.add(stat("Paid so far", pesoShort(paid), "This month", "peso", OK));
            stats.add(stat("Balance", pesoShort(balance), balance.signum() > 0 ? "Still to pay" : "All paid up",
                    "clock", balance.signum() > 0 ? BAD : OK));
            stats.add(stat("Next due date", none ? "None" : next,
                    none ? "Lease ends " + r.getLeaseExpirationDate() : "Due day " + r.getDueDay(), "doc", MUTED));
        } else {
            stats.add(stat("Lease", "\u2014", "No lease record found", "doc", MUTED));
        }

        List<Payment> mine = new ArrayList<>();
        for (Payment p : db.payments) {
            if (p.getTenantID() != null && p.getTenantID().equalsIgnoreCase(t.getTenantID())) {
                mine.add(p);
            }
        }
        mine.sort((p1, p2) -> p2.getPaymentDate().compareTo(p1.getPaymentDate()));

        TableSpec s = new TableSpec("My Payments", "Your payment history and balances.",
                "Payment ID", "Billing month", "Paid on", "Rent", "Paid", "Method", "Balance", "Status");

        for (Payment p : mine) {
            YearMonth m = p.getBillingYearMonth();
            s.row(p, p.getPaymentID(), m == null ? "Unknown" : m.toString(), p.getPaymentDate(),
                    peso(p.getMonthlyRent()), peso(p.getAmountPaid()), p.getPaymentMethod(),
                    p.getRemainingBalance() == null ? "-" : peso(p.getRemainingBalance()), p.getPaymentStatus());
        }

        s.empty = "No payment transactions recorded.";
        s.act("View receipt", false, true, k -> showReceipt((Payment) k));
        s.open = k -> showReceipt((Payment) k);

        stats.setPreferredSize(new Dimension(10, 114));
        s.above = stats;
        return tableView(s);
    }

    private JComponent tenantPasswordView() {
        JPasswordField cur = passField();
        JPasswordField nw = passField();
        JPasswordField cf = passField();

        Form form = new Form(420);
        form.one("Current password", cur);
        form.one("New password (min 6 characters)", nw);
        form.one("Confirm new password", cf);

        FlatButton save = new FlatButton("Update password", INK, Color.WHITE);
        save.addActionListener(e -> {
            try {
                applyPasswordChange(new String(cur.getPassword()), new String(nw.getPassword()),
                        new String(cf.getPassword()));
                cur.setText("");
                nw.setText("");
                cf.setText("");
                message("Password changed", "Password changed successfully!");
            } catch (RuntimeException ex) {
                message("Please check your input", ex.getMessage() == null ? ex.toString() : ex.getMessage());
            }
        });

        Card card = new Card(new BorderLayout(0, 14));
        card.add(label("Change my password", INK, Font.BOLD, 16), BorderLayout.NORTH);
        card.add(form, BorderLayout.CENTER);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        btnRow.setOpaque(false);
        btnRow.add(save);
        card.add(btnRow, BorderLayout.SOUTH);

        JPanel holder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        holder.setOpaque(false);
        card.setPreferredSize(new Dimension(480, 365));
        holder.add(card);

        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(BG);
        page.setBorder(new EmptyBorder(28, 32, 32, 32));
        page.add(header("Password", "Keep your tenant account secure.", null), BorderLayout.NORTH);
        page.add(holder, BorderLayout.CENTER);
        return page;
    }
}