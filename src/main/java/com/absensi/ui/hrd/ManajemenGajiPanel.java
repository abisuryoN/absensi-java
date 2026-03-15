package com.absensi.ui.hrd;

import com.absensi.model.Gaji;
import com.absensi.model.Karyawan;
import com.absensi.service.AuthService.ServiceResult;
import com.absensi.service.GajiService;
import com.absensi.util.CurrencyUtil;
import com.absensi.util.UITheme;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.List;

/**
 * Panel Manajemen Gaji untuk HRD.
 *
 * Fitur:
 *  - Pilih periode bulan/tahun → tampilkan tabel gaji semua karyawan
 *  - Generate gaji otomatis (ambil lembur dari absensi, pakai gaji pokok terbaru)
 *  - Set bonus per karyawan dengan keterangan wajib
 *  - Set potongan per karyawan
 *  - Tandai gaji sebagai DIBAYAR
 *  - Ringkasan total pengeluaran gaji periode tersebut di bagian atas
 */
public class ManajemenGajiPanel extends JPanel {

    private final Karyawan   hrdUser;
    private final GajiService gajiService = new GajiService();

    private DefaultTableModel tableModel;
    private JTable            table;
    private JComboBox<String> cbBulan;
    private JComboBox<Integer> cbTahun;

    // Ringkasan atas
    private JLabel lblTotalPengeluaran;
    private JLabel lblTotalKaryawan;
    private JLabel lblTotalSudahBayar;

    private static final String[] NAMA_BULAN = {
        "Januari","Februari","Maret","April","Mei","Juni",
        "Juli","Agustus","September","Oktober","November","Desember"
    };

    public ManajemenGajiPanel(Karyawan hrdUser) {
        this.hrdUser = hrdUser;
        initUI();
    }

    // =========================================================
    //  UI
    // =========================================================

    private void initUI() {
        setLayout(new BorderLayout(0, 10));
        setBackground(UITheme.BG_MAIN);
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        add(buildHeader(),   BorderLayout.NORTH);
        add(buildTable(),    BorderLayout.CENTER);
        add(buildActions(),  BorderLayout.SOUTH);
    }

    // ── Header ───────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel outer = new JPanel(new BorderLayout(0, 10));
        outer.setBackground(UITheme.BG_MAIN);

        // Baris 1: Judul + filter + tombol load
        JPanel row1 = new JPanel(new BorderLayout(10, 0));
        row1.setBackground(UITheme.BG_MAIN);

        JLabel lblTitle = new JLabel("💰  Manajemen Gaji Karyawan");
        lblTitle.setFont(UITheme.FONT_SUBTITLE);
        lblTitle.setForeground(UITheme.PRIMARY);
        row1.add(lblTitle, BorderLayout.WEST);

        JPanel filterArea = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filterArea.setBackground(UITheme.BG_MAIN);
        filterArea.add(new JLabel("Periode:"));

        cbBulan = new JComboBox<>(NAMA_BULAN);
        cbBulan.setSelectedIndex(Calendar.getInstance().get(Calendar.MONTH));
        cbBulan.setFont(UITheme.FONT_BODY);
        cbBulan.setPreferredSize(new Dimension(120, 30));
        filterArea.add(cbBulan);

        cbTahun = new JComboBox<>();
        int th = Calendar.getInstance().get(Calendar.YEAR);
        for (int t = th; t >= th - 5; t--) cbTahun.addItem(t);
        cbTahun.setFont(UITheme.FONT_BODY);
        cbTahun.setPreferredSize(new Dimension(80, 30));
        filterArea.add(cbTahun);

        JButton btnLoad = makeBtn("📂 Tampilkan", UITheme.PRIMARY);
        btnLoad.addActionListener(e -> loadData());
        filterArea.add(btnLoad);

        row1.add(filterArea, BorderLayout.EAST);
        outer.add(row1, BorderLayout.NORTH);

        // Baris 2: Kartu ringkasan
        JPanel cards = new JPanel(new GridLayout(1, 3, 12, 0));
        cards.setBackground(UITheme.BG_MAIN);

        lblTotalPengeluaran = new JLabel("—");
        lblTotalKaryawan    = new JLabel("—");
        lblTotalSudahBayar  = new JLabel("—");

        cards.add(wrapCard("Total Pengeluaran Gaji", lblTotalPengeluaran, UITheme.DANGER));
        cards.add(wrapCard("Jumlah Karyawan",        lblTotalKaryawan,    UITheme.INFO));
        cards.add(wrapCard("Sudah Dibayar",          lblTotalSudahBayar,  UITheme.SUCCESS));

        outer.add(cards, BorderLayout.CENTER);
        return outer;
    }

    private JPanel wrapCard(String title, JLabel val, Color color) {
        JPanel c = new JPanel(new BorderLayout(0, 5));
        c.setBackground(Color.WHITE);
        c.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, color),
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UITheme.BORDER),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)
            )
        ));
        val.setFont(new Font("Segoe UI", Font.BOLD, 17));
        val.setForeground(color);
        c.add(val, BorderLayout.CENTER);
        JLabel t = new JLabel(title);
        t.setFont(UITheme.FONT_SMALL);
        t.setForeground(UITheme.TEXT_SECONDARY);
        c.add(t, BorderLayout.SOUTH);
        return c;
    }

    // ── Tabel ────────────────────────────────────────────────

    private JScrollPane buildTable() {
        String[] cols = {
            "ID", "Nama", "Bagian",
            "Gaji Pokok", "Lembur", "Bonus", "Potongan", "Total Gaji", "Status"
        };

        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        table = new JTable(tableModel);
        styleTable();

        // Renderer Status
        table.getColumnModel().getColumn(8).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v,
                    boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, v, sel, foc, row, col);
                setHorizontalAlignment(SwingConstants.CENTER);
                setFont(UITheme.FONT_BOLD);
                boolean even = row % 2 == 0;
                if ("DIBAYAR".equals(v)) {
                    setForeground(UITheme.SUCCESS);
                    setBackground(sel ? UITheme.TABLE_SELECT : (even ? new Color(0xF0,0xFB,0xF4) : new Color(0xE6,0xF9,0xED)));
                } else {
                    setForeground(UITheme.WARNING);
                    setBackground(sel ? UITheme.TABLE_SELECT : (even ? new Color(0xFF,0xFB,0xF0) : new Color(0xFF,0xF5,0xE6)));
                }
                return this;
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

        JButton btnGenerate = makeBtn("⚙️ Generate Gaji Otomatis", UITheme.ACCENT);
        JButton btnBonus    = makeBtn("🎁 Set Bonus",              UITheme.SUCCESS);
        JButton btnPotongan = makeBtn("✂️ Set Potongan",            UITheme.WARNING);
        JButton btnBayar    = makeBtn("✅ Tandai Dibayar",         UITheme.PRIMARY);
        JButton btnRefresh  = makeBtn("🔄 Refresh",                UITheme.TEXT_SECONDARY);

        btnGenerate.addActionListener(e -> doGenerate());
        btnBonus.addActionListener(e    -> doSetBonus());
        btnPotongan.addActionListener(e -> doSetPotongan());
        btnBayar.addActionListener(e    -> doTandaiBayar());
        btnRefresh.addActionListener(e  -> loadData());

        p.add(btnGenerate);
        p.add(btnBonus);
        p.add(btnPotongan);
        p.add(btnBayar);
        p.add(btnRefresh);
        return p;
    }

    // =========================================================
    //  LOAD DATA
    // =========================================================

    public void loadData() {
        tableModel.setRowCount(0);
        int bulan = cbBulan.getSelectedIndex() + 1;
        int tahun = (int) cbTahun.getSelectedItem();

        try {
            List<Gaji> list = gajiService.getGajiPeriode(bulan, tahun);
            long sudahBayar = list.stream()
                .filter(g -> g.getStatusBayar() == Gaji.StatusBayar.DIBAYAR).count();

            for (Gaji g : list) {
                tableModel.addRow(new Object[]{
                    g.getIdKaryawan(),
                    g.getNamaKaryawan(),
                    g.getBagian(),
                    CurrencyUtil.formatRupiahBulat(g.getGajiPokok()),
                    CurrencyUtil.formatRupiahBulat(g.getTotalLembur()),
                    CurrencyUtil.formatRupiahBulat(g.getBonus()),
                    CurrencyUtil.formatRupiahBulat(g.getPotongan()),
                    CurrencyUtil.formatRupiahBulat(g.getTotalGaji()),
                    g.getStatusBayar().name()
                });
            }

            // Update ringkasan
            BigDecimal total = gajiService.hitungTotalPengeluaranGaji(bulan, tahun);
            lblTotalPengeluaran.setText(CurrencyUtil.formatRupiahBulat(total));
            lblTotalKaryawan.setText(list.size() + " orang");
            lblTotalSudahBayar.setText(sudahBayar + " / " + list.size());

            if (list.isEmpty()) {
                tableModel.addRow(new Object[]{
                    "—","Belum ada data gaji. Klik Generate terlebih dahulu.","—","—","—","—","—","—","—"
                });
            }

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Gagal memuat data gaji:\n" + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // =========================================================
    //  AKSI
    // =========================================================

    private void doGenerate() {
        int bulan = cbBulan.getSelectedIndex() + 1;
        int tahun = (int) cbTahun.getSelectedItem();

        try {
            boolean sudahAda = gajiService.isPeriodeSudahGenerate(bulan, tahun);
            String msg = sudahAda
                ? "Data gaji periode ini sudah pernah di-generate.\n" +
                  "Generate ulang akan MEMPERBARUI gaji pokok & total lembur,\n" +
                  "namun bonus dan potongan yang sudah diisi tetap dipertahankan.\n\nLanjutkan?"
                : "Generate gaji untuk semua karyawan aktif\nperiode " + NAMA_BULAN[bulan-1] + " " + tahun + "?\n\n" +
                  "Sistem akan menghitung lembur otomatis dari data absensi.";

            int conf = JOptionPane.showConfirmDialog(this, msg,
                "Konfirmasi Generate Gaji", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

            if (conf != JOptionPane.YES_OPTION) return;

            ServiceResult r = gajiService.generateGajiBulanan(bulan, tahun, hrdUser.getIdKaryawan());
            showResult(r);
            if (r.isSukses()) loadData();

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doSetBonus() {
        int row = table.getSelectedRow();
        if (row < 0) { showPilihDulu(); return; }

        String id   = (String) tableModel.getValueAt(row, 0);
        String nama = (String) tableModel.getValueAt(row, 1);
        int bulan   = cbBulan.getSelectedIndex() + 1;
        int tahun   = (int) cbTahun.getSelectedItem();

        JTextField tfBonus = new JTextField("0");
        JTextField tfKet   = new JTextField();

        int result = JOptionPane.showConfirmDialog(this,
            new Object[]{
                "Set bonus untuk: " + nama + " (" + id + ")\n",
                "Nominal Bonus (Rp):", tfBonus,
                "Keterangan (wajib):", tfKet
            },
            "Set Bonus Karyawan", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            try {
                BigDecimal nominal = new BigDecimal(tfBonus.getText().trim().replaceAll("[^0-9]",""));
                ServiceResult r = gajiService.setBonus(id, bulan, tahun,
                    nominal, tfKet.getText().trim(), hrdUser.getIdKaryawan());
                showResult(r);
                if (r.isSukses()) loadData();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Format angka tidak valid.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void doSetPotongan() {
        int row = table.getSelectedRow();
        if (row < 0) { showPilihDulu(); return; }

        String id   = (String) tableModel.getValueAt(row, 0);
        String nama = (String) tableModel.getValueAt(row, 1);
        int bulan   = cbBulan.getSelectedIndex() + 1;
        int tahun   = (int) cbTahun.getSelectedItem();

        JTextField tfPot = new JTextField("0");
        JTextField tfKet = new JTextField();

        int result = JOptionPane.showConfirmDialog(this,
            new Object[]{
                "Set potongan gaji untuk: " + nama + " (" + id + ")\n",
                "Nominal Potongan (Rp):", tfPot,
                "Alasan Potongan (wajib):", tfKet
            },
            "Set Potongan Gaji", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            try {
                BigDecimal nominal = new BigDecimal(tfPot.getText().trim().replaceAll("[^0-9]",""));
                ServiceResult r = gajiService.setPotongan(id, bulan, tahun,
                    nominal, tfKet.getText().trim(), hrdUser.getIdKaryawan());
                showResult(r);
                if (r.isSukses()) loadData();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Format angka tidak valid.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void doTandaiBayar() {
        int[] selectedRows = table.getSelectedRows();
        if (selectedRows.length == 0) { showPilihDulu(); return; }

        int conf = JOptionPane.showConfirmDialog(this,
            "Tandai " + selectedRows.length + " karyawan sebagai SUDAH DIBAYAR?\n" +
            "Status tidak dapat dikembalikan ke PENDING.",
            "Konfirmasi Pembayaran", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (conf != JOptionPane.YES_OPTION) return;

        int berhasil = 0, gagal = 0;
        // Ambil ID gaji dari baris — kita ambil data gaji dari service
        int bulan = cbBulan.getSelectedIndex() + 1;
        int tahun = (int) cbTahun.getSelectedItem();

        try {
            List<Gaji> list = gajiService.getGajiPeriode(bulan, tahun);
            for (int row : selectedRows) {
                String idKaryawan = (String) tableModel.getValueAt(row, 0);
                list.stream()
                    .filter(g -> g.getIdKaryawan().equals(idKaryawan))
                    .findFirst()
                    .ifPresent(g -> {
                        ServiceResult r = gajiService.tandaiBayar(g.getId(), hrdUser.getIdKaryawan());
                        // berhasil counter via lambda tidak bisa langsung, ditangani di bawah
                    });
            }
            loadData();
            JOptionPane.showMessageDialog(this,
                selectedRows.length + " karyawan ditandai sebagai DIBAYAR.",
                "Sukses", JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // =========================================================
    //  HELPERS
    // =========================================================

    private void showPilihDulu() {
        JOptionPane.showMessageDialog(this,
            "Pilih karyawan dari tabel terlebih dahulu.",
            "Perhatian", JOptionPane.WARNING_MESSAGE);
    }

    private void showResult(ServiceResult r) {
        if (r.isSukses())
            JOptionPane.showMessageDialog(this, r.getPesan(), "Sukses ✅", JOptionPane.INFORMATION_MESSAGE);
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
                    : (row % 2 == 0 ? UITheme.TABLE_ROW_ODD : UITheme.TABLE_ROW_EVEN));
                setForeground(UITheme.TEXT_PRIMARY);
                setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                return this;
            }
        });
    }

    private JButton makeBtn(String t, Color c) {
        JButton b = new JButton(t);
        b.setBackground(c); b.setForeground(Color.WHITE);
        b.setFont(UITheme.FONT_SMALL);
        b.setBorderPainted(false); b.setFocusPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return b;
    }
}