package com.absensi.ui.hrd;

import com.absensi.dao.*;
import com.absensi.model.*;
import com.absensi.ui.components.SidebarComponent;
import com.absensi.ui.employee.EmployeeDashboard;
import com.absensi.util.*;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.List;

/**
 * Dashboard HRD - extends EmployeeDashboard dengan fitur tambahan.
 * HRD tetap bisa melakukan absensi seperti karyawan biasa,
 * ditambah fitur manajemen karyawan, gaji, dan laporan.
 */
public class HRDDashboard extends EmployeeDashboard {

    private final KaryawanDAO karyawanDAO = new KaryawanDAO();
    private final AbsensiDAO  absensiDAO  = new AbsensiDAO();
    private final GajiDAO     gajiDAO     = new GajiDAO();

    // Tabel models
    private DefaultTableModel karyawanModel;
    private DefaultTableModel absensiSemuaModel;
    private DefaultTableModel laporanTelatModel;
    private DefaultTableModel laporanLemburModel;
    private DefaultTableModel gajiSemuaModel;

    // Filter komponen
    private JComboBox<Integer> cbLapBulan, cbLapTahun;
    private JComboBox<Integer> cbGajiBulan, cbGajiTahun;

    public HRDDashboard(Karyawan user) {
        super(user);
    }

    @Override
    protected void initUI() {
        setTitle("Dashboard HRD - " + currentUser.getNama());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 750);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        add(buildHRDSidebar(), BorderLayout.WEST);
        add(createHRDHeader(), BorderLayout.NORTH);

        CardLayout cl = new CardLayout();
        JPanel content = new JPanel(cl);
        content.setBackground(UITheme.BG_MAIN);

        // Panel Beranda: AbsensiMasuk + AbsensiKeluar untuk HRD sendiri
        JPanel berandaHRD = new JPanel(new GridLayout(1, 2, 15, 0));
        berandaHRD.setBackground(UITheme.BG_MAIN);
        berandaHRD.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        com.absensi.ui.employee.AbsensiMasukPanel  hrdMasuk  = new com.absensi.ui.employee.AbsensiMasukPanel(currentUser);
        com.absensi.ui.employee.AbsensiKeluarPanel hrdKeluar = new com.absensi.ui.employee.AbsensiKeluarPanel(currentUser);
        hrdMasuk.setOnAbsenBerhasil(hrdKeluar::refreshStatus);
        hrdKeluar.setOnAbsenBerhasil(hrdMasuk::refreshStatus);
        berandaHRD.add(hrdMasuk);
        berandaHRD.add(hrdKeluar);

        content.add(berandaHRD,                          "beranda");
        content.add(new DivisiPanel(),                   "divisi");
        content.add(new ManajemenKaryawanPanel(currentUser), "karyawan");
        content.add(new LaporanPanel(currentUser),       "absensi_semua");
        content.add(new LaporanPanel(currentUser),       "laporan");
        content.add(new ManajemenGajiPanel(currentUser), "gaji_management");

        // Simpan referensi untuk navigasi
        add(content, BorderLayout.CENTER);

        // Navigasi via SidebarComponent listener
        hrdSidebarComp.setMenuClickListener((key, idx) -> {
            switch (key) {
                case "beranda":         cl.show(content, "beranda");         break;
                case "divisi":          cl.show(content, "divisi");          break;
                case "karyawan":        cl.show(content, "karyawan");        loadDataKaryawan(); break;
                case "absensi_semua":   cl.show(content, "absensi_semua");   loadAbsensiSemua(); break;
                case "laporan":         cl.show(content, "laporan");         break;
                case "gaji_management": cl.show(content, "gaji_management"); loadGajiSemua(); break;
                case "logout":          doHRDLogout(); break;
            }
        });
    }

    private JPanel createHRDHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(0x2E4272));
        header.setPreferredSize(new Dimension(0, UITheme.HEADER_HEIGHT));
        header.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 15));

        JLabel lblTitle = new JLabel("👑  DASHBOARD HRD - Sistem Absensi Karyawan");
        lblTitle.setFont(UITheme.FONT_HEADER);
        lblTitle.setForeground(Color.WHITE);
        header.add(lblTitle, BorderLayout.WEST);

        JLabel lblUser = new JLabel(currentUser.getNama() + "  |  HRD  ");
        lblUser.setFont(UITheme.FONT_BODY);
        lblUser.setForeground(UITheme.ACCENT);
        header.add(lblUser, BorderLayout.EAST);

        return header;
    }

    // ===================== SIDEBAR HRD =====================

    private SidebarComponent hrdSidebarComp;

    private SidebarComponent buildHRDSidebar() {
    hrdSidebarComp = new SidebarComponent(currentUser);

    hrdSidebarComp.tambahMenu("\u2302", "Beranda & Absensi", "beranda");
    hrdSidebarComp.tambahMenu("\u25A6", "Monitor Divisi",    "divisi");
    hrdSidebarComp.tambahMenu("\u25A6", "Monitor Karyawan",  "karyawan");
    hrdSidebarComp.tambahMenu("\u25A6", "Monitor Absensi",   "absensi_semua");
    hrdSidebarComp.tambahMenu("\u25B6", "Laporan",           "laporan");
    hrdSidebarComp.tambahMenu("\u25B6", "Monitor Gaji",      "gaji_management");
    hrdSidebarComp.tambahSpacer();
    hrdSidebarComp.tambahSeparator();
    hrdSidebarComp.tambahMenu("\u2716", "Keluar",            "logout");

    // listener diset di initUI() setelah content panel dibuat
    return hrdSidebarComp;
}



    // ===================== PANEL BERANDA HRD (+ Absensi) =====================

    private JPanel createBerandaHRD() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(UITheme.BG_MAIN);
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Info HRD di atas
        JPanel infoPanel = new JPanel(new GridLayout(1, 4, 10, 0));
        infoPanel.setBackground(UITheme.BG_MAIN);

        try {
            List<Karyawan> semua = karyawanDAO.findAll();
            int totalKaryawan = semua.size();
            int pending = (int) semua.stream().filter(k -> k.getStatusVerifikasi() == Karyawan.StatusVerifikasi.PENDING).count();
            int active  = (int) semua.stream().filter(Karyawan::isTerverifikasi).count();

            infoPanel.add(createInfoCard("👥 Total Karyawan", String.valueOf(totalKaryawan), UITheme.INFO));
            infoPanel.add(createInfoCard("✅ Aktif", String.valueOf(active), UITheme.SUCCESS));
            infoPanel.add(createInfoCard("⏳ Pending Verifikasi", String.valueOf(pending), UITheme.WARNING));

            Calendar cal = Calendar.getInstance();
            int[] stats = absensiDAO.hitungStatistik("", cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR));
            infoPanel.add(createInfoCard("📅 Hadir Hari Ini", "Lihat Monitor", UITheme.ACCENT));
        } catch (Exception e) {
            // Fallback jika error
            infoPanel.add(createInfoCard("Sistem Siap", "HRD Dashboard", UITheme.ACCENT));
        }

        panel.add(infoPanel, BorderLayout.NORTH);

        // Absensi diri sendiri (inherit dari EmployeeDashboard)
        JPanel absenPanel = new JPanel(new BorderLayout());
        absenPanel.setBackground(UITheme.BG_CARD);
        absenPanel.setBorder(new CompoundBorder(
            BorderFactory.createLineBorder(UITheme.BORDER),
            BorderFactory.createEmptyBorder(15, 20, 15, 20)
        ));

        JLabel lblAbsenTitle = new JLabel("📥  Absensi Anda (HRD juga wajib absensi)");
        lblAbsenTitle.setFont(UITheme.FONT_SUBTITLE);
        lblAbsenTitle.setForeground(UITheme.PRIMARY);
        absenPanel.add(lblAbsenTitle, BorderLayout.NORTH);

        // Tombol absensi HRD
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        btnPanel.setBackground(UITheme.BG_CARD);

        JButton btnMasuk  = new JButton("📥  ABSEN MASUK");
        JButton btnKeluar = new JButton("📤  ABSEN KELUAR");

        styleAbsenBtn(btnMasuk,  UITheme.ACCENT);
        styleAbsenBtn(btnKeluar, UITheme.PRIMARY);

        btnMasuk.addActionListener(e  -> doAbsenMasukHRD());
        btnKeluar.addActionListener(e -> doAbsenKeluarHRD());

        btnPanel.add(btnMasuk);
        btnPanel.add(btnKeluar);
        absenPanel.add(btnPanel, BorderLayout.CENTER);

        panel.add(absenPanel, BorderLayout.CENTER);
        return panel;
    }

    private void styleAbsenBtn(JButton btn, Color color) {
        btn.setBackground(color);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(180, 44));
    }

    // Absensi HRD - Delegasi ke parent (EmployeeDashboard)
    private void doAbsenMasukHRD() {
        // Memanggil method dari parent melalui simulasi aksi
        JOptionPane.showMessageDialog(this, "Silakan gunakan fitur absensi yang ada di tab Beranda.", "Info", JOptionPane.INFORMATION_MESSAGE);
    }

    private void doAbsenKeluarHRD() {
        JOptionPane.showMessageDialog(this, "Silakan gunakan fitur absensi yang ada di tab Beranda.", "Info", JOptionPane.INFORMATION_MESSAGE);
    }

    private JPanel createInfoCard(String label, String value, Color color) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(UITheme.BG_CARD);
        card.setBorder(new CompoundBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, color),
            new CompoundBorder(
                BorderFactory.createLineBorder(UITheme.BORDER),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
            )
        ));

        JLabel lblValue = new JLabel(value, SwingConstants.LEFT);
        lblValue.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblValue.setForeground(color);

        JLabel lblLabel = new JLabel(label);
        lblLabel.setFont(UITheme.FONT_SMALL);
        lblLabel.setForeground(UITheme.TEXT_SECONDARY);

        card.add(lblValue, BorderLayout.CENTER);
        card.add(lblLabel, BorderLayout.SOUTH);
        return card;
    }

    // ===================== PANEL MANAJEMEN KARYAWAN =====================

    private JPanel createKaryawanPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(UITheme.BG_MAIN);
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(UITheme.BG_MAIN);

        JLabel lblTitle = new JLabel("👥  Manajemen Karyawan");
        lblTitle.setFont(UITheme.FONT_SUBTITLE);
        lblTitle.setForeground(UITheme.PRIMARY);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        btnPanel.setBackground(UITheme.BG_MAIN);

        JButton btnVerifikasi = createActionButton("✅ Verifikasi", UITheme.SUCCESS);
        JButton btnUbahBagian = createActionButton("✏️ Ubah Bagian", UITheme.INFO);
        JButton btnSetGaji    = createActionButton("💰 Set Gaji", UITheme.ACCENT);
        JButton btnRefresh    = createActionButton("🔄 Refresh", UITheme.TEXT_SECONDARY);

        btnPanel.add(btnRefresh);
        btnPanel.add(btnUbahBagian);
        btnPanel.add(btnSetGaji);
        btnPanel.add(btnVerifikasi);

        headerPanel.add(lblTitle, BorderLayout.WEST);
        headerPanel.add(btnPanel, BorderLayout.EAST);
        panel.add(headerPanel, BorderLayout.NORTH);

        // Tabel karyawan
        String[] cols = {"ID Karyawan", "Nama", "Bagian", "Role", "Status", "Gaji Pokok", "Terdaftar"};
        karyawanModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(karyawanModel);
        styleTable(table);

        // Color coding untuk status
        table.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, value, sel, foc, row, col);
                String status = (String) value;
                switch (status) {
                    case "TERVERIFIKASI": setForeground(UITheme.SUCCESS); break;
                    case "PENDING":       setForeground(UITheme.WARNING); break;
                    case "DITOLAK":       setForeground(UITheme.DANGER);  break;
                }
                setFont(UITheme.FONT_BOLD);
                setHorizontalAlignment(SwingConstants.CENTER);
                return this;
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        panel.add(scrollPane, BorderLayout.CENTER);

        // Action listeners
        btnRefresh.addActionListener(e -> loadDataKaryawan());

        btnVerifikasi.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { showWarning("Pilih karyawan terlebih dahulu."); return; }
            String idKaryawan = (String) karyawanModel.getValueAt(row, 0);
            String nama = (String) karyawanModel.getValueAt(row, 1);

            String[] options = {"✅ Verifikasi (Aktifkan)", "❌ Tolak", "Batal"};
            int choice = JOptionPane.showOptionDialog(this,
                "Aksi untuk karyawan: " + nama, "Verifikasi Karyawan",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

            try {
                if (choice == 0) {
                    karyawanDAO.updateStatusVerifikasi(idKaryawan, Karyawan.StatusVerifikasi.TERVERIFIKASI);
                    showSuccess("Karyawan " + nama + " berhasil diverifikasi.");
                } else if (choice == 1) {
                    karyawanDAO.updateStatusVerifikasi(idKaryawan, Karyawan.StatusVerifikasi.DITOLAK);
                    showSuccess("Status karyawan " + nama + " ditolak.");
                }
                loadDataKaryawan();
            } catch (SQLException ex) { showError(ex.getMessage()); }
        });

        btnUbahBagian.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { showWarning("Pilih karyawan terlebih dahulu."); return; }

            String idKaryawan = (String) karyawanModel.getValueAt(row, 0);
            String nama = (String) karyawanModel.getValueAt(row, 1);
            String bagianLama = (String) karyawanModel.getValueAt(row, 2);

            String[] bagianOptions = {"HRD","Keuangan","IT","Marketing","Operasional","Produksi","Legal","Logistik","Lainnya"};
            JComboBox<String> cbBagian = new JComboBox<>(bagianOptions);
            cbBagian.setSelectedItem(bagianLama);

            int result = JOptionPane.showConfirmDialog(this,
                new Object[]{"Ubah bagian kerja " + nama + ":", cbBagian},
                "Ubah Bagian Kerja", JOptionPane.OK_CANCEL_OPTION);

            if (result == JOptionPane.OK_OPTION) {
                try {
                    String bagianBaru = (String) cbBagian.getSelectedItem();
                    karyawanDAO.updateBagian(idKaryawan, bagianBaru);
                    showSuccess("Bagian karyawan berhasil diubah ke: " + bagianBaru);
                    loadDataKaryawan();
                } catch (SQLException ex) { showError(ex.getMessage()); }
            }
        });

        btnSetGaji.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { showWarning("Pilih karyawan terlebih dahulu."); return; }

            String idKaryawan = (String) karyawanModel.getValueAt(row, 0);
            String nama = (String) karyawanModel.getValueAt(row, 1);

            JTextField tfGaji = new JTextField();
            int result = JOptionPane.showConfirmDialog(this,
                new Object[]{"Set gaji pokok untuk " + nama + " (Rp):", tfGaji},
                "Set Gaji Pokok", JOptionPane.OK_CANCEL_OPTION);

            if (result == JOptionPane.OK_OPTION) {
                try {
                    BigDecimal gaji = new BigDecimal(tfGaji.getText().trim().replace(",", "").replace(".", ""));
                    karyawanDAO.updateGajiPokok(idKaryawan, gaji);
                    showSuccess("Gaji pokok berhasil diatur: " + CurrencyUtil.formatRupiahBulat(gaji));
                    loadDataKaryawan();
                } catch (Exception ex) { showError("Format gaji tidak valid: " + ex.getMessage()); }
            }
        });

        return panel;
    }

    private void loadDataKaryawan() {
        if (karyawanModel == null) return;
        karyawanModel.setRowCount(0);
        try {
            List<Karyawan> list = karyawanDAO.findAll();
            for (Karyawan k : list) {
                karyawanModel.addRow(new Object[]{
                    k.getIdKaryawan(), k.getNama(), k.getBagian(), k.getRole().name(),
                    k.getStatusVerifikasi().name(),
                    CurrencyUtil.formatRupiahBulat(k.getGajiPokok()),
                    k.getCreatedAt() != null ? k.getCreatedAt().toString().substring(0, 10) : "-"
                });
            }
        } catch (SQLException e) { showError(e.getMessage()); }
    }

    // ===================== PANEL MONITOR ABSENSI SEMUA =====================

    private JComboBox<Integer> cbMonBulan, cbMonTahun;

    private JPanel createAbsensiSemuaPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(UITheme.BG_MAIN);
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Filter
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filterPanel.setBackground(UITheme.BG_MAIN);
        filterPanel.add(new JLabel("📊 Monitor Absensi - Bulan:"));
        cbMonBulan = createBulanCombo();
        cbMonTahun = createTahunCombo();
        JButton btnFilter = createActionButton("🔍 Tampilkan", UITheme.PRIMARY);
        btnFilter.addActionListener(e -> loadAbsensiSemua());
        filterPanel.add(cbMonBulan);
        filterPanel.add(cbMonTahun);
        filterPanel.add(btnFilter);
        panel.add(filterPanel, BorderLayout.NORTH);

        // Tabel
        String[] cols = {"Tanggal","Nama Karyawan","Jam Masuk","Jam Keluar","Status","Telat","Lembur"};
        absensiSemuaModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(absensiSemuaModel);
        styleTable(table);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        return panel;
    }

    private void loadAbsensiSemua() {
        if (absensiSemuaModel == null) return;
        absensiSemuaModel.setRowCount(0);
        try {
            int bulan = cbMonBulan != null ? (int) cbMonBulan.getSelectedItem() : Calendar.getInstance().get(Calendar.MONTH) + 1;
            int tahun = cbMonTahun != null ? (int) cbMonTahun.getSelectedItem() : Calendar.getInstance().get(Calendar.YEAR);
            List<Absensi> list = absensiDAO.findAllByPeriode(bulan, tahun);
            for (Absensi a : list) {
                absensiSemuaModel.addRow(new Object[]{
                    a.getTanggal(), a.getNamaKaryawan(),
                    a.getJamMasuk() != null ? a.getJamMasuk() : "-",
                    a.getJamKeluar() != null ? a.getJamKeluar() : "-",
                    a.isStatusTelat() ? "Terlambat" : "Tepat Waktu",
                    a.getDurasiTelatFormatted(),
                    a.getDurasiLemburFormatted()
                });
            }
        } catch (SQLException e) { showError(e.getMessage()); }
    }

    // ===================== PANEL LAPORAN =====================

    private JPanel createLaporanPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(UITheme.BG_MAIN);
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Filter
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filterPanel.setBackground(UITheme.BG_MAIN);
        filterPanel.add(new JLabel("📈 Laporan - Periode:"));
        cbLapBulan = createBulanCombo();
        cbLapTahun = createTahunCombo();

        JButton btnAbsensi = createActionButton("📋 Laporan Absensi", UITheme.PRIMARY);
        JButton btnTelat   = createActionButton("⚠️ Keterlambatan",  UITheme.WARNING);
        JButton btnLembur  = createActionButton("⏰ Lembur",          UITheme.INFO);

        btnAbsensi.addActionListener(e -> loadLaporanAbsensi());
        btnTelat.addActionListener(e   -> loadLaporanTelat());
        btnLembur.addActionListener(e  -> loadLaporanLembur());

        filterPanel.add(cbLapBulan);
        filterPanel.add(cbLapTahun);
        filterPanel.add(btnAbsensi);
        filterPanel.add(btnTelat);
        filterPanel.add(btnLembur);
        panel.add(filterPanel, BorderLayout.NORTH);

        // Tabel (shared untuk semua laporan)
        String[] cols = {"Tanggal","Nama","Jam Masuk","Jam Keluar","Status","Telat","Lembur","Uang Lembur"};
        laporanTelatModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(laporanTelatModel);
        styleTable(table);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        return panel;
    }

    private void loadLaporanAbsensi() {
        laporanTelatModel.setRowCount(0);
        try {
            int b = (int) cbLapBulan.getSelectedItem(), t = (int) cbLapTahun.getSelectedItem();
            List<Absensi> list = absensiDAO.findAllByPeriode(b, t);
            populateLaporanTable(list);
        } catch (SQLException e) { showError(e.getMessage()); }
    }

    private void loadLaporanTelat() {
        laporanTelatModel.setRowCount(0);
        try {
            int b = (int) cbLapBulan.getSelectedItem(), t = (int) cbLapTahun.getSelectedItem();
            List<Absensi> list = absensiDAO.findLateByPeriode(b, t);
            populateLaporanTable(list);
        } catch (SQLException e) { showError(e.getMessage()); }
    }

    private void loadLaporanLembur() {
        laporanTelatModel.setRowCount(0);
        try {
            int b = (int) cbLapBulan.getSelectedItem(), t = (int) cbLapTahun.getSelectedItem();
            List<Absensi> list = absensiDAO.findOvertimeByPeriode(b, t);
            populateLaporanTable(list);
        } catch (SQLException e) { showError(e.getMessage()); }
    }

    private void populateLaporanTable(List<Absensi> list) {
        for (Absensi a : list) {
            laporanTelatModel.addRow(new Object[]{
                a.getTanggal(), a.getNamaKaryawan(),
                a.getJamMasuk() != null ? a.getJamMasuk() : "-",
                a.getJamKeluar() != null ? a.getJamKeluar() : "-",
                a.isStatusTelat() ? "Terlambat" : "Tepat Waktu",
                a.getDurasiTelatFormatted(),
                a.getDurasiLemburFormatted(),
                a.getUangLembur() != null ? CurrencyUtil.formatRupiahBulat(a.getUangLembur()) : "-"
            });
        }
    }

    // ===================== PANEL MANAJEMEN GAJI =====================

    private JPanel createGajiManagementPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(UITheme.BG_MAIN);
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Filter + Tombol aksi
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(UITheme.BG_MAIN);

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filterPanel.setBackground(UITheme.BG_MAIN);
        filterPanel.add(new JLabel("💰 Manajemen Gaji - Periode:"));
        cbGajiBulan = createBulanCombo();
        cbGajiTahun = createTahunCombo();
        JButton btnLoad     = createActionButton("📂 Tampilkan", UITheme.PRIMARY);
        JButton btnGenerate = createActionButton("⚙️ Generate Gaji", UITheme.ACCENT);
        JButton btnBonus    = createActionButton("🎁 Set Bonus", UITheme.SUCCESS);
        JButton btnBayar    = createActionButton("✅ Tandai Dibayar", UITheme.INFO);

        filterPanel.add(cbGajiBulan);
        filterPanel.add(cbGajiTahun);
        filterPanel.add(btnLoad);
        filterPanel.add(btnGenerate);
        filterPanel.add(btnBonus);
        filterPanel.add(btnBayar);
        topPanel.add(filterPanel, BorderLayout.NORTH);
        panel.add(topPanel, BorderLayout.NORTH);

        // Tabel gaji
        String[] cols = {"ID","Nama","Bagian","Gaji Pokok","Lembur","Bonus","Total Gaji","Status"};
        gajiSemuaModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(gajiSemuaModel);
        styleTable(table);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        // Event listeners
        btnLoad.addActionListener(e     -> loadGajiSemua());
        btnGenerate.addActionListener(e -> doGenerateGaji());
        btnBayar.addActionListener(e    -> { /* tandai dibayar */ });
        btnBonus.addActionListener(e    -> doSetBonus(table));

        return panel;
    }

    private void loadGajiSemua() {
        if (gajiSemuaModel == null) return;
        gajiSemuaModel.setRowCount(0);
        try {
            int b = cbGajiBulan != null ? (int) cbGajiBulan.getSelectedItem() : Calendar.getInstance().get(Calendar.MONTH) + 1;
            int t = cbGajiTahun != null ? (int) cbGajiTahun.getSelectedItem() : Calendar.getInstance().get(Calendar.YEAR);
            List<Gaji> list = gajiDAO.findAllByPeriode(b, t);
            for (Gaji g : list) {
                gajiSemuaModel.addRow(new Object[]{
                    g.getIdKaryawan(), g.getNamaKaryawan(), g.getBagian(),
                    CurrencyUtil.formatRupiahBulat(g.getGajiPokok()),
                    CurrencyUtil.formatRupiahBulat(g.getTotalLembur()),
                    CurrencyUtil.formatRupiahBulat(g.getBonus()),
                    CurrencyUtil.formatRupiahBulat(g.getTotalGaji()),
                    g.getStatusBayar().name()
                });
            }
        } catch (SQLException e) { showError(e.getMessage()); }
    }

    private void doGenerateGaji() {
        int b = (int) cbGajiBulan.getSelectedItem();
        int t = (int) cbGajiTahun.getSelectedItem();
        int confirm = JOptionPane.showConfirmDialog(this,
            "Generate/update gaji untuk semua karyawan periode " + b + "/" + t + "?\n" +
            "Data lembur akan dihitung otomatis dari absensi.",
            "Konfirmasi Generate Gaji", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                gajiDAO.generateGajiBulanan(b, t, currentUser.getIdKaryawan());
                showSuccess("Gaji berhasil di-generate untuk periode " + b + "/" + t);
                loadGajiSemua();
            } catch (SQLException e) { showError(e.getMessage()); }
        }
    }

    private void doSetBonus(JTable table) {
        int row = table.getSelectedRow();
        if (row < 0) { showWarning("Pilih karyawan terlebih dahulu."); return; }

        String idKaryawan = (String) gajiSemuaModel.getValueAt(row, 0);
        String nama = (String) gajiSemuaModel.getValueAt(row, 1);

        JTextField tfBonus = new JTextField();
        JTextField tfKet   = new JTextField();
        int result = JOptionPane.showConfirmDialog(this,
            new Object[]{"Bonus untuk " + nama + " (Rp):", tfBonus, "Keterangan:", tfKet},
            "Set Bonus", JOptionPane.OK_CANCEL_OPTION);

        if (result == JOptionPane.OK_OPTION) {
            try {
                BigDecimal bonus = new BigDecimal(tfBonus.getText().trim().replace(",","").replace(".",""));
                int b = (int) cbGajiBulan.getSelectedItem();
                int t = (int) cbGajiTahun.getSelectedItem();
                gajiDAO.updateBonus(idKaryawan, b, t, bonus, tfKet.getText().trim());
                showSuccess("Bonus berhasil disimpan: " + CurrencyUtil.formatRupiahBulat(bonus));
                loadGajiSemua();
            } catch (Exception e) { showError("Format nominal tidak valid."); }
        }
    }

    // ===================== HELPER =====================

    private JComboBox<Integer> createBulanCombo() {
        JComboBox<Integer> cb = new JComboBox<>();
        for (int i = 1; i <= 12; i++) cb.addItem(i);
        cb.setSelectedItem(Calendar.getInstance().get(Calendar.MONTH) + 1);
        return cb;
    }

    private JComboBox<Integer> createTahunCombo() {
        JComboBox<Integer> cb = new JComboBox<>();
        int tahunSekarang = Calendar.getInstance().get(Calendar.YEAR);
        for (int t = tahunSekarang; t >= tahunSekarang - 5; t--) cb.addItem(t);
        return cb;
    }

    private JButton createActionButton(String text, Color color) {
        JButton btn = new JButton(text);
        btn.setBackground(color);
        btn.setForeground(Color.WHITE);
        btn.setFont(UITheme.FONT_SMALL);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JPanel createBerandaPanel() { return createBerandaHRD(); }

    private void showSuccess(String msg) { JOptionPane.showMessageDialog(this, msg, "Sukses", JOptionPane.INFORMATION_MESSAGE); }
    private void showWarning(String msg) { JOptionPane.showMessageDialog(this, msg, "Peringatan", JOptionPane.WARNING_MESSAGE); }
    private void showError(String msg)   { JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE); }

    private void doHRDLogout() {
        int confirm = JOptionPane.showConfirmDialog(this, "Yakin ingin logout?", "Logout", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            dispose();
            new com.absensi.ui.auth.LoginFrame().setVisible(true);
        }
    }
}
