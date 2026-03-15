package com.absensi.ui.employee;

import com.absensi.model.Gaji;
import com.absensi.model.Karyawan;
import com.absensi.service.GajiService;
import com.absensi.service.GajiService.RingkasanGaji;
import com.absensi.service.AuthService.ServiceResult;
import com.absensi.util.CurrencyUtil;
import com.absensi.util.SlipGajiGenerator;
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
 * Panel Riwayat Gaji Karyawan.
 *
 * Fitur:
 *  - Tabel riwayat gaji semua periode
 *  - Warna status: hijau = DIBAYAR, kuning = PENDING
 *  - Tombol "Lihat Slip Gaji" → dialog detail komponen gaji
 *  - Tombol "Cetak / Simpan PDF" → generate slip gaji PDF via SlipGajiGenerator
 *  - Kartu ringkasan total gaji terbaru di bagian atas
 */
public class RiwayatGajiPanel extends JPanel {

    private final Karyawan    user;
    private final GajiService gajiService = new GajiService();

    private DefaultTableModel tableModel;
    private JTable            table;

    // Kartu ringkasan atas
    private JLabel lblGajiPokok;
    private JLabel lblTotalLembur;
    private JLabel lblBonus;
    private JLabel lblTotalGaji;
    private JLabel lblPeriode;

    public RiwayatGajiPanel(Karyawan user) {
        this.user = user;
        initUI();
        loadData();
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
        add(buildActions(),   BorderLayout.SOUTH);
    }

    // ── Header: judul + kartu ringkasan gaji terbaru ─────────

    private JPanel buildHeader() {
        JPanel outer = new JPanel(new BorderLayout(0, 12));
        outer.setBackground(UITheme.BG_MAIN);

        JLabel lblTitle = new JLabel("💰  Riwayat & Slip Gaji");
        lblTitle.setFont(UITheme.FONT_SUBTITLE);
        lblTitle.setForeground(UITheme.PRIMARY);
        outer.add(lblTitle, BorderLayout.NORTH);

        // Kartu ringkasan 4 kolom
        JPanel cards = new JPanel(new GridLayout(1, 4, 12, 0));
        cards.setBackground(UITheme.BG_MAIN);

        lblPeriode    = new JLabel("—");
        lblGajiPokok  = new JLabel("—");
        lblTotalLembur= new JLabel("—");
        lblBonus      = new JLabel("—");
        lblTotalGaji  = new JLabel("—");

        cards.add(wrapCard("Periode Terbaru",  lblPeriode,     new Color(0x6C, 0x75, 0x7D)));
        cards.add(wrapCard("Gaji Pokok",       lblGajiPokok,   UITheme.INFO));
        cards.add(wrapCard("Lembur + Bonus",   lblTotalLembur, UITheme.SUCCESS));
        cards.add(wrapCard("Total Gaji Bersih",lblTotalGaji,   UITheme.ACCENT));

        outer.add(cards, BorderLayout.CENTER);
        return outer;
    }

    private JPanel wrapCard(String title, JLabel valLabel, Color color) {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, color),
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UITheme.BORDER),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)
            )
        ));
        valLabel.setFont(new Font("Segoe UI", Font.BOLD, 17));
        valLabel.setForeground(color);
        card.add(valLabel, BorderLayout.CENTER);
        JLabel lTitle = new JLabel(title);
        lTitle.setFont(UITheme.FONT_SMALL);
        lTitle.setForeground(UITheme.TEXT_SECONDARY);
        card.add(lTitle, BorderLayout.SOUTH);
        return card;
    }

    // ── Tabel riwayat gaji ───────────────────────────────────

    private JScrollPane buildTable() {
        String[] cols = {"Periode","Gaji Pokok","Lembur","Bonus","Potongan","Total Gaji","Status"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        table = new JTable(tableModel);
        styleTable();

        // Renderer kolom Status
        table.getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val,
                    boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                setHorizontalAlignment(SwingConstants.CENTER);
                setFont(UITheme.FONT_BOLD);
                if ("DIBAYAR".equals(val)) {
                    setForeground(UITheme.SUCCESS);
                    setBackground(sel ? UITheme.TABLE_SELECT : new Color(0xF0,0xFB,0xF4));
                } else {
                    setForeground(UITheme.WARNING);
                    setBackground(sel ? UITheme.TABLE_SELECT : new Color(0xFF,0xFB,0xF0));
                }
                return this;
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER));

        JLabel lblInfo = new JLabel("  💡 Pilih baris lalu klik 'Lihat Slip Gaji' untuk detail");
        lblInfo.setFont(UITheme.FONT_SMALL);
        lblInfo.setForeground(UITheme.TEXT_SECONDARY);

        JPanel wrapper = new JPanel(new BorderLayout(0, 4));
        wrapper.setBackground(UITheme.BG_MAIN);
        wrapper.add(scroll, BorderLayout.CENTER);
        wrapper.add(lblInfo, BorderLayout.SOUTH);

        // Kembalikan sebagai scroll pane, bungkus wrapper
        JScrollPane outerScroll = new JScrollPane(wrapper);
        outerScroll.setBorder(null);
        return scroll;
    }

    // ── Tombol aksi bawah ────────────────────────────────────

    private JPanel buildActions() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        p.setBackground(UITheme.BG_MAIN);

        JButton btnRefresh   = makeBtn("🔄  Refresh",       UITheme.TEXT_SECONDARY);
        JButton btnSlip      = makeBtn("📄  Lihat Slip Gaji", UITheme.PRIMARY);
        JButton btnPDF       = makeBtn("🖨️  Simpan PDF",     UITheme.ACCENT);

        btnRefresh.addActionListener(e -> loadData());
        btnSlip.addActionListener(e    -> doLihatSlip());
        btnPDF.addActionListener(e     -> doCetakPDF());

        p.add(btnRefresh);
        p.add(btnSlip);
        p.add(btnPDF);
        return p;
    }

    // =========================================================
    //  LOAD DATA
    // =========================================================

    public void loadData() {
        tableModel.setRowCount(0);

        try {
            List<Gaji> list = gajiService.getRiwayatGaji(user.getIdKaryawan());

            for (Gaji g : list) {
                tableModel.addRow(new Object[]{
                    g.getPeriodeLabel(),
                    CurrencyUtil.formatRupiahBulat(g.getGajiPokok()),
                    CurrencyUtil.formatRupiahBulat(g.getTotalLembur()),
                    CurrencyUtil.formatRupiahBulat(g.getBonus()),
                    CurrencyUtil.formatRupiahBulat(g.getPotongan()),
                    CurrencyUtil.formatRupiahBulat(g.getTotalGaji()),
                    g.getStatusBayar().name()
                });
            }

            // Update kartu ringkasan dengan gaji paling baru
            if (!list.isEmpty()) {
                Gaji terbaru = list.get(0);
                lblPeriode.setText(terbaru.getPeriodeLabel());
                lblGajiPokok.setText(CurrencyUtil.formatRupiahBulat(terbaru.getGajiPokok()));
                java.math.BigDecimal lemburBonus = terbaru.getTotalLembur()
                    .add(terbaru.getBonus() != null ? terbaru.getBonus() : java.math.BigDecimal.ZERO);
                lblTotalLembur.setText(CurrencyUtil.formatRupiahBulat(lemburBonus));
                lblTotalGaji.setText(CurrencyUtil.formatRupiahBulat(terbaru.getTotalGaji()));
            }

            if (list.isEmpty()) {
                tableModel.addRow(new Object[]{
                    "—","Belum ada data gaji","—","—","—","—","—"
                });
            }

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                "Gagal memuat riwayat gaji:\n" + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // =========================================================
    //  LIHAT SLIP GAJI (Dialog detail)
    // =========================================================

    private void doLihatSlip() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this,
                "Pilih periode gaji dari tabel terlebih dahulu.",
                "Perhatian", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Parse periode dari label "Januari 2025"
        String periode = (String) tableModel.getValueAt(row, 0);
        int[]  bTahun  = parsePeriode(periode);
        if (bTahun == null) return;

        try {
            ServiceResult validasi = gajiService.validasiSlipGaji(
                user.getIdKaryawan(), bTahun[0], bTahun[1]);

            if (!validasi.isSukses()) {
                JOptionPane.showMessageDialog(this, validasi.getPesan(),
                    "Slip Tidak Tersedia", JOptionPane.WARNING_MESSAGE);
                return;
            }

            RingkasanGaji rg = validasi.getData();
            showSlipDialog(rg);

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Gagal memuat data slip: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showSlipDialog(RingkasanGaji rg) {
        JDialog dialog = new JDialog(
            (Frame) SwingUtilities.getWindowAncestor(this),
            "Slip Gaji — " + rg.gaji.getPeriodeLabel(), true
        );
        dialog.setSize(480, 480);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        // Header dialog
        JLabel hdr = new JLabel("  SLIP GAJI  —  " + rg.gaji.getPeriodeLabel());
        hdr.setFont(UITheme.FONT_BOLD);
        hdr.setForeground(Color.WHITE);
        hdr.setOpaque(true);
        hdr.setBackground(UITheme.PRIMARY);
        hdr.setPreferredSize(new Dimension(0, 42));
        dialog.add(hdr, BorderLayout.NORTH);

        // Isi slip
        JTextArea txt = new JTextArea(rg.getCetakRingkasan());
        txt.setEditable(false);
        txt.setFont(new Font("Consolas", Font.PLAIN, 13));
        txt.setBackground(new Color(0xFA, 0xFA, 0xFA));
        txt.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        txt.setForeground(UITheme.TEXT_PRIMARY);
        dialog.add(new JScrollPane(txt), BorderLayout.CENTER);

        // Tombol bawah
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        btnPanel.setBackground(Color.WHITE);
        btnPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UITheme.BORDER));

        JButton btnPDF    = makeBtn("🖨️  Simpan PDF", UITheme.ACCENT);
        JButton btnTutup  = makeBtn("Tutup",           UITheme.TEXT_SECONDARY);
        btnPDF.addActionListener(e   -> { dialog.dispose(); doCetakPDFLangsung(rg); });
        btnTutup.addActionListener(e -> dialog.dispose());

        btnPanel.add(btnPDF);
        btnPanel.add(btnTutup);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    // =========================================================
    //  CETAK PDF
    // =========================================================

    private void doCetakPDF() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this,
                "Pilih periode gaji dari tabel terlebih dahulu.",
                "Perhatian", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String periode = (String) tableModel.getValueAt(row, 0);
        int[]  bTahun  = parsePeriode(periode);
        if (bTahun == null) return;

        try {
            ServiceResult validasi = gajiService.validasiSlipGaji(
                user.getIdKaryawan(), bTahun[0], bTahun[1]);
            if (!validasi.isSukses()) {
                JOptionPane.showMessageDialog(this, validasi.getPesan(), "Perhatian", JOptionPane.WARNING_MESSAGE);
                return;
            }
            doCetakPDFLangsung(validasi.getData());
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doCetakPDFLangsung(RingkasanGaji rg) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Simpan Slip Gaji PDF");
        fc.setSelectedFile(new File(
            "SlipGaji_" + rg.gaji.getIdKaryawan() + "_" + rg.gaji.getPeriodeLabel().replace(" ", "_") + ".pdf"
        ));
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PDF Files", "pdf"));

        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = fc.getSelectedFile().getAbsolutePath();
            if (!path.endsWith(".pdf")) path += ".pdf";

            boolean ok = SlipGajiGenerator.generate(rg.karyawan, rg.gaji, path);
            if (ok) {
                JOptionPane.showMessageDialog(this,
                    "✅  Slip gaji berhasil disimpan:\n" + path,
                    "PDF Tersimpan", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                    "Gagal membuat PDF. Periksa izin folder tujuan.",
                    "Gagal", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // =========================================================
    //  UTIL
    // =========================================================

    /** Parse "Januari 2025" → [1, 2025] */
    private int[] parsePeriode(String label) {
        if (label == null || label.equals("—")) return null;
        String[] BULAN = {
            "Januari","Februari","Maret","April","Mei","Juni",
            "Juli","Agustus","September","Oktober","November","Desember"
        };
        try {
            String[] parts = label.trim().split(" ");
            int bulan = -1;
            for (int i = 0; i < BULAN.length; i++)
                if (BULAN[i].equalsIgnoreCase(parts[0])) { bulan = i + 1; break; }
            int tahun = Integer.parseInt(parts[1]);
            return new int[]{bulan, tahun};
        } catch (Exception e) { return null; }
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