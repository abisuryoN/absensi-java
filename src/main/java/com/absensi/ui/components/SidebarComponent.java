package com.absensi.ui.components;

import com.absensi.model.Karyawan;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.List;

public class SidebarComponent extends JPanel {

    private static final int SIDEBAR_WIDTH = 290;
    private static final int AVATAR_SIZE   = 80;
    private static final int MENU_HEIGHT   = 46;
    private static final int PAD_H         = 10;

    private static final Color C_BG     = new Color(0x1B, 0x2A, 0x4A);
    private static final Color C_HOVER  = new Color(0x25, 0x3A, 0x62);
    private static final Color C_ACTIVE = new Color(0x2E, 0x4A, 0x78);
    private static final Color C_ACCENT = new Color(0x00, 0xC9, 0xA7);
    private static final Color C_TEXT   = new Color(0xE2, 0xE8, 0xF0);
    private static final Color C_SEP    = new Color(255, 255, 255, 35);

    private final Karyawan      user;
    private final List<MenuBtn> items = new ArrayList<>();
    private int                 activeIdx = 0;
    private MenuClickListener   listener;

    public interface MenuClickListener {
        void onMenuClick(String key, int idx);
    }

    public SidebarComponent(Karyawan user) {
        this.user = user;
        setPreferredSize(new Dimension(SIDEBAR_WIDTH, 0));
        setBackground(C_BG);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        add(Box.createVerticalStrut(20));
        add(buildProfilPanel());
        add(Box.createVerticalStrut(14));
        add(buildSep());
        add(Box.createVerticalStrut(8));
    }

    // ── API publik ────────────────────────────────────────────

    public void tambahMenu(String ikon, String label, String key) {
        MenuBtn btn = new MenuBtn(ikon, label, key, items.size());
        items.add(btn);
        add(btn);
    }

    public void tambahSeparator() {
        add(Box.createVerticalStrut(4));
        add(buildSep());
        add(Box.createVerticalStrut(4));
    }

    public void tambahSpacer() { add(Box.createVerticalGlue()); }

    public void setMenuClickListener(MenuClickListener l) { this.listener = l; }

    public void setActiveMenu(int idx) {
        activeIdx = idx;
        items.forEach(Component::repaint);
    }

    // ── Panel profil ─────────────────────────────────────────

    private JPanel buildProfilPanel() {
        JPanel outer = new JPanel();
        outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
        outer.setBackground(C_BG);
        outer.setAlignmentX(LEFT_ALIGNMENT);
        outer.setMaximumSize(new Dimension(SIDEBAR_WIDTH, 145));

        outer.add(makeCenter(buildAvatar()));
        outer.add(Box.createVerticalStrut(8));

        JLabel lblNama = new JLabel(user.getNama());
        lblNama.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblNama.setForeground(C_TEXT);
        outer.add(makeCenter(lblNama));
        outer.add(Box.createVerticalStrut(3));

        JLabel lblBagian = new JLabel(user.getBagian());
        lblBagian.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblBagian.setForeground(C_ACCENT);
        outer.add(makeCenter(lblBagian));

        return outer;
    }

    private JPanel makeCenter(JComponent comp) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        row.setBackground(C_BG);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(SIDEBAR_WIDTH, comp.getPreferredSize().height + 4));
        row.add(comp);
        return row;
    }

    // ── Avatar ───────────────────────────────────────────────

    private JLabel buildAvatar() {
        String path = user.getFotoProfil();
        if (path != null && !path.isBlank()) {
            try {
                File f = new File(path);
                if (f.exists()) {
                    BufferedImage src = ImageIO.read(f);
                    if (src != null) {
                        // Scale dengan BICUBIC — tajam, tidak blur
                        BufferedImage scaled = new BufferedImage(
                            AVATAR_SIZE, AVATAR_SIZE, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g2 = scaled.createGraphics();
                        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                            RenderingHints.VALUE_RENDER_QUALITY);
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.drawImage(src, 0, 0, AVATAR_SIZE, AVATAR_SIZE, null);
                        g2.dispose();

                        JLabel lbl = new JLabel(cropCircle(scaled, AVATAR_SIZE));
                        lbl.setPreferredSize(new Dimension(AVATAR_SIZE, AVATAR_SIZE));
                        return lbl;
                    }
                }
            } catch (Exception ignored) {}
        }
        return buildInitialAvatar();
    }

    private JLabel buildInitialAvatar() {
        JLabel lbl = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(C_ACCENT);
                g2.fillOval(0, 0, AVATAR_SIZE, AVATAR_SIZE);
                g2.setColor(new Color(255, 255, 255, 70));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(1, 1, AVATAR_SIZE - 2, AVATAR_SIZE - 2);
                String ini = user.getNama().isEmpty() ? "?"
                    : user.getNama().substring(0, 1).toUpperCase();
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 24));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(ini,
                    (AVATAR_SIZE - fm.stringWidth(ini)) / 2,
                    (AVATAR_SIZE + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        lbl.setPreferredSize(new Dimension(AVATAR_SIZE, AVATAR_SIZE));
        return lbl;
    }

    private ImageIcon cropCircle(BufferedImage img, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_QUALITY);
        g2.setClip(new Ellipse2D.Float(0, 0, size, size));
        g2.drawImage(img, 0, 0, size, size, null);
        g2.setClip(null);
        g2.setColor(new Color(255, 255, 255, 90));
        g2.setStroke(new BasicStroke(2.0f));
        g2.drawOval(1, 1, size - 2, size - 2);
        g2.dispose();
        return new ImageIcon(out);
    }

    // ── Menu item ─────────────────────────────────────────────

    private class MenuBtn extends JPanel {
        private final String  ikon, label, key;
        private final int     idx;
        private boolean       hov = false;

        MenuBtn(String ikon, String label, String key, int idx) {
            this.ikon  = ikon;
            this.label = label;
            this.key   = key;
            this.idx   = idx;
            setOpaque(false);
            setMaximumSize(new Dimension(SIDEBAR_WIDTH, MENU_HEIGHT));
            setPreferredSize(new Dimension(SIDEBAR_WIDTH, MENU_HEIGHT));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hov = true;  repaint(); }
                @Override public void mouseExited (MouseEvent e) { hov = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) {
                    activeIdx = idx;
                    items.forEach(Component::repaint);
                    if (listener != null) listener.onMenuClick(key, idx);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            boolean act = (activeIdx == idx);

            // Background
            if (act) {
                g2.setColor(C_ACTIVE);
                g2.fillRoundRect(PAD_H / 2, 3, w - PAD_H, h - 6, 8, 8);
                g2.setColor(C_ACCENT);
                g2.setStroke(new BasicStroke(3f,
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(PAD_H / 2 + 2, 9, PAD_H / 2 + 2, h - 9);
            } else if (hov) {
                g2.setColor(C_HOVER);
                g2.fillRoundRect(PAD_H / 2, 3, w - PAD_H, h - 6, 8, 8);
            }

            // Ikon
            int iconX = PAD_H + 8;
            g2.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 13));
            g2.setColor(act ? C_ACCENT : C_TEXT);
            g2.drawString(ikon, iconX, h / 2 + 5);

            // Label — clip dinamis berdasarkan lebar panel aktual
            int labelX = iconX + 24;
            g2.setColor(act ? Color.WHITE : C_TEXT);
            g2.setFont(act
                ? new Font("Segoe UI", Font.BOLD, 13)
                : new Font("Segoe UI", Font.PLAIN, 13));
            FontMetrics fm = g2.getFontMetrics();
            int availW = w - labelX - 8;
            g2.setClip(labelX, 0, availW, h);
            g2.drawString(label, labelX,
                (h + fm.getAscent() - fm.getDescent()) / 2);
            g2.setClip(null);

            g2.dispose();
        }
    }

    // ── Separator ─────────────────────────────────────────────

    private JPanel buildSep() {
        JPanel p = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(C_SEP);
                g.fillRect(PAD_H, 0, getWidth() - PAD_H * 2, 1);
            }
        };
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(SIDEBAR_WIDTH, 1));
        p.setPreferredSize(new Dimension(SIDEBAR_WIDTH, 1));
        return p;
    }
}