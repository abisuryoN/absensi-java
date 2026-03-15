package com.absensi.ui.employee;

import com.absensi.model.Absensi;
import com.absensi.model.Karyawan;
import com.absensi.service.AbsensiService;
import com.absensi.service.AbsensiService.HasilAbsensi;
import com.absensi.ui.components.WebcamCaptureDialog;
import com.absensi.util.AbsensiUtil;
import com.absensi.util.CurrencyUtil;
import com.absensi.util.ImageUtil;
import com.absensi.util.LocationServer;
import com.absensi.util.LocationUtil;
import com.absensi.util.UITheme;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class AbsensiKeluarPanel extends JPanel {

    private final Karyawan       user;
    private final AbsensiService absensiService = new AbsensiService();

    private JLabel   lblStatusIcon;
    private JLabel   lblStatusJudul;
    private JLabel   lblStatusDetail;
    private JLabel   lblJamMasukInfo;
    private JLabel   lblPrakiraan;
    private JLabel   lblJamSekarang;
    private JLabel   lblLemburPreview;
    private JLabel   lblFotoPreview;
    private JLabel   lblFotoKet;
    private JLabel   lblGpsInfo;
    private JLabel   lblGpsIcon;
    private JButton  btnAbsenKeluar;
    private Timer    jamTimer;
    private Runnable onAbsenBerhasil;

    public AbsensiKeluarPanel(Karyawan user) {
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

        JLabel lblTitle = new JLabel("Absensi Keluar");
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
        card.setLayout(new BorderLayout(0, 10));
        card.add(makeCardHeader("Ringkasan Hari Ini"), BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(Color.WHITE);
        body.setBorder(BorderFactory.createEmptyBorder(12, 15, 12, 15));

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
        body.add(Box.createVerticalStrut(20));

        JPanel infoGrid = new JPanel(new GridLayout(3, 2, 8, 8));
        infoGrid.setBackground(Color.WHITE);
        infoGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        infoGrid.setAlignmentX(LEFT_ALIGNMENT);

        infoGrid.add(makeLabel("Jam Masuk", UITheme.TEXT_SECONDARY));
        lblJamMasukInfo = makeLabel("--", UITheme.TEXT_PRIMARY);
        lblJamMasukInfo.setFont(UITheme.FONT_BOLD);
        infoGrid.add(lblJamMasukInfo);

        infoGrid.add(makeLabel("Jam Pulang Normal", UITheme.TEXT_SECONDARY));
        lblPrakiraan = makeLabel(AbsensiUtil.getJamPulang().toString(), UITheme.TEXT_PRIMARY);
        infoGrid.add(lblPrakiraan);

        infoGrid.add(makeLabel("Estimasi Lembur", UITheme.TEXT_SECONDARY));
        lblLemburPreview = makeLabel("--", UITheme.INFO);
        lblLemburPreview.setFont(UITheme.FONT_BOLD);
        infoGrid.add(lblLemburPreview);

        body.add(infoGrid);
        body.add(Box.createVerticalGlue());

        JButton btnRefresh = makeButton("Refresh", UITheme.TEXT_SECONDARY);
        btnRefresh.setAlignmentX(CENTER_ALIGNMENT);
        btnRefresh.addActionListener(e -> refreshStatus());
        body.add(Box.createVerticalStrut(12));
        body.add(btnRefresh);

        card.add(body, BorderLayout.CENTER);
        return card;
    }

    // ── Kartu aksi ───────────────────────────────────────────

    private JPanel buildAksiCard() {
        JPanel card = makeCard();
        card.setLayout(new BorderLayout(0, 10));
        card.add(makeCardHeader("Proses Absensi Keluar"), BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(Color.WHITE);
        body.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Preview foto
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
        lblFotoPreview.setPreferredSize(new Dimension(0, 130));
        lblFotoPreview.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
        lblFotoPreview.setOpaque(false);
        lblFotoPreview.setAlignmentX(CENTER_ALIGNMENT);
        body.add(lblFotoPreview);
        body.add(Box.createVerticalStrut(4));

        lblFotoKet = new JLabel("Foto diambil saat absensi keluar", SwingConstants.CENTER);
        lblFotoKet.setFont(UITheme.FONT_SMALL);
        lblFotoKet.setForeground(UITheme.TEXT_MUTED);
        lblFotoKet.setAlignmentX(CENTER_ALIGNMENT);
        body.add(lblFotoKet);
        body.add(Box.createVerticalStrut(8));

        // GPS status bar
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

        // Info lembur
        JPanel infoBox = new JPanel();
        infoBox.setLayout(new BoxLayout(infoBox, BoxLayout.Y_AXIS));
        infoBox.setBackground(new Color(0xF0, 0xF4, 0xF8));
        infoBox.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UITheme.BORDER),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        infoBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        infoBox.setAlignmentX(LEFT_ALIGNMENT);

        String[] infos = {
            "Jam pulang normal: " + AbsensiUtil.getJamPulang() + " WIB",
            "Pulang setelah jam tersebut = lembur",
            "Tarif lembur: " +
                CurrencyUtil.formatRupiahBulat(AbsensiUtil.getTarifLembur()) + " / jam"
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

        btnAbsenKeluar = makeButton("ABSEN KELUAR SEKARANG", UITheme.PRIMARY);
        btnAbsenKeluar.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnAbsenKeluar.setPreferredSize(new Dimension(0, 46));
        btnAbsenKeluar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        btnAbsenKeluar.setAlignmentX(CENTER_ALIGNMENT);
        btnAbsenKeluar.addActionListener(e -> doAbsenKeluar());
        body.add(btnAbsenKeluar);

        card.add(body, BorderLayout.CENTER);
        return card;
    }

    // ── Refresh status ────────────────────────────────────────

    public void refreshStatus() {
        try {
            Absensi a = absensiService.getStatusHariIni(user.getIdKaryawan());

            if (a == null) {
                lblStatusIcon.setText("!");
                lblStatusJudul.setText("Belum Absen Masuk");
                lblStatusJudul.setForeground(UITheme.DANGER);
                lblStatusDetail.setText("Anda harus absen masuk terlebih dahulu");
                lblJamMasukInfo.setText("--");
                lblLemburPreview.setText("--");
                btnAbsenKeluar.setEnabled(false);
                btnAbsenKeluar.setText("Belum Absen Masuk");

            } else if (!a.sudahAbsenKeluar()) {
                lblStatusIcon.setText("OK");
                lblStatusJudul.setText("Sedang Bekerja");
                lblStatusJudul.setForeground(UITheme.SUCCESS);
                lblStatusDetail.setText("Silakan absen keluar saat selesai bekerja");
                lblJamMasukInfo.setText(
                    a.getJamMasuk() != null ? a.getJamMasuk().toString() : "--");
                btnAbsenKeluar.setEnabled(true);
                btnAbsenKeluar.setText("ABSEN KELUAR SEKARANG");
                updateLemburPreview();

            } else {
                lblStatusIcon.setText("~");
                lblStatusJudul.setText("Sudah Pulang");
                lblStatusJudul.setForeground(UITheme.TEXT_SECONDARY);
                lblStatusDetail.setText("Keluar: " + a.getJamKeluar() +
                    (a.getDurasiLembur() > 0
                        ? "  |  Lembur: " + a.getDurasiLemburFormatted()
                        : ""));
                lblJamMasukInfo.setText(
                    a.getJamMasuk() != null ? a.getJamMasuk().toString() : "--");
                lblLemburPreview.setText(a.getDurasiLembur() > 0
                    ? a.getDurasiLemburFormatted() + "  (" +
                      CurrencyUtil.formatRupiahBulat(a.getUangLembur()) + ")"
                    : "Tidak ada lembur");
                btnAbsenKeluar.setEnabled(false);
                btnAbsenKeluar.setText("Sudah Absen Keluar");

                if (a.getPathFotoKeluar() != null && !a.getPathFotoKeluar().isBlank()) {
                    ImageIcon icon = ImageUtil.loadSebagaiIcon(
                        a.getPathFotoKeluar(), 130, 130);
                    if (icon != null) {
                        lblFotoPreview.setIcon(icon);
                        lblFotoKet.setText("Foto absensi keluar hari ini");
                        lblFotoKet.setForeground(UITheme.TEXT_SECONDARY);
                    }
                }
            }

        } catch (SQLException e) {
            lblStatusJudul.setText("Gagal memuat status");
            lblStatusDetail.setText(e.getMessage());
        }
    }

    private void updateLemburPreview() {
        int menit = AbsensiUtil.hitungLembur(LocalTime.now());
        if (menit > 0) {
            lblLemburPreview.setText(AbsensiUtil.formatDurasi(menit) + "  (+  " +
                CurrencyUtil.formatRupiahBulat(
                    AbsensiUtil.hitungUangLembur(menit)) + ")");
            lblLemburPreview.setForeground(UITheme.INFO);
        } else {
            int sisaMenit = (int) java.time.temporal.ChronoUnit.MINUTES.between(
                LocalTime.now(), AbsensiUtil.getJamPulang());
            lblLemburPreview.setText(sisaMenit > 0
                ? "Belum lembur (" + sisaMenit + " mnt lagi)"
                : "Mulai sekarang");
            lblLemburPreview.setForeground(UITheme.TEXT_SECONDARY);
        }
    }

    private void updateGpsStatus() {
        if (lblGpsInfo == null || lblGpsIcon == null) return;

        if (LocationServer.hasData() && LocationServer.isFresh()) {
            double jarak = LocationUtil.hitungJarak(
                LocationServer.getLatitude(), LocationServer.getLongitude(),
                LocationUtil.getKantorLatitude(), LocationUtil.getKantorLongitude());
            boolean diKantor = jarak <= LocationUtil.getRadiusMeter();

            lblGpsIcon.setBackground(diKantor
                ? new Color(0x05, 0x96, 0x62) : new Color(0xDC, 0x35, 0x45));
            lblGpsInfo.setText(String.format(
                "%.4f, %.4f  |  Jarak: %.0f m  |  %s",
                LocationServer.getLatitude(), LocationServer.getLongitude(),
                jarak, diKantor ? "Dalam area kantor" : "Di luar area kantor"));
            lblGpsInfo.setForeground(diKantor ? UITheme.SUCCESS : UITheme.DANGER);

        } else if (LocationServer.hasData()) {
            lblGpsIcon.setBackground(UITheme.WARNING);
            lblGpsInfo.setText("GPS kadaluarsa (" +
                LocationServer.getMenitSejak() + " mnt). Kirim ulang dari HP.");
            lblGpsInfo.setForeground(UITheme.WARNING);
        } else {
            lblGpsIcon.setBackground(UITheme.TEXT_SECONDARY);
            lblGpsInfo.setText("Buka  " + LocationServer.getGpsUrl() +
                "  di browser HP");
            lblGpsInfo.setForeground(UITheme.TEXT_MUTED);
        }
    }

    // ── Aksi absen keluar ─────────────────────────────────────

    private void doAbsenKeluar() {
        Frame parentFrame = (Frame) SwingUtilities.getWindowAncestor(this);

        // 1. Cek GPS dari HP
        if (!LocationServer.hasData() || !LocationServer.isFresh()) {
            tampilkanDialogGPS(parentFrame);
            return;
        }

        double lat   = LocationServer.getLatitude();
        double lon   = LocationServer.getLongitude();
        double jarak = LocationUtil.hitungJarak(lat, lon,
            LocationUtil.getKantorLatitude(), LocationUtil.getKantorLongitude());

        // 2. Validasi geofencing
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

        // 3. Ambil foto
        WebcamCaptureDialog cam = new WebcamCaptureDialog(
            parentFrame, "Foto Absensi Keluar", false);
        cam.setVisible(true);
        if (!cam.isConfirmed() || cam.getCapturedImageBytes() == null) return;
        byte[] fotoBytes = cam.getCapturedImageBytes();

        // 4. Proses ke service
        btnAbsenKeluar.setEnabled(false);
        btnAbsenKeluar.setText("Memproses...");

        HasilAbsensi hasil = absensiService.absenKeluar(
            user, fotoBytes, lat, lon, false);

        if (hasil.isSukses()) {
            LocationServer.reset();

            try {
                Absensi a = absensiService.getStatusHariIni(user.getIdKaryawan());
                if (a != null && a.getPathFotoKeluar() != null) {
                    ImageIcon icon = ImageUtil.loadSebagaiIcon(
                        a.getPathFotoKeluar(), 130, 130);
                    if (icon != null) {
                        lblFotoPreview.setIcon(icon);
                        lblFotoKet.setText("Foto absensi keluar tersimpan");
                        lblFotoKet.setForeground(UITheme.SUCCESS);
                    }
                }
            } catch (SQLException ignored) {}

            JOptionPane.showMessageDialog(this,
                hasil.getRingkasan(),
                "Absensi Keluar Berhasil",
                JOptionPane.INFORMATION_MESSAGE);
            refreshStatus();
            if (onAbsenBerhasil != null) onAbsenBerhasil.run();

        } else {
            JOptionPane.showMessageDialog(this,
                hasil.getPesan(),
                "Absensi Ditolak",
                JOptionPane.WARNING_MESSAGE);
            btnAbsenKeluar.setEnabled(true);
            btnAbsenKeluar.setText("ABSEN KELUAR SEKARANG");
        }
    }

    // ── Dialog GPS (sama seperti AbsensiMasukPanel) ───────────

    private void tampilkanDialogGPS(Frame parent) {
        String url = LocationServer.getGpsUrl();

        JDialog dialog = new JDialog(parent, "GPS Diperlukan", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(480, 340);
        dialog.setLocationRelativeTo(parent);
        dialog.setResizable(false);

        JLabel header = new JLabel("  Kirim Lokasi GPS dari HP");
        header.setFont(new Font("Segoe UI", Font.BOLD, 15));
        header.setForeground(Color.WHITE);
        header.setOpaque(true);
        header.setBackground(new Color(0x1B, 0x2A, 0x4A));
        header.setPreferredSize(new Dimension(0, 44));
        dialog.add(header, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(Color.WHITE);
        body.setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));

        JLabel lblInfo = new JLabel(
            "<html>Sistem membutuhkan lokasi GPS akurat dari HP Anda.<br><br>" +
            "<b>Buka URL berikut di browser HP:</b></html>");
        lblInfo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblInfo.setAlignmentX(LEFT_ALIGNMENT);
        body.add(lblInfo);
        body.add(Box.createVerticalStrut(10));

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
        urlBox.add(tfUrl, BorderLayout.CENTER);

        JButton btnCopy = new JButton("Salin");
        btnCopy.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnCopy.setBackground(new Color(0x1B, 0x2A, 0x4A));
        btnCopy.setForeground(Color.WHITE);
        btnCopy.setBorderPainted(false);
        btnCopy.setFocusPainted(false);
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
            "1. HP dan PC harus terhubung WiFi yang sama<br>" +
            "2. Buka URL di atas di browser HP<br>" +
            "3. Tekan <b>Ambil Lokasi GPS</b><br>" +
            "4. Tekan <b>Kirim ke Aplikasi Absensi</b><br>" +
            "5. Kembali ke PC dan tekan <b>Coba Lagi</b></html>");
        lblSteps.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSteps.setForeground(new Color(0x47, 0x55, 0x69));
        lblSteps.setAlignmentX(LEFT_ALIGNMENT);
        body.add(lblSteps);
        dialog.add(body, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 10));
        btnPanel.setBackground(new Color(0xF8, 0xFA, 0xFC));
        btnPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
            new Color(0xE2, 0xE8, 0xF0)));

        JButton btnBatal = new JButton("Batal");
        btnBatal.setFocusPainted(false);
        btnBatal.addActionListener(e -> dialog.dispose());

        JButton btnRetry = new JButton("Coba Lagi");
        btnRetry.setBackground(new Color(0x00, 0xC9, 0xA7));
        btnRetry.setForeground(Color.WHITE);
        btnRetry.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnRetry.setBorderPainted(false);
        btnRetry.setFocusPainted(false);
        btnRetry.addActionListener(e -> {
            dialog.dispose();
            if (LocationServer.hasData() && LocationServer.isFresh()) {
                doAbsenKeluar();
            } else {
                JOptionPane.showMessageDialog(null,
                    "GPS belum diterima.\nPastikan sudah menekan 'Kirim' di HP.",
                    "GPS Belum Diterima", JOptionPane.WARNING_MESSAGE);
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
            if (btnAbsenKeluar != null && btnAbsenKeluar.isEnabled())
                updateLemburPreview();
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

    private JLabel makeLabel(String text, Color fg) {
        JLabel l = new JLabel(text);
        l.setFont(UITheme.FONT_BODY);
        l.setForeground(fg);
        return l;
    }

    private JButton makeButton(String text, Color bg) {
        JButton b = new JButton(text);
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setFont(UITheme.FONT_BOLD);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return b;
    }
}