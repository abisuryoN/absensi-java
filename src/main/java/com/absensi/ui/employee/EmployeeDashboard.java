package com.absensi.ui.employee;

import com.absensi.dao.AbsensiDAO;
import com.absensi.dao.GajiDAO;
import com.absensi.model.Absensi;
import com.absensi.model.Gaji;
import com.absensi.model.Karyawan;
import com.absensi.service.AbsensiService;
import com.absensi.ui.auth.LoginFrame;
import com.absensi.ui.components.SidebarComponent;
import com.absensi.ui.components.WebcamCaptureDialog;
import com.absensi.util.*;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.List;

public class EmployeeDashboard extends JFrame {

    protected final Karyawan      currentUser;
    private final AbsensiService  absensiService = new AbsensiService();
    private final AbsensiDAO      absensiDAO     = new AbsensiDAO();
    private final GajiDAO         gajiDAO        = new GajiDAO();

    protected JPanel     contentPanel;
    protected CardLayout contentLayout;
    private   JLabel     lblCurrentTime;

    // Dipakai oleh refreshStatusAbsensi() — masih dipertahankan
    // untuk backward compat dengan HRDDashboard yang mungkin override
    protected JLabel   lblStatusAbsen;
    protected JButton  btnAbsenMasuk;
    protected JButton  btnAbsenKeluar;

    private Timer clockTimer;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Untuk riwayat & gaji (panel lama — masih dipakai jika belum pakai panel baru)
    private DefaultTableModel riwayatModel;
    private DefaultTableModel gajiModel;
    private JComboBox<Integer> cbBulan, cbTahun;

    public EmployeeDashboard(Karyawan user) {
        this.currentUser = user;
        initUI();
        startClock();
    }

    protected void initUI() {
        setTitle("Dashboard Karyawan - " + currentUser.getNama());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1150, 720);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        add(buildSidebar(),  BorderLayout.WEST);
        add(buildHeader(),   BorderLayout.NORTH);
        add(buildContent(),  BorderLayout.CENTER);
    }

    // =========================================================
    //  SIDEBAR — pakai SidebarComponent baru
    // =========================================================

    protected SidebarComponent buildSidebar() {
        SidebarComponent sidebar = new SidebarComponent(currentUser);

        // Ikon: teks biasa ASCII/Unicode pendek agar render di semua OS.
        // Emoji multi-codepoint sering muncul sebagai □ di Windows via Graphics2D.
        // Solusi: pakai karakter tunggal atau teks singkat.
        // Ikon: Unicode Geometric Shapes — render stabil di Windows Segoe UI Symbol
        // ⌂ = rumah, ↓ = panah bawah masuk, ↑ = panah atas keluar
        // ▦ = kotak dengan tanda, ▶ = segitiga kanan, ⌂ = home
        sidebar.tambahMenu("\u2302", "Beranda",         "beranda");
        sidebar.tambahMenu("\u2193", "Absensi Masuk",   "absen_masuk");
        sidebar.tambahMenu("\u2191", "Absensi Keluar",  "absen_keluar");
        sidebar.tambahMenu("\u25A6", "Riwayat Absensi", "riwayat");
        sidebar.tambahMenu("\u25B6", "Gaji & Slip",     "gaji");
        sidebar.tambahSpacer();
        sidebar.tambahSeparator();
        sidebar.tambahMenu("\u2716", "Keluar",          "logout");

        sidebar.setMenuClickListener((key, idx) -> handleMenu(key));
        return sidebar;
    }

    protected void handleMenu(String key) {
        switch (key) {
            case "beranda":
                contentLayout.show(contentPanel, "beranda");
                break;
            case "absen_masuk":
                contentLayout.show(contentPanel, "absen_masuk");
                break;
            case "absen_keluar":
                contentLayout.show(contentPanel, "absen_keluar");
                break;
            case "riwayat":
                contentLayout.show(contentPanel, "riwayat");
                break;
            case "gaji":
                contentLayout.show(contentPanel, "gaji");
                break;
            case "logout":
                doLogout();
                break;
        }
    }

    // =========================================================
    //  HEADER
    // =========================================================

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UITheme.BG_CARD);
        header.setPreferredSize(new Dimension(0, UITheme.HEADER_HEIGHT));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.BORDER));

        JLabel lblTitle = new JLabel("  Sistem Absensi Karyawan");
        lblTitle.setFont(UITheme.FONT_HEADER);
        lblTitle.setForeground(UITheme.PRIMARY);
        header.add(lblTitle, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 12));
        right.setBackground(UITheme.BG_CARD);

        lblCurrentTime = new JLabel(LocalTime.now().format(TIME_FORMAT));
        lblCurrentTime.setFont(new Font("Consolas", Font.BOLD, 20));
        lblCurrentTime.setForeground(UITheme.PRIMARY);

        JLabel lblUser = new JLabel(currentUser.getNama() + "  |  " + currentUser.getBagian());
        lblUser.setFont(UITheme.FONT_BODY);
        lblUser.setForeground(UITheme.TEXT_SECONDARY);

        right.add(lblCurrentTime);
        right.add(new JSeparator(SwingConstants.VERTICAL));
        right.add(lblUser);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    // =========================================================
    //  CONTENT PANEL
    // =========================================================

    private JPanel buildContent() {
        contentLayout = new CardLayout();
        contentPanel  = new JPanel(contentLayout);
        contentPanel.setBackground(UITheme.BG_MAIN);

        // Beranda: dua panel absensi side-by-side
        JPanel beranda = new JPanel(new GridLayout(1, 2, 15, 0));
        beranda.setBackground(UITheme.BG_MAIN);
        beranda.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        AbsensiMasukPanel  masuk  = new AbsensiMasukPanel(currentUser);
        AbsensiKeluarPanel keluar = new AbsensiKeluarPanel(currentUser);
        masuk.setOnAbsenBerhasil(keluar::refreshStatus);
        keluar.setOnAbsenBerhasil(masuk::refreshStatus);
        beranda.add(masuk);
        beranda.add(keluar);

        contentPanel.add(beranda,                            "beranda");
        contentPanel.add(new AbsensiMasukPanel(currentUser), "absen_masuk");
        contentPanel.add(new AbsensiKeluarPanel(currentUser),"absen_keluar");
        contentPanel.add(new RiwayatAbsensiPanel(currentUser),"riwayat");
        contentPanel.add(new RiwayatGajiPanel(currentUser),  "gaji");

        contentLayout.show(contentPanel, "beranda");
        return contentPanel;
    }

    // =========================================================
    //  REFRESH STATUS (masih dipakai beberapa panel lama)
    // =========================================================

    protected void refreshStatusAbsensi() {
        // Hanya berlaku jika lblStatusAbsen tidak null (panel lama)
        if (lblStatusAbsen == null) return;
        try {
            Absensi a = absensiService.getStatusHariIni(currentUser.getIdKaryawan());
            if (a == null) {
                lblStatusAbsen.setText("Anda belum melakukan absensi masuk hari ini.");
                if (btnAbsenMasuk  != null) btnAbsenMasuk.setEnabled(true);
                if (btnAbsenKeluar != null) btnAbsenKeluar.setEnabled(false);
            } else if (!a.sudahAbsenKeluar()) {
                String info = "Masuk: " + a.getJamMasuk();
                if (a.isStatusTelat()) info += "  |  Terlambat " + a.getDurasiTelatFormatted();
                lblStatusAbsen.setText(info);
                if (btnAbsenMasuk  != null) btnAbsenMasuk.setEnabled(false);
                if (btnAbsenKeluar != null) btnAbsenKeluar.setEnabled(true);
            } else {
                lblStatusAbsen.setText("Masuk: " + a.getJamMasuk() + "  |  Keluar: " + a.getJamKeluar());
                if (btnAbsenMasuk  != null) btnAbsenMasuk.setEnabled(false);
                if (btnAbsenKeluar != null) btnAbsenKeluar.setEnabled(false);
            }
        } catch (SQLException e) {
            if (lblStatusAbsen != null) lblStatusAbsen.setText("Error: " + e.getMessage());
        }
    }

    // =========================================================
    //  UTILITY
    // =========================================================

    protected void styleTable(JTable table) {
        table.setFont(UITheme.FONT_BODY);
        table.setRowHeight(35);
        table.setGridColor(UITheme.BORDER);
        table.setSelectionBackground(UITheme.TABLE_SELECT);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));

        JTableHeader h = table.getTableHeader();
        h.setBackground(UITheme.TABLE_HEADER);
        h.setForeground(Color.WHITE);
        h.setFont(UITheme.FONT_BOLD);
        h.setPreferredSize(new Dimension(0, 40));
        h.setReorderingAllowed(false);

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v,
                    boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, v, sel, foc, row, col);
                setBackground(sel ? UITheme.TABLE_SELECT
                    : (row % 2 == 0 ? UITheme.TABLE_ROW_ODD : UITheme.TABLE_ROW_EVEN));
                setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                return this;
            }
        });
    }

    private void startClock() {
        clockTimer = new Timer(1000, e -> {
            if (lblCurrentTime != null)
                lblCurrentTime.setText(LocalTime.now().format(TIME_FORMAT));
        });
        clockTimer.start();
    }

    protected void doLogout() {
        int c = JOptionPane.showConfirmDialog(this,
            "Apakah Anda yakin ingin keluar?", "Konfirmasi Logout", JOptionPane.YES_NO_OPTION);
        if (c == JOptionPane.YES_OPTION) {
            if (clockTimer != null) clockTimer.stop();
            dispose();
            new LoginFrame().setVisible(true);
        }
    }
}
