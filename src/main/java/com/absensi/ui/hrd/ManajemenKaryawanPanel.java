package com.absensi.ui.hrd;

import com.absensi.model.Karyawan;
import com.absensi.service.AuthService;
import com.absensi.service.AuthService.ServiceResult;
import com.absensi.util.CurrencyUtil;
import com.absensi.util.UITheme;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

public class ManajemenKaryawanPanel extends JPanel {

    private final Karyawan    hrdUser;
    private final AuthService authService = new AuthService();

    private DefaultTableModel tableModel;
    private JTable            table;
    private JComboBox<String> cbFilterStatus;
    private JLabel            lblBadgePending;

    public ManajemenKaryawanPanel(Karyawan hrdUser) {
        this.hrdUser = hrdUser;
        initUI();
        loadData();
    }

    private void initUI() {
        setLayout(new BorderLayout(0, 10));
        setBackground(UITheme.BG_MAIN);
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        add(buildHeader(),  BorderLayout.NORTH);
        add(buildTable(),   BorderLayout.CENTER);
        add(buildActions(), BorderLayout.SOUTH);
    }

    // ── Header ───────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setBackground(UITheme.BG_MAIN);
        p.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setBackground(UITheme.BG_MAIN);

        JLabel lblTitle = new JLabel("Manajemen Karyawan");
        lblTitle.setFont(UITheme.FONT_SUBTITLE);
        lblTitle.setForeground(UITheme.PRIMARY);

        lblBadgePending = new JLabel();
        lblBadgePending.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblBadgePending.setForeground(UITheme.WARNING);
        lblBadgePending.setVisible(false);

        left.add(lblTitle);
        left.add(lblBadgePending);
        p.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setBackground(UITheme.BG_MAIN);
        right.add(new JLabel("Filter Status:"));

        cbFilterStatus = new JComboBox<>(new String[]{"Semua","PENDING","TERVERIFIKASI","DITOLAK"});
        cbFilterStatus.setFont(UITheme.FONT_BODY);
        cbFilterStatus.addActionListener(e -> loadData());
        right.add(cbFilterStatus);

        JButton btnRefresh = makeBtn("Refresh", UITheme.TEXT_SECONDARY);
        btnRefresh.addActionListener(e -> loadData());
        right.add(btnRefresh);

        p.add(right, BorderLayout.EAST);
        return p;
    }

    // ── Tabel ────────────────────────────────────────────────

    private JScrollPane buildTable() {
        String[] cols = {"ID Karyawan","Nama","Divisi / Bagian","Role","Status","Gaji Pokok","Terdaftar"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        styleTable();

        // Renderer Status
        table.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val,
                    boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                setHorizontalAlignment(SwingConstants.CENTER);
                setFont(UITheme.FONT_BOLD);
                switch (String.valueOf(val)) {
                    case "TERVERIFIKASI":
                        setForeground(UITheme.SUCCESS);
                        setBackground(sel ? UITheme.TABLE_SELECT : new Color(0xF0,0xFB,0xF4));
                        break;
                    case "PENDING":
                        setForeground(UITheme.WARNING);
                        setBackground(sel ? UITheme.TABLE_SELECT : new Color(0xFF,0xFB,0xF0));
                        break;
                    case "DITOLAK":
                        setForeground(UITheme.DANGER);
                        setBackground(sel ? UITheme.TABLE_SELECT : new Color(0xFF,0xF5,0xF5));
                        break;
                    default:
                        setForeground(UITheme.TEXT_SECONDARY);
                        setBackground(sel ? UITheme.TABLE_SELECT
                            : (row%2==0 ? UITheme.TABLE_ROW_ODD : UITheme.TABLE_ROW_EVEN));
                }
                return this;
            }
        });

        // Renderer Role
        table.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val,
                    boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                setHorizontalAlignment(SwingConstants.CENTER);
                setFont(UITheme.FONT_BOLD);
                setForeground("HRD".equals(val) ? new Color(0x6F,0x42,0xC1) : UITheme.INFO);
                setBackground(sel ? UITheme.TABLE_SELECT
                    : (row%2==0 ? UITheme.TABLE_ROW_ODD : UITheme.TABLE_ROW_EVEN));
                return this;
            }
        });

        // Double-click → tampilkan foto
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) showFotoKaryawan(table.getSelectedRow());
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER));
        return scroll;
    }

    // ── Tombol aksi ──────────────────────────────────────────

    private JPanel buildActions() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        p.setBackground(UITheme.BG_MAIN);

        JButton btnVerifikasi = makeBtn("Verifikasi Akun",  UITheme.SUCCESS);
        JButton btnTolak      = makeBtn("Tolak Akun",       UITheme.DANGER);
        JButton btnUbahBagian = makeBtn("Koreksi Divisi",   UITheme.INFO);
        JButton btnSetGaji    = makeBtn("Set Gaji Pokok",   UITheme.ACCENT);
        JButton btnHapus      = makeBtn("Hapus Karyawan",   new Color(0x8B,0x00,0x00));

        btnVerifikasi.addActionListener(e -> doVerifikasi());
        btnTolak.addActionListener(e      -> doTolak());
        btnUbahBagian.addActionListener(e -> doKoreksiDivisi());
        btnSetGaji.addActionListener(e    -> doSetGaji());
        btnHapus.addActionListener(e      -> doHapus());

        p.add(btnVerifikasi);
        p.add(btnTolak);
        p.add(btnUbahBagian);
        p.add(btnSetGaji);
        p.add(btnHapus);

        JLabel info = new JLabel("  Double-click baris untuk melihat foto karyawan");
        info.setFont(UITheme.FONT_SMALL);
        info.setForeground(UITheme.TEXT_SECONDARY);
        p.add(info);
        return p;
    }

    // ── Load data ────────────────────────────────────────────

    public void loadData() {
        tableModel.setRowCount(0);
        try {
            String filter = (String) cbFilterStatus.getSelectedItem();
            List<Karyawan> list = "Semua".equals(filter)
                ? authService.getAllKaryawan()
                : authService.getKaryawanByStatus(Karyawan.StatusVerifikasi.valueOf(filter));

            long pending = list.stream()
                .filter(k -> k.getStatusVerifikasi() == Karyawan.StatusVerifikasi.PENDING).count();

            for (Karyawan k : list) {
                tableModel.addRow(new Object[]{
                    k.getIdKaryawan(), k.getNama(), k.getBagian(),
                    k.getRole().name(), k.getStatusVerifikasi().name(),
                    CurrencyUtil.formatRupiahBulat(k.getGajiPokok()),
                    k.getCreatedAt() != null ? k.getCreatedAt().toString().substring(0,10) : "-"
                });
            }

            lblBadgePending.setVisible(pending > 0);
            if (pending > 0) lblBadgePending.setText("  " + pending + " akun menunggu verifikasi");
            if (list.isEmpty())
                tableModel.addRow(new Object[]{"-","Tidak ada data","-","-","-","-","-"});

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                "Gagal memuat data:\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Aksi ─────────────────────────────────────────────────

    private void doVerifikasi() {
        Karyawan k = getSelectedKaryawan(); if (k == null) return;
        if (k.isTerverifikasi()) {
            JOptionPane.showMessageDialog(this, k.getNama() + " sudah terverifikasi.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (JOptionPane.showConfirmDialog(this,
                "Verifikasi akun: " + k.getNama() + " (" + k.getIdKaryawan() + ")?\n" +
                "Divisi: " + k.getBagian(),
                "Konfirmasi", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            ServiceResult r = authService.verifikasiAkun(k.getIdKaryawan(), hrdUser.getIdKaryawan());
            showResult(r);
            if (r.isSukses()) loadData();
        }
    }

    private void doTolak() {
        Karyawan k = getSelectedKaryawan(); if (k == null) return;
        if (JOptionPane.showConfirmDialog(this,
                "Tolak akun: " + k.getNama() + "?",
                "Konfirmasi", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) {
            ServiceResult r = authService.tolakAkun(k.getIdKaryawan(), hrdUser.getIdKaryawan());
            showResult(r);
            if (r.isSukses()) loadData();
        }
    }

    private void doKoreksiDivisi() {
        Karyawan k = getSelectedKaryawan(); if (k == null) return;
        String[] opsi;
        try {
            List<com.absensi.model.Divisi> dList = new com.absensi.dao.DivisiDAO().findAllAktif();
            opsi = dList.stream().map(com.absensi.model.Divisi::getNamaDivisi).toArray(String[]::new);
        } catch (Exception ex) {
            opsi = new String[]{"HRD","Keuangan","IT","Marketing","Operasional","Produksi","Legal","Logistik"};
        }
        JComboBox<String> cb = new JComboBox<>(opsi);
        cb.setSelectedItem(k.getBagian());
        if (JOptionPane.showConfirmDialog(this,
                new Object[]{"Koreksi divisi untuk: " + k.getNama() + "\nDivisi saat ini: " + k.getBagian(), cb},
                "Koreksi Divisi", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            ServiceResult r = authService.koreksiDivisi(k.getIdKaryawan(), (String)cb.getSelectedItem(), hrdUser.getIdKaryawan());
            showResult(r);
            if (r.isSukses()) loadData();
        }
    }

    private void doSetGaji() {
        Karyawan k = getSelectedKaryawan(); if (k == null) return;
        JTextField tf = new JTextField(k.getGajiPokok() != null ? k.getGajiPokok().toPlainString() : "0");
        if (JOptionPane.showConfirmDialog(this,
                new Object[]{"Set gaji pokok untuk: " + k.getNama() + "\nGaji saat ini: " +
                    CurrencyUtil.formatRupiahBulat(k.getGajiPokok()) + "\nGaji baru (Rp):", tf},
                "Set Gaji Pokok", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            try {
                BigDecimal gaji = new BigDecimal(tf.getText().trim().replaceAll("[^0-9]",""));
                ServiceResult r = authService.setGajiPokok(k.getIdKaryawan(), gaji, hrdUser.getIdKaryawan());
                showResult(r);
                if (r.isSukses()) loadData();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Format angka tidak valid.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void doHapus() {
        Karyawan k = getSelectedKaryawan(); if (k == null) return;
        if (k.getIdKaryawan().equals(hrdUser.getIdKaryawan())) {
            JOptionPane.showMessageDialog(this, "Tidak dapat menghapus akun sendiri.", "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (JOptionPane.showConfirmDialog(this,
                "HAPUS PERMANEN akun: " + k.getNama() + "?\nTindakan ini tidak dapat dibatalkan.",
                "Konfirmasi Hapus", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE) == JOptionPane.YES_OPTION) {
            try {
                new com.absensi.dao.KaryawanDAO().delete(k.getIdKaryawan());
                JOptionPane.showMessageDialog(this, "Karyawan berhasil dihapus.", "Sukses", JOptionPane.INFORMATION_MESSAGE);
                loadData();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Gagal menghapus: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ── Tampilkan foto karyawan ──────────────────────────────
    //
    //  FIX: Tidak lagi pakai getFoto() byte[].
    //  Ambil getFotoProfil() (String path) lalu baca file dari disk.
    //  Tampilkan dalam dialog 250x250.

    private void showFotoKaryawan(int row) {
        if (row < 0) return;

        String id = (String) tableModel.getValueAt(row, 0);
        try {
            Karyawan k = authService.getKaryawanById(id);
            if (k == null) return;

            String path = k.getFotoProfil();

            // Tidak ada foto
            if (path == null || path.isBlank()) {
                JOptionPane.showMessageDialog(this,
                    "Karyawan " + k.getNama() + " belum memiliki foto profil.",
                    "Foto Tidak Ada", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            // Load dari file di disk
            File f = new File(path);
            if (!f.exists()) {
                JOptionPane.showMessageDialog(this,
                    "File foto tidak ditemukan:\n" + path + "\n\n" +
                    "Pastikan folder photos/ berada di direktori yang benar.",
                    "File Tidak Ditemukan", JOptionPane.WARNING_MESSAGE);
                return;
            }

            BufferedImage img = ImageIO.read(f);
            if (img == null) {
                JOptionPane.showMessageDialog(this,
                    "Gagal membaca file foto: " + path,
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Scale ke 250x250 untuk tampilan dialog
            Image scaled = img.getScaledInstance(250, 250, Image.SCALE_SMOOTH);
            JLabel lblFoto = new JLabel(new ImageIcon(scaled));
            lblFoto.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

            JOptionPane.showMessageDialog(this, lblFoto,
                "Foto Profil: " + k.getNama() + " (" + k.getIdKaryawan() + ")",
                JOptionPane.PLAIN_MESSAGE);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Gagal memuat foto: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    private Karyawan getSelectedKaryawan() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this,
                "Pilih karyawan dari tabel terlebih dahulu.", "Perhatian", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        String id = (String) tableModel.getValueAt(row, 0);
        try {
            return authService.getKaryawanById(id);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    private void showResult(ServiceResult r) {
        if (r.isSukses())
            JOptionPane.showMessageDialog(this, r.getPesan(), "Sukses", JOptionPane.INFORMATION_MESSAGE);
        else
            JOptionPane.showMessageDialog(this, r.getPesan(), "Gagal", JOptionPane.WARNING_MESSAGE);
    }

    private void styleTable() {
        table.setFont(UITheme.FONT_BODY);
        table.setRowHeight(34);
        table.setGridColor(UITheme.BORDER);
        table.setSelectionBackground(UITheme.TABLE_SELECT);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));

        JTableHeader h = table.getTableHeader();
        h.setBackground(UITheme.TABLE_HEADER);
        h.setForeground(Color.WHITE);
        h.setFont(UITheme.FONT_BOLD);
        h.setPreferredSize(new Dimension(0, 38));
        h.setReorderingAllowed(false);

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v,
                    boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, v, sel, foc, row, col);
                setBackground(sel ? UITheme.TABLE_SELECT
                    : (row%2==0 ? UITheme.TABLE_ROW_ODD : UITheme.TABLE_ROW_EVEN));
                setForeground(UITheme.TEXT_PRIMARY);
                setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                return this;
            }
        });

        int[] w = {110,160,160,90,120,130,100};
        for (int i = 0; i < w.length; i++)
            table.getColumnModel().getColumn(i).setPreferredWidth(w[i]);
    }

    private JButton makeBtn(String text, Color color) {
        JButton b = new JButton(text);
        b.setBackground(color);
        b.setForeground(Color.WHITE);
        b.setFont(UITheme.FONT_SMALL);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
}
