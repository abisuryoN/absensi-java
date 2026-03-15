package com.absensi.ui.employee;

import com.absensi.model.Absensi;
import com.absensi.model.Karyawan;
import com.absensi.service.AbsensiService;
import com.absensi.util.UITheme;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.List;

/**
 * Panel Riwayat Absensi Karyawan.
 *
 * Fitur:
 *  - Tabel riwayat dengan filter bulan & tahun
 *  - Warna status: hijau = tepat waktu, merah = terlambat
 *  - Ringkasan statistik di bawah tabel (total hadir, telat, lembur)
 *  - Double-click baris → tampilkan detail (foto absensi jika ada)
 */
public class RiwayatAbsensiPanel extends JPanel {

    private final Karyawan       user;
    private final AbsensiService absensiService = new AbsensiService();

    private DefaultTableModel tableModel;
    private JTable            table;
    private JComboBox<String> cbBulan;
    private JComboBox<Integer> cbTahun;

    // Label ringkasan
    private JLabel lblTotalHadir;
    private JLabel lblTotalTelat;
    private JLabel lblTotalLembur;

    private static final String[] NAMA_BULAN = {
        "Januari","Februari","Maret","April","Mei","Juni",
        "Juli","Agustus","September","Oktober","November","Desember"
    };

    public RiwayatAbsensiPanel(Karyawan user) {
        this.user = user;
        initUI();
        loadData(); // load bulan berjalan saat pertama kali dibuka
    }

    // =========================================================
    //  UI
    // =========================================================

    private void initUI() {
        setLayout(new BorderLayout(0, 12));
        setBackground(UITheme.BG_MAIN);
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        add(buildHeader(),    BorderLayout.NORTH);
        add(buildTable(),     BorderLayout.CENTER);
        add(buildSummary(),   BorderLayout.SOUTH);
    }

    // ── Header: judul + filter ────────────────────────────────

    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setBackground(UITheme.BG_MAIN);
        p.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));

        JLabel lblTitle = new JLabel("📋  Riwayat Absensi");
        lblTitle.setFont(UITheme.FONT_SUBTITLE);
        lblTitle.setForeground(UITheme.PRIMARY);
        p.add(lblTitle, BorderLayout.WEST);

        // Filter area
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filterPanel.setBackground(UITheme.BG_MAIN);

        filterPanel.add(new JLabel("Filter:"));

        cbBulan = new JComboBox<>(NAMA_BULAN);
        cbBulan.setSelectedIndex(Calendar.getInstance().get(Calendar.MONTH));
        cbBulan.setFont(UITheme.FONT_BODY);
        cbBulan.setPreferredSize(new Dimension(120, 30));
        filterPanel.add(cbBulan);

        cbTahun = new JComboBox<>();
        int tahunNow = Calendar.getInstance().get(Calendar.YEAR);
        for (int t = tahunNow; t >= tahunNow - 5; t--) cbTahun.addItem(t);
        cbTahun.setFont(UITheme.FONT_BODY);
        cbTahun.setPreferredSize(new Dimension(80, 30));
        filterPanel.add(cbTahun);

        JButton btnTampil = new JButton("🔍  Tampilkan");
        btnTampil.setBackground(UITheme.PRIMARY);
        btnTampil.setForeground(Color.WHITE);
        btnTampil.setFont(UITheme.FONT_BOLD);
        btnTampil.setBorderPainted(false);
        btnTampil.setFocusPainted(false);
        btnTampil.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnTampil.addActionListener(e -> loadData());
        filterPanel.add(btnTampil);

        p.add(filterPanel, BorderLayout.EAST);
        return p;
    }

    // ── Tabel ────────────────────────────────────────────────

    private JScrollPane buildTable() {
        String[] cols = {
            "No", "Tanggal", "Hari",
            "Jam Masuk", "Jam Keluar",
            "Status", "Terlambat", "Lembur", "Uang Lembur"
        };

        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        table = new JTable(tableModel);
        styleTable();

        // Renderer kolom Status (warna)
        table.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val,
                    boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                setHorizontalAlignment(SwingConstants.CENTER);
                setFont(UITheme.FONT_BOLD);
                boolean bg = row % 2 == 0;
                if ("Tepat Waktu".equals(val)) {
                    setForeground(UITheme.SUCCESS);
                    setBackground(sel ? UITheme.TABLE_SELECT : (bg ? new Color(0xF0,0xFB,0xF4) : new Color(0xE6,0xF9,0xED)));
                } else if ("Terlambat".equals(val)) {
                    setForeground(UITheme.DANGER);
                    setBackground(sel ? UITheme.TABLE_SELECT : (bg ? new Color(0xFFF5,0xF5,0xF5) : new Color(0xFEE,0xEE,0xEE)));
                } else {
                    setForeground(UITheme.TEXT_MUTED);
                    setBackground(sel ? UITheme.TABLE_SELECT : (bg ? UITheme.TABLE_ROW_ODD : UITheme.TABLE_ROW_EVEN));
                }
                return this;
            }
        });

        // Double-click → detail
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.getSelectedRow();
                    if (row >= 0) showDetail(row);
                }
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER));
        return scroll;
    }

    // ── Ringkasan bawah ──────────────────────────────────────

    private JPanel buildSummary() {
        JPanel p = new JPanel(new GridLayout(1, 3, 12, 0));
        p.setBackground(UITheme.BG_MAIN);
        p.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));

        lblTotalHadir  = makeSummaryCard("📅 Total Hadir",  "—", UITheme.SUCCESS);
        lblTotalTelat  = makeSummaryCard("⚠  Terlambat",    "—", UITheme.WARNING);
        lblTotalLembur = makeSummaryCard("⏰ Hari Lembur",   "—", UITheme.INFO);

        p.add(wrapSummary(lblTotalHadir,  "📅 Total Hadir",  UITheme.SUCCESS));
        p.add(wrapSummary(lblTotalTelat,  "⚠  Terlambat",   UITheme.WARNING));
        p.add(wrapSummary(lblTotalLembur, "⏰ Hari Lembur",  UITheme.INFO));
        return p;
    }

    private JLabel makeSummaryCard(String label, String val, Color color) {
        JLabel lbl = new JLabel(val, SwingConstants.LEFT);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lbl.setForeground(color);
        return lbl;
    }

    private JPanel wrapSummary(JLabel valLabel, String title, Color color) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, color),
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UITheme.BORDER),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)
            )
        ));
        card.add(valLabel, BorderLayout.CENTER);
        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(UITheme.FONT_SMALL);
        lblTitle.setForeground(UITheme.TEXT_SECONDARY);
        card.add(lblTitle, BorderLayout.SOUTH);
        return card;
    }

    // =========================================================
    //  LOAD DATA
    // =========================================================

    public void loadData() {
        tableModel.setRowCount(0);

        int bulan = cbBulan.getSelectedIndex() + 1;
        int tahun = (int) cbTahun.getSelectedItem();

        try {
            List<Absensi> list = absensiService.getRiwayatKaryawan(user.getIdKaryawan(), bulan, tahun);

            int no = 1;
            for (Absensi a : list) {
                String hari = a.getTanggal() != null
                    ? new java.text.SimpleDateFormat("EEEE", new java.util.Locale("id","ID"))
                        .format(a.getTanggal())
                    : "—";

                tableModel.addRow(new Object[]{
                    no++,
                    a.getTanggal() != null ? a.getTanggal().toString() : "—",
                    hari,
                    a.getJamMasuk()  != null ? a.getJamMasuk().toString()  : "—",
                    a.getJamKeluar() != null ? a.getJamKeluar().toString() : "—",
                    a.getJamMasuk()  == null ? "—" : (a.isStatusTelat() ? "Terlambat" : "Tepat Waktu"),
                    a.getDurasiTelatFormatted(),
                    a.getDurasiLemburFormatted(),
                    a.getUangLembur() != null && a.getUangLembur().compareTo(java.math.BigDecimal.ZERO) > 0
                        ? com.absensi.util.CurrencyUtil.formatRupiahBulat(a.getUangLembur()) : "—"
                });
            }

            // Update ringkasan statistik
            int[] stats = absensiService.getStatistikBulanan(user.getIdKaryawan(), bulan, tahun);
            lblTotalHadir.setText(stats[0] + " hari");
            lblTotalTelat.setText(stats[1] + " kali");
            lblTotalLembur.setText(stats[2] + " hari");

            if (list.isEmpty()) {
                tableModel.addRow(new Object[]{
                    "—", "Tidak ada data absensi untuk periode ini", "—","—","—","—","—","—","—"
                });
            }

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                "Gagal memuat riwayat absensi:\n" + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // =========================================================
    //  DETAIL ROW (double-click)
    // =========================================================

    private void showDetail(int row) {
        // Ambil data dari model
        Object tgl   = tableModel.getValueAt(row, 1);
        Object masuk = tableModel.getValueAt(row, 3);
        Object kluar = tableModel.getValueAt(row, 4);
        Object stat  = tableModel.getValueAt(row, 5);
        Object telat = tableModel.getValueAt(row, 6);
        Object lembur= tableModel.getValueAt(row, 7);
        Object uang  = tableModel.getValueAt(row, 8);

        String info = String.format(
            "Tanggal    : %s\n" +
            "Jam Masuk  : %s\n" +
            "Jam Keluar : %s\n" +
            "Status     : %s\n" +
            "Terlambat  : %s\n" +
            "Lembur     : %s\n" +
            "Uang Lembur: %s",
            tgl, masuk, kluar, stat, telat, lembur, uang
        );
        JOptionPane.showMessageDialog(this, info,
            "Detail Absensi — " + tgl, JOptionPane.INFORMATION_MESSAGE);
    }

    // =========================================================
    //  STYLE TABEL
    // =========================================================

    private void styleTable() {
        table.setFont(UITheme.FONT_BODY);
        table.setRowHeight(34);
        table.setGridColor(UITheme.BORDER);
        table.setSelectionBackground(UITheme.TABLE_SELECT);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));

        JTableHeader header = table.getTableHeader();
        header.setBackground(UITheme.TABLE_HEADER);
        header.setForeground(Color.WHITE);
        header.setFont(UITheme.FONT_BOLD);
        header.setPreferredSize(new Dimension(0, 38));
        header.setReorderingAllowed(false);

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val,
                    boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                setBackground(sel ? UITheme.TABLE_SELECT
                    : (row % 2 == 0 ? UITheme.TABLE_ROW_ODD : UITheme.TABLE_ROW_EVEN));
                setForeground(UITheme.TEXT_PRIMARY);
                setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                return this;
            }
        });

        // Lebar kolom
        int[] widths = {40, 100, 100, 90, 90, 110, 110, 110, 120};
        for (int i = 0; i < widths.length; i++)
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        table.getColumnModel().getColumn(0).setMaxWidth(45);
    }
}