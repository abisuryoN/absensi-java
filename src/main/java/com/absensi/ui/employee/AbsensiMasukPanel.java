package com.absensi.ui.employee;

import com.absensi.model.Absensi;
import com.absensi.model.Karyawan;
import com.absensi.service.AbsensiService;
import com.absensi.service.AbsensiService.HasilAbsensi;
import com.absensi.ui.components.WebcamCaptureDialog;
import com.absensi.util.AbsensiUtil;
import com.absensi.util.LocationServer;
import com.absensi.util.LocationUtil;
import com.absensi.util.UITheme;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.File;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class AbsensiMasukPanel extends JPanel {

    private final Karyawan       user;
    private final AbsensiService absensiService = new AbsensiService();

    private JLabel   lblStatusIcon;
    private JLabel   lblStatusJudul;
    private JLabel   lblStatusDetail;
    private JLabel   lblJamMasuk;
    private JLabel   lblLokasiMasuk;
    private JLabel   lblJamSekarang;
    private JLabel   lblFotoPreview;
    private JLabel   lblFotoKet;
    private JLabel   lblGpsInfo;
    private JLabel   lblGpsIcon;
    private JButton  btnAbsenMasuk;
    private JButton  btnRefresh;
    private Timer    jamTimer;
    private Runnable onAbsenBerhasil;

    public AbsensiMasukPanel(Karyawan user) {
        this.user = user;
        initUI();
        startJamTimer();
        refreshStatus();
    }

    public void setOnAbsenBerhasil(Runnable callback) {
        this.onAbsenBerhasil = callback;
    }

    // ── UI ────────────────────────────────────────────────────

    private void initUI() {
        setLayout(new BorderLayout(0, 0));
        setBackground(UITheme.BG_MAIN);
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        add(buildTopRow(),   BorderLayout.NORTH);
        add(buildMainArea(), BorderLayout.CENTER);
    }

    private JPanel buildTopRow() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(UITheme.BG_MAIN);
        p.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));

        JLabel lblTitle = new JLabel("Absensi Masuk");
        lblTitle.setFont(UITheme.FONT_SUBTITLE);
        lblTitle.setForeground(UITheme.PRIMARY);
        p.add(lblTitle, BorderLayout.WEST);

        lblJamSekarang = new JLabel(
            LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        lblJamSekarang.setFont(new Font("Consolas", Font.BOLD, 22));
        lblJamSekarang.setForeground(UITheme.PRIMARY);
        p.add(lblJamSekarang, BorderLayout.EAST);
        return p;
    }

    private JPanel buildMainArea() {
        JPanel p = new JPanel(new GridLayout(1, 2, 18, 0));
        p.setBackground(UITheme.BG_MAIN);
        p.add(buildStatusCard());
        p.add(buildAksiCard());
        return p;
    }

    // ── Kartu status ─────────────────────────────────────────

    private JPanel buildStatusCard() {
        JPanel card = makeCard();
        card.setLayout(new BorderLayout(0, 12));
        card.add(makeCardHeader("Status Absensi Hari Ini"), BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(Color.WHITE);
        body.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));

        lblStatusIcon = new JLabel("", SwingConstants.CENTER);
        lblStatusIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 48));
        lblStatusIcon.setAlignmentX(CENTER_ALIGNMENT);
        body.add(lblStatusIcon);
        body.add(Box.createVerticalStrut(8));

        lblStatusJudul = new JLabel("Memuat...", SwingConstants.CENTER);
        lblStatusJudul.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblStatusJudul.setForeground(UITheme.TEXT_PRIMARY);
        lblStatusJudul.setAlignmentX(CENTER_ALIGNMENT);
        body.add(lblStatusJudul);
        body.add(Box.createVerticalStrut(4));

        lblStatusDetail = new JLabel(" ", SwingConstants.CENTER);
        lblStatusDetail.setFont(UITheme.FONT_SMALL);
        lblStatusDetail.setForeground(UITheme.TEXT_SECONDARY);
        lblStatusDetail.setAlignmentX(CENTER_ALIGNMENT);
        body.add(lblStatusDetail);
        body.add(Box.createVerticalStrut(16));

        JPanel grid = new JPanel(new GridLayout(2, 2, 8, 8));
        grid.setBackground(Color.WHITE);
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
        grid.setAlignmentX(LEFT_ALIGNMENT);

        grid.add(makeInfoLabel("Jam Masuk", UITheme.TEXT_SECONDARY));
        lblJamMasuk = makeInfoLabel("--", UITheme.TEXT_PRIMARY);
        lblJamMasuk.setFont(UITheme.FONT_BOLD);
        grid.add(lblJamMasuk);

        grid.add(makeInfoLabel("Lokasi", UITheme.TEXT_SECONDARY));
        lblLokasiMasuk = makeInfoLabel("--", UITheme.TEXT_PRIMARY);
        grid.add(lblLokasiMasuk);

        body.add(grid);
        body.add(Box.createVerticalGlue());

        btnRefresh = makeButton("Refresh", UITheme.TEXT_SECONDARY);
        btnRefresh.setAlignmentX(CENTER_ALIGNMENT);
        btnRefresh.addActionListener(e -> refreshStatus());
        body.add(Box.createVerticalStrut(10));
        body.add(btnRefresh);

        card.add(body, BorderLayout.CENTER);
        return card;
    }

    // ── Kartu aksi ───────────────────────────────────────────

    private JPanel buildAksiCard() {
        JPanel card = makeCard();
        card.setLayout(new BorderLayout(0, 12));
        card.add(makeCardHeader("Proses Absensi Masuk"), BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(Color.WHITE);
        body.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ── Preview foto ──────────────────────────────────────
        lblFotoPreview = new JLabel("", SwingConstants.CENTER) {
            @Override protected void paintComponent(Graphics g) {
                if (getIcon() == null) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(0xF0, 0xF4, 0xF8));
                    g2.fillRoundRect(0, 0, getWidth()-1, getHeight()-1, 12, 12);
                    g2.setColor(UITheme.BORDER);
                    g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 12, 12);
                } else {
                    super.paintComponent(g);
                }
            }
        };
        lblFotoPreview.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
        lblFotoPreview.setPreferredSize(new Dimension(0, 130));
        lblFotoPreview.setOpaque(false);
        lblFotoPreview.setAlignmentX(CENTER_ALIGNMENT);
        body.add(lblFotoPreview);
        body.add(Box.createVerticalStrut(4));

        lblFotoKet = new JLabel("Foto diambil saat absensi", SwingConstants.CENTER);
        lblFotoKet.setFont(UITheme.FONT_SMALL);
        lblFotoKet.setForeground(UITheme.TEXT_MUTED);
        lblFotoKet.setAlignmentX(CENTER_ALIGNMENT);
        body.add(lblFotoKet);
        body.add(Box.createVerticalStrut(8));

        // ── GPS status bar ────────────────────────────────────
        JPanel gpsBox = new JPanel(new BorderLayout(8, 0));
        gpsBox.setBackground(new Color(0xF0, 0xF4, 0xF8));
        gpsBox.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UITheme.BORDER),
            BorderFactory.createEmptyBorder(7, 10, 7, 10)));
        gpsBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        gpsBox.setAlignmentX(LEFT_ALIGNMENT);

        lblGpsIcon = new JLabel("GPS");
        lblGpsIcon.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lblGpsIcon.setForeground(Color.WHITE);
        lblGpsIcon.setOpaque(true);
        lblGpsIcon.setBackground(UITheme.TEXT_SECONDARY);
        lblGpsIcon.setBorder(BorderFactory.createEmptyBorder(3, 7, 3, 7));

        lblGpsInfo = new JLabel("Belum ada data GPS dari HP");
        lblGpsInfo.setFont(UITheme.FONT_SMALL);
        lblGpsInfo.setForeground(UITheme.TEXT_SECONDARY);

        gpsBox.add(lblGpsIcon, BorderLayout.WEST);
        gpsBox.add(lblGpsInfo, BorderLayout.CENTER);
        body.add(gpsBox);
        body.add(Box.createVerticalStrut(8));

        // ── Info aturan ───────────────────────────────────────
        JPanel infoBox = new JPanel();
        infoBox.setLayout(new BoxLayout(infoBox, BoxLayout.Y_AXIS));
        infoBox.setBackground(new Color(0xF0, 0xF4, 0xF8));
        infoBox.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UITheme.BORDER),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        infoBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        infoBox.setAlignmentX(LEFT_ALIGNMENT);

        String[] infos = {
            "Jam masuk mulai pukul " + AbsensiUtil.getJamMasuk() + " WIB",
            "Kirim GPS dari HP sebelum absensi",
            "Sistem verifikasi wajah aktif",
            "Lewat jam masuk = dicatat terlambat"
        };
        for (String info : infos) {
            JLabel l = new JLabel(info);
            l.setFont(UITheme.FONT_SMALL);
            l.setForeground(UITheme.TEXT_SECONDARY);
            infoBox.add(l);
        }
        body.add(infoBox);
        body.add(Box.createVerticalStrut(10));
        body.add(Box.createVerticalGlue());

        btnAbsenMasuk = makeButton("ABSEN MASUK SEKARANG", UITheme.ACCENT);
        btnAbsenMasuk.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnAbsenMasuk.setPreferredSize(new Dimension(0, 46));
        btnAbsenMasuk.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        btnAbsenMasuk.setAlignmentX(CENTER_ALIGNMENT);
        btnAbsenMasuk.addActionListener(e -> doAbsenMasuk());
        body.add(btnAbsenMasuk);

        card.add(body, BorderLayout.CENTER);
        return card;
    }

    // ── Refresh status ────────────────────────────────────────

    public void refreshStatus() {
        try {
            Absensi a = absensiService.getStatusHariIni(user.getIdKaryawan());

            if (a == null) {
                lblStatusIcon.setText("--");
                lblStatusJudul.setText("Belum Absen Masuk");
                lblStatusJudul.setForeground(UITheme.WARNING);
                lblStatusDetail.setText("Silakan lakukan absensi masuk sekarang");
                lblJamMasuk.setText("--");
                lblLokasiMasuk.setText("--");
                btnAbsenMasuk.setEnabled(true);
                btnAbsenMasuk.setText("ABSEN MASUK SEKARANG");

            } else if (a.sudahAbsenMasuk() && !a.sudahAbsenKeluar()) {
                lblStatusIcon.setText("V");
                lblStatusJudul.setText("Sudah Absen Masuk");
                lblStatusJudul.setForeground(UITheme.SUCCESS);
                String detail = a.isStatusTelat()
                    ? "Terlambat " + a.getDurasiTelatFormatted()
                    : "Tepat waktu";
                lblStatusDetail.setText(detail);
                lblStatusDetail.setForeground(
                    a.isStatusTelat() ? UITheme.WARNING : UITheme.SUCCESS);
                lblJamMasuk.setText(
                    a.getJamMasuk() != null ? a.getJamMasuk().toString() : "--");
                lblLokasiMasuk.setText(
                    a.getLokasiMasuk() != null ? a.getLokasiMasuk() : "--");
                btnAbsenMasuk.setEnabled(false);
                btnAbsenMasuk.setText("Sudah Absen Masuk");
                tampilkanFotoAbsensi(a.getPathFotoMasuk());

            } else if (a.sudahAbsenKeluar()) {
                lblStatusIcon.setText("OK");
                lblStatusJudul.setText("Absensi Hari Ini Lengkap");
                lblStatusJudul.setForeground(UITheme.SUCCESS);
                lblStatusDetail.setText("Masuk: " + a.getJamMasuk() +
                    "  |  Keluar: " + a.getJamKeluar());
                lblJamMasuk.setText(
                    a.getJamMasuk() != null ? a.getJamMasuk().toString() : "--");
                lblLokasiMasuk.setText(
                    a.getLokasiMasuk() != null ? a.getLokasiMasuk() : "--");
                btnAbsenMasuk.setEnabled(false);
                btnAbsenMasuk.setText("Absensi Selesai");
                tampilkanFotoAbsensi(a.getPathFotoMasuk());
            }

        } catch (SQLException e) {
            lblStatusJudul.setText("Gagal memuat status");
            lblStatusDetail.setText(e.getMessage());
        }
    }

    private void tampilkanFotoAbsensi(String pathFoto) {
        if (pathFoto == null || pathFoto.isBlank()) return;
        try {
            File f = new File(pathFoto);
            if (!f.exists()) return;
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(f);
            if (img != null) {
                Image scaled = img.getScaledInstance(-1, 130, Image.SCALE_SMOOTH);
                lblFotoPreview.setIcon(new ImageIcon(scaled));
                lblFotoKet.setText("Foto absensi masuk hari ini");
                lblFotoKet.setForeground(UITheme.TEXT_SECONDARY);
            }
        } catch (Exception ignored) {}
    }

    // ── Update GPS status bar ─────────────────────────────────

    private void updateGpsStatus() {
        if (lblGpsInfo == null || lblGpsIcon == null) return;

        if (LocationServer.hasData() && LocationServer.isFresh()) {
            double jarak = LocationUtil.hitungJarak(
                LocationServer.getLatitude(), LocationServer.getLongitude(),
                LocationUtil.getKantorLatitude(), LocationUtil.getKantorLongitude());

            boolean diKantor = jarak <= LocationUtil.getRadiusMeter();

            lblGpsIcon.setBackground(diKantor
                ? new Color(0x05, 0x96, 0x62)
                : new Color(0xDC, 0x35, 0x45));
            lblGpsIcon.setText("GPS");

            lblGpsInfo.setText(String.format(
                "%.4f, %.4f  |  Jarak: %.0f m  |  %s",
                LocationServer.getLatitude(),
                LocationServer.getLongitude(),
                jarak,
                diKantor ? "Dalam area kantor" : "Di luar area kantor"));
            lblGpsInfo.setForeground(diKantor ? UITheme.SUCCESS : UITheme.DANGER);

        } else if (LocationServer.hasData()) {
            // Data ada tapi sudah lebih dari 5 menit
            lblGpsIcon.setBackground(UITheme.WARNING);
            lblGpsInfo.setText("GPS kadaluarsa (" +
                LocationServer.getMenitSejak() + " mnt lalu). Kirim ulang dari HP.");
            lblGpsInfo.setForeground(UITheme.WARNING);

        } else {
            // Belum ada data sama sekali
            lblGpsIcon.setBackground(UITheme.TEXT_SECONDARY);
            lblGpsInfo.setText("Buka  " + LocationServer.getGpsUrl() +
                "  di browser HP");
            lblGpsInfo.setForeground(UITheme.TEXT_MUTED);
        }
    }

    // ── Aksi absen masuk ─────────────────────────────────────

    private void doAbsenMasuk() {
        Window parentWindow = SwingUtilities.getWindowAncestor(this);
        Frame  parentFrame  = parentWindow instanceof Frame
            ? (Frame) parentWindow : null;

        // 1. Cek GPS dari HP sudah dikirim dan masih fresh
        if (!LocationServer.hasData() || !LocationServer.isFresh()) {
            tampilkanDialogGPS(parentFrame);
            return;
        }

        double lat   = LocationServer.getLatitude();
        double lon   = LocationServer.getLongitude();
        double jarak = LocationUtil.hitungJarak(lat, lon,
            LocationUtil.getKantorLatitude(), LocationUtil.getKantorLongitude());

        // 2. Validasi geofencing dengan GPS HP
        if (!LocationUtil.isInKantor(lat, lon)) {
            JOptionPane.showMessageDialog(this,
                String.format(
                    "Anda berada di luar area kantor.\n\n" +
                    "Lokasi GPS Anda  : %.6f, %.6f\n" +
                    "Jarak ke kantor  : %.0f meter\n" +
                    "Radius diizinkan : %.0f meter\n\n" +
                    "Absensi hanya dapat dilakukan\n" +
                    "dalam radius kantor.",
                    lat, lon, jarak, LocationUtil.getRadiusMeter()),
                "Di Luar Area Kantor",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 3. Ambil foto webcam
        WebcamCaptureDialog camDialog = new WebcamCaptureDialog(
            parentFrame, "Foto Absensi Masuk", false);
        camDialog.setVisible(true);

        if (!camDialog.isConfirmed()
                || camDialog.getCapturedImageBytes() == null) return;

        byte[] fotoBytes = camDialog.getCapturedImageBytes();

        // 4. Proses absensi — lokasi sudah valid dari GPS HP
        btnAbsenMasuk.setEnabled(false);
        btnAbsenMasuk.setText("Memproses...");

        HasilAbsensi hasil = absensiService.absenMasuk(
            user, fotoBytes, lat, lon, false);

        btnAbsenMasuk.setEnabled(!hasil.isSukses());
        btnAbsenMasuk.setText(hasil.isSukses()
            ? "Sudah Absen Masuk"
            : "ABSEN MASUK SEKARANG");

        if (hasil.isSukses()) {
            LocationServer.reset(); // reset GPS setelah absensi berhasil
            refreshStatus();
            JOptionPane.showMessageDialog(this,
                hasil.getRingkasan(),
                "Absensi Masuk Berhasil",
                JOptionPane.INFORMATION_MESSAGE);
            if (onAbsenBerhasil != null) onAbsenBerhasil.run();
        } else {
            JOptionPane.showMessageDialog(this,
                hasil.getPesan(),
                "Absensi Ditolak",
                JOptionPane.WARNING_MESSAGE);
        }
    }

    // ── Dialog panduan GPS ────────────────────────────────────

    private void tampilkanDialogGPS(Frame parent) {
        String url = LocationServer.getGpsUrl();

        JDialog dialog = new JDialog(parent, "GPS Diperlukan", true);
        dialog.setLayout(new BorderLayout(0, 0));
        dialog.setSize(480, 360);
        dialog.setLocationRelativeTo(parent);
        dialog.setResizable(false);

        // Header
        JLabel header = new JLabel("  Kirim Lokasi GPS dari HP");
        header.setFont(new Font("Segoe UI", Font.BOLD, 15));
        header.setForeground(Color.WHITE);
        header.setOpaque(true);
        header.setBackground(new Color(0x1B, 0x2A, 0x4A));
        header.setPreferredSize(new Dimension(0, 44));
        dialog.add(header, BorderLayout.NORTH);

        // Body
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(Color.WHITE);
        body.setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));

        JLabel lblInfo = new JLabel(
            "<html>Sistem membutuhkan lokasi GPS akurat dari HP Anda.<br><br>" +
            "<b>Buka URL berikut di browser HP (Chrome/Safari):</b></html>");
        lblInfo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblInfo.setAlignmentX(LEFT_ALIGNMENT);
        body.add(lblInfo);
        body.add(Box.createVerticalStrut(10));

        // URL box
        JPanel urlBox = new JPanel(new BorderLayout(8, 0));
        urlBox.setBackground(new Color(0xF0, 0xF4, 0xF8));
        urlBox.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0xCB, 0xD5, 0xE1)),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        urlBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        urlBox.setAlignmentX(LEFT_ALIGNMENT);

        JTextField tfUrl = new JTextField(url);
        tfUrl.setEditable(false);
        tfUrl.setFont(new Font("Consolas", Font.BOLD, 12));
        tfUrl.setBackground(new Color(0xF0, 0xF4, 0xF8));
        tfUrl.setBorder(null);
        tfUrl.setForeground(new Color(0x1B, 0x2A, 0x4A));
        urlBox.add(tfUrl, BorderLayout.CENTER);

        JButton btnCopy = new JButton("Salin");
        btnCopy.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnCopy.setBackground(new Color(0x1B, 0x2A, 0x4A));
        btnCopy.setForeground(Color.WHITE);
        btnCopy.setBorderPainted(false);
        btnCopy.setFocusPainted(false);
        btnCopy.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnCopy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(url), null);
            btnCopy.setText("Disalin!");
            new Timer(2000, ev -> {
                btnCopy.setText("Salin");
                ((Timer) ev.getSource()).stop();
            }).start();
        });
        urlBox.add(btnCopy, BorderLayout.EAST);
        body.add(urlBox);
        body.add(Box.createVerticalStrut(12));

        JLabel lblSteps = new JLabel(
            "<html><b>Langkah:</b><br>" +
            "1. Pastikan HP terhubung WiFi yang <b>sama</b> dengan PC ini<br>" +
            "2. Buka URL di atas di browser HP (Chrome/Safari)<br>" +
            "3. Tekan <b>Ambil Lokasi GPS</b> — izinkan akses lokasi<br>" +
            "4. Tekan <b>Kirim ke Aplikasi Absensi</b><br>" +
            "5. Kembali ke PC ini dan tekan <b>Coba Lagi</b></html>");
        lblSteps.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSteps.setForeground(new Color(0x47, 0x55, 0x69));
        lblSteps.setAlignmentX(LEFT_ALIGNMENT);
        body.add(lblSteps);
        dialog.add(body, BorderLayout.CENTER);

        // Tombol
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 10));
        btnPanel.setBackground(new Color(0xF8, 0xFA, 0xFC));
        btnPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
            new Color(0xE2, 0xE8, 0xF0)));

        JButton btnBatal = new JButton("Batal");
        btnBatal.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btnBatal.setFocusPainted(false);
        btnBatal.addActionListener(e -> dialog.dispose());

        JButton btnRetry = new JButton("Coba Lagi");
        btnRetry.setBackground(new Color(0x00, 0xC9, 0xA7));
        btnRetry.setForeground(Color.WHITE);
        btnRetry.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnRetry.setBorderPainted(false);
        btnRetry.setFocusPainted(false);
        btnRetry.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnRetry.addActionListener(e -> {
            dialog.dispose();
            if (LocationServer.hasData() && LocationServer.isFresh()) {
                // GPS sudah diterima — langsung proses
                doAbsenMasuk();
            } else {
                JOptionPane.showMessageDialog(null,
                    "GPS belum diterima dari HP.\n\n" +
                    "Pastikan sudah menekan 'Kirim ke Aplikasi Absensi'\n" +
                    "di halaman GPS di HP Anda.",
                    "GPS Belum Diterima",
                    JOptionPane.WARNING_MESSAGE);
            }
        });

        btnPanel.add(btnBatal);
        btnPanel.add(btnRetry);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    // ── Timer ─────────────────────────────────────────────────

    private void startJamTimer() {
        jamTimer = new Timer(1000, e -> {
            if (lblJamSekarang != null)
                lblJamSekarang.setText(
                    LocalTime.now().format(
                        DateTimeFormatter.ofPattern("HH:mm:ss")));
            // Update GPS status bar setiap detik
            updateGpsStatus();
        });
        jamTimer.start();
    }

    public void stopTimer() {
        if (jamTimer != null) jamTimer.stop();
    }

    // ── UI helpers ────────────────────────────────────────────

    private JPanel makeCard() {
        JPanel c = new JPanel(new BorderLayout());
        c.setBackground(Color.WHITE);
        c.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UITheme.BORDER),
            BorderFactory.createEmptyBorder(0, 0, 14, 0)));
        return c;
    }

    private JLabel makeCardHeader(String text) {
        JLabel l = new JLabel("  " + text);
        l.setFont(UITheme.FONT_BOLD);
        l.setForeground(Color.WHITE);
        l.setOpaque(true);
        l.setBackground(UITheme.PRIMARY);
        l.setPreferredSize(new Dimension(0, 36));
        return l;
    }

    private JLabel makeInfoLabel(String text, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(UITheme.FONT_BODY);
        l.setForeground(color);
        return l;
    }

    private JButton makeButton(String text, Color color) {
        JButton b = new JButton(text);
        b.setBackground(color);
        b.setForeground(Color.WHITE);
        b.setFont(UITheme.FONT_BOLD);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return b;
    }
}