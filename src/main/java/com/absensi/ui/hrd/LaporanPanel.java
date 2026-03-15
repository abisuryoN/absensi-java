package com.absensi.ui.hrd;

import com.absensi.model.Absensi;
import com.absensi.model.Gaji;
import com.absensi.model.Karyawan;
import com.absensi.service.AbsensiService;
import com.absensi.service.GajiService;
import com.absensi.util.CurrencyUtil;
import com.absensi.util.ExcelExporter;
import com.absensi.util.UITheme;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.io.File;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.List;

/**
 * Panel Laporan untuk HRD.
 *
 * Jenis laporan yang tersedia:
 *  1. Laporan Absensi Semua Karyawan — per periode bulan/tahun
 *  2. Laporan Keterlambatan — hanya yang telat
 *  3. Laporan Lembur — hanya yang ada lemburnya
 *  4. Laporan Gaji — rekap gaji semua karyawan per periode
 *
 * Fitur tambahan:
 *  - Tombol Export Excel (.xlsx) untuk setiap laporan
 *  - Ringkasan statistik kecil di atas tabel
 *  - Tab selector untuk memilih jenis laporan
 */
public class LaporanPanel extends JPanel {

    private final Karyawan       hrdUser;
    private final AbsensiService absensiService = new AbsensiService();
    private final GajiService    gajiService    = new GajiService();

    // ── Filter ────────────────────────────────────────────────
    private JComboBox<String>  cbBulan;
    private JComboBox<Integer> cbTahun;
    private JComboBox<String>  cbJenisLaporan;

    // ── Tabel ─────────────────────────────────────────────────
    private DefaultTableModel tableModel;
    private JTable            table;
    private JLabel            lblJumlahRecord;
    private JLabel            lblStatusBar;

    // ── Jenis laporan ─────────────────────────────────────────
    private static final String LAP_ABSENSI    = "📋  Absensi Semua Karyawan";
    private static final String LAP_KETERLAMBATAN = "⚠️  Keterlambatan";
    private static final String LAP_LEMBUR     = "⏰  Lembur";
    private static final String LAP_GAJI       = "💰  Gaji & Penggajian";

    private static final String[] NAMA_BULAN = {
        "Januari","Februari","Maret","April","Mei","Juni",
        "Juli","Agustus","September","Oktober","November","Desember"
    };

    public LaporanPanel(Karyawan hrdUser) {
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

        add(buildHeader(),  BorderLayout.NORTH);
        add(buildTable(),   BorderLayout.CENTER);
        add(buildFooter(),  BorderLayout.SOUTH);
    }

    // ── Header: filter + tombol cetak ────────────────────────

    private JPanel buildHeader() {
        JPanel outer = new JPanel(new BorderLayout(0, 8));
        outer.setBackground(UITheme.BG_MAIN);

        // Baris 1: judul
        JLabel lblTitle = new JLabel("📈  Laporan HRD");
        lblTitle.setFont(UITheme.FONT_SUBTITLE);
        lblTitle.setForeground(UITheme.PRIMARY);
        outer.add(lblTitle, BorderLayout.NORTH);

        // Baris 2: filter
        JPanel filterRow = new JPanel(new BorderLayout(10, 0));
        filterRow.setBackground(UITheme.BG_MAIN);

        // Jenis laporan (kiri)
        JPanel leftFilter = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftFilter.setBackground(UITheme.BG_MAIN);
        leftFilter.add(new JLabel("Jenis Laporan:"));

        cbJenisLaporan = new JComboBox<>(new String[]{
            LAP_ABSENSI, LAP_KETERLAMBATAN, LAP_LEMBUR, LAP_GAJI
        });
        cbJenisLaporan.setFont(UITheme.FONT_BODY);
        cbJenisLaporan.setPreferredSize(new Dimension(230, 30));
        leftFilter.add(cbJenisLaporan);
        filterRow.add(leftFilter, BorderLayout.WEST);

        // Periode + tombol (kanan)
        JPanel rightFilter = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightFilter.setBackground(UITheme.BG_MAIN);
        rightFilter.add(new JLabel("Periode:"));

        cbBulan = new JComboBox<>(NAMA_BULAN);
        cbBulan.setSelectedIndex(Calendar.getInstance().get(Calendar.MONTH));
        cbBulan.setFont(UITheme.FONT_BODY);
        cbBulan.setPreferredSize(new Dimension(115, 30));
        rightFilter.add(cbBulan);

        cbTahun = new JComboBox<>();
        int th = Calendar.getInstance().get(Calendar.YEAR);
        for (int t = th; t >= th - 5; t--) cbTahun.addItem(t);
        cbTahun.setFont(UITheme.FONT_BODY);
        cbTahun.setPreferredSize(new Dimension(78, 30));
        rightFilter.add(cbTahun);

        JButton btnTampil = makeBtn("🔍 Tampilkan", UITheme.PRIMARY);
        btnTampil.addActionListener(e -> loadLaporan());
        rightFilter.add(btnTampil);

        JButton btnExcel = makeBtn("📊 Export Excel", new Color(0x21, 0x7B, 0x17));
        btnExcel.addActionListener(e -> doExportExcel());
        rightFilter.add(btnExcel);

        filterRow.add(rightFilter, BorderLayout.EAST);
        outer.add(filterRow, BorderLayout.CENTER);
        return outer;
    }

    // ── Tabel ────────────────────────────────────────────────

    private JPanel buildTable() {
        // Kolom default (akan diganti saat load laporan)
        tableModel = new DefaultTableModel(new String[]{"Silakan pilih jenis laporan dan klik Tampilkan"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        table = new JTable(tableModel);
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

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER));

        JPanel wrapper = new JPanel(new BorderLayout(0, 4));
        wrapper.setBackground(UITheme.BG_MAIN);
        wrapper.add(scroll, BorderLayout.CENTER);

        lblJumlahRecord = new JLabel("  Pilih laporan dan klik Tampilkan");
        lblJumlahRecord.setFont(UITheme.FONT_SMALL);
        lblJumlahRecord.setForeground(UITheme.TEXT_SECONDARY);
        wrapper.add(lblJumlahRecord, BorderLayout.SOUTH);

        return wrapper;
    }

    // ── Footer: status bar ───────────────────────────────────

    private JPanel buildFooter() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(0xF8, 0xF9, 0xFA));
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, UITheme.BORDER),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        lblStatusBar = new JLabel("Siap");
        lblStatusBar.setFont(UITheme.FONT_SMALL);
        lblStatusBar.setForeground(UITheme.TEXT_SECONDARY);
        p.add(lblStatusBar, BorderLayout.WEST);
        return p;
    }

    // =========================================================
    //  LOAD LAPORAN
    // =========================================================

    public void loadLaporan() {
        int bulan = cbBulan.getSelectedIndex() + 1;
        int tahun = (int) cbTahun.getSelectedItem();
        String jenis = (String) cbJenisLaporan.getSelectedItem();

        lblStatusBar.setText("Memuat " + jenis + " ...");
        tableModel.setRowCount(0);

        try {
            switch (jenis) {
                case LAP_ABSENSI:
                    loadAbsensi(bulan, tahun);
                    break;
                case LAP_KETERLAMBATAN:
                    loadKeterlambatan(bulan, tahun);
                    break;
                case LAP_LEMBUR:
                    loadLembur(bulan, tahun);
                    break;
                case LAP_GAJI:
                    loadGaji(bulan, tahun);
                    break;
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                "Gagal memuat laporan:\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            lblStatusBar.setText("Gagal: " + e.getMessage());
        }
    }

    // ── Absensi Semua ─────────────────────────────────────────

    private void loadAbsensi(int bulan, int tahun) throws SQLException {
        gantKolomAbsensi();
        List<Absensi> list = absensiService.getSemuaAbsensiPeriode(bulan, tahun);
        int no = 1;
        for (Absensi a : list) {
            tableModel.addRow(new Object[]{
                no++,
                a.getTanggal(),
                a.getNamaKaryawan(),
                a.getJamMasuk()  != null ? a.getJamMasuk().toString()  : "—",
                a.getJamKeluar() != null ? a.getJamKeluar().toString() : "—",
                a.isStatusTelat() ? "Terlambat" : "Tepat Waktu",
                a.getDurasiTelatFormatted(),
                a.getDurasiLemburFormatted(),
                a.getUangLembur() != null ? CurrencyUtil.formatRupiahBulat(a.getUangLembur()) : "—"
            });
        }
        updateStatusBar(list.size(), NAMA_BULAN[bulan-1] + " " + tahun);
    }

    // ── Keterlambatan ─────────────────────────────────────────

    private void loadKeterlambatan(int bulan, int tahun) throws SQLException {
        gantKolomAbsensi();
        List<Absensi> list = absensiService.getLaporanKeterlambatan(bulan, tahun);
        int no = 1, totalMenit = 0;
        for (Absensi a : list) {
            tableModel.addRow(new Object[]{
                no++,
                a.getTanggal(),
                a.getNamaKaryawan(),
                a.getJamMasuk() != null ? a.getJamMasuk().toString() : "—",
                "—",
                "Terlambat",
                a.getDurasiTelatFormatted(),
                "—", "—"
            });
            totalMenit += a.getDurasiTelat();
        }
        updateStatusBar(list.size(), NAMA_BULAN[bulan-1] + " " + tahun);
        lblStatusBar.setText(lblStatusBar.getText() +
            "  |  Total keterlambatan: " + (totalMenit / 60) + " jam " + (totalMenit % 60) + " menit");
    }

    // ── Lembur ────────────────────────────────────────────────

    private void loadLembur(int bulan, int tahun) throws SQLException {
        gantKolomAbsensi();
        List<Absensi> list = absensiService.getLaporanLembur(bulan, tahun);
        int no = 1;
        java.math.BigDecimal totalUang = java.math.BigDecimal.ZERO;
        for (Absensi a : list) {
            tableModel.addRow(new Object[]{
                no++,
                a.getTanggal(),
                a.getNamaKaryawan(),
                a.getJamMasuk()  != null ? a.getJamMasuk().toString()  : "—",
                a.getJamKeluar() != null ? a.getJamKeluar().toString() : "—",
                "Tepat Waktu",
                "—",
                a.getDurasiLemburFormatted(),
                a.getUangLembur() != null ? CurrencyUtil.formatRupiahBulat(a.getUangLembur()) : "—"
            });
            if (a.getUangLembur() != null) totalUang = totalUang.add(a.getUangLembur());
        }
        updateStatusBar(list.size(), NAMA_BULAN[bulan-1] + " " + tahun);
        lblStatusBar.setText(lblStatusBar.getText() +
            "  |  Total uang lembur: " + CurrencyUtil.formatRupiahBulat(totalUang));
    }

    // ── Gaji ──────────────────────────────────────────────────

    private void loadGaji(int bulan, int tahun) throws SQLException {
        // Ganti kolom
        tableModel = new DefaultTableModel(new String[]{
            "No","ID","Nama","Bagian","Gaji Pokok","Lembur","Bonus","Potongan","Total Gaji","Status"
        }, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table.setModel(tableModel);

        List<Gaji> list = gajiService.getGajiPeriode(bulan, tahun);
        int no = 1;
        java.math.BigDecimal grandTotal = java.math.BigDecimal.ZERO;
        for (Gaji g : list) {
            tableModel.addRow(new Object[]{
                no++,
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
            if (g.getTotalGaji() != null) grandTotal = grandTotal.add(g.getTotalGaji());
        }

        updateStatusBar(list.size(), NAMA_BULAN[bulan-1] + " " + tahun);
        lblStatusBar.setText(lblStatusBar.getText() +
            "  |  Total pengeluaran: " + CurrencyUtil.formatRupiahBulat(grandTotal));
    }

    // =========================================================
    //  EXPORT EXCEL
    // =========================================================

    private void doExportExcel() {
        if (tableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this,
                "Tidak ada data untuk diekspor. Tampilkan laporan terlebih dahulu.",
                "Perhatian", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int bulan = cbBulan.getSelectedIndex() + 1;
        int tahun = (int) cbTahun.getSelectedItem();
        String jenis = (String) cbJenisLaporan.getSelectedItem();

        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Simpan Laporan Excel");
        String defaultName = "Laporan_" + jenis.replaceAll("[^a-zA-Z0-9]","_") +
            "_" + NAMA_BULAN[bulan-1] + "_" + tahun + ".xlsx";
        fc.setSelectedFile(new File(defaultName));
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Excel Files (*.xlsx)", "xlsx"));

        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        String path = fc.getSelectedFile().getAbsolutePath();
        if (!path.endsWith(".xlsx")) path += ".xlsx";

        boolean ok = false;
        try {
            if (LAP_GAJI.equals(jenis)) {
                ok = ExcelExporter.exportLaporanGaji(
                    gajiService.getGajiPeriode(bulan, tahun), bulan, tahun, path);
            } else {
                List<Absensi> data;
                if (LAP_KETERLAMBATAN.equals(jenis)) {
                    data = absensiService.getLaporanKeterlambatan(bulan, tahun);
                } else if (LAP_LEMBUR.equals(jenis)) {
                    data = absensiService.getLaporanLembur(bulan, tahun);
                } else {
                    data = absensiService.getSemuaAbsensiPeriode(bulan, tahun);
                }
                ok = ExcelExporter.exportLaporanAbsensi(data, bulan, tahun, path);
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (ok) {
            JOptionPane.showMessageDialog(this,
                "✅  Laporan berhasil disimpan:\n" + path,
                "Export Berhasil", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this,
                "Gagal menyimpan file Excel.", "Gagal", JOptionPane.ERROR_MESSAGE);
        }
    }

    // =========================================================
    //  HELPERS
    // =========================================================

    private void gantKolomAbsensi() {
        tableModel = new DefaultTableModel(new String[]{
            "No", "Tanggal", "Nama Karyawan",
            "Jam Masuk", "Jam Keluar",
            "Status", "Terlambat", "Lembur", "Uang Lembur"
        }, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table.setModel(tableModel);
        applyTableRenderer();
    }

    private void applyTableRenderer() {
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v,
                    boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, v, sel, foc, row, col);
                setBackground(sel ? UITheme.TABLE_SELECT
                    : (row % 2 == 0 ? UITheme.TABLE_ROW_ODD : UITheme.TABLE_ROW_EVEN));
                setForeground(UITheme.TEXT_PRIMARY);
                setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                // Warna status
                if (v instanceof String) {
                    String s = (String) v;
                    if ("Terlambat".equals(s))  setForeground(UITheme.DANGER);
                    if ("Tepat Waktu".equals(s)) setForeground(UITheme.SUCCESS);
                    if ("DIBAYAR".equals(s))     setForeground(UITheme.SUCCESS);
                    if ("PENDING".equals(s))     setForeground(UITheme.WARNING);
                }
                return this;
            }
        });
    }

    private void updateStatusBar(int jumlah, String periode) {
        lblJumlahRecord.setText("  " + jumlah + " record ditemukan untuk periode " + periode);
        lblStatusBar.setText("Laporan dimuat: " + jumlah + " baris  |  Periode: " + periode);
    }

    private JButton makeBtn(String text, Color color) {
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