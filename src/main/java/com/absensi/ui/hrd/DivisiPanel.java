package com.absensi.ui.hrd;

import com.absensi.dao.DivisiDAO;
import com.absensi.model.Divisi;
import com.absensi.util.UITheme;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.SQLException;
import java.util.List;

/**
 * Panel UI untuk manajemen Divisi/Bagian kerja oleh HRD.
 *
 * Fitur:
 * - Lihat semua divisi beserta jumlah karyawan
 * - Tambah divisi baru
 * - Edit nama/deskripsi divisi
 * - Nonaktifkan divisi (soft delete)
 * - Set kepala divisi
 */
public class DivisiPanel extends JPanel {

    private final DivisiDAO divisiDAO = new DivisiDAO();
    private DefaultTableModel tableModel;
    private JTable table;

    // Form input
    private JTextField txtKode;
    private JTextField txtNama;
    private JTextArea  txtDeskripsi;
    private JCheckBox  chkAktif;

    // State
    private Divisi selectedDivisi = null;
    private boolean isEditMode    = false;

    public DivisiPanel() {
        initUI();
        loadData();
    }

    private void initUI() {
        setLayout(new BorderLayout(12, 12));
        setBackground(UITheme.BG_MAIN);
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        add(buildHeader(),    BorderLayout.NORTH);
        add(buildContent(),   BorderLayout.CENTER);
    }

    // ===================== HEADER =====================

    private JPanel buildHeader() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UITheme.BG_MAIN);

        JLabel lblTitle = new JLabel("🏢  Manajemen Divisi / Bagian Kerja");
        lblTitle.setFont(UITheme.FONT_SUBTITLE);
        lblTitle.setForeground(UITheme.PRIMARY);
        panel.add(lblTitle, BorderLayout.WEST);

        JLabel lblInfo = new JLabel("Divisi yang ditambahkan di sini akan muncul di form registrasi karyawan  ");
        lblInfo.setFont(UITheme.FONT_SMALL);
        lblInfo.setForeground(UITheme.TEXT_SECONDARY);
        panel.add(lblInfo, BorderLayout.EAST);

        return panel;
    }

    // ===================== KONTEN UTAMA =====================

    private JSplitPane buildContent() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            buildTablePanel(), buildFormPanel());
        split.setDividerLocation(620);
        split.setDividerSize(6);
        split.setBorder(null);
        split.setBackground(UITheme.BG_MAIN);
        return split;
    }

    // ===================== PANEL TABEL =====================

    private JPanel buildTablePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(UITheme.BG_CARD);
        panel.setBorder(new CompoundBorder(
            BorderFactory.createLineBorder(UITheme.BORDER),
            BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));

        // Toolbar tabel
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        toolbar.setBackground(UITheme.BG_CARD);

        JButton btnRefresh    = makeBtn("🔄 Refresh",     UITheme.TEXT_SECONDARY);
        JButton btnTambah     = makeBtn("➕ Tambah Baru", UITheme.ACCENT);
        JButton btnEdit       = makeBtn("✏️ Edit",        UITheme.INFO);
        JButton btnNonaktif   = makeBtn("🚫 Nonaktifkan", UITheme.WARNING);
        JButton btnHapus      = makeBtn("🗑️ Hapus",       UITheme.DANGER);

        toolbar.add(btnRefresh);
        toolbar.add(btnTambah);
        toolbar.add(btnEdit);
        toolbar.add(btnNonaktif);
        toolbar.add(btnHapus);
        panel.add(toolbar, BorderLayout.NORTH);

        // Tabel
        String[] cols = {"ID", "Kode", "Nama Divisi", "Deskripsi", "Karyawan", "Status"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                return c == 4 ? Integer.class : String.class;
            }
        };

        table = new JTable(tableModel);
        styleTable();

        // Kolom Status dengan color coding
        table.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, value, sel, foc, row, col);
                setHorizontalAlignment(SwingConstants.CENTER);
                setFont(UITheme.FONT_BOLD);
                if ("Aktif".equals(value)) {
                    setForeground(UITheme.SUCCESS);
                    setBackground(sel ? UITheme.TABLE_SELECT : new Color(0xE6, 0xF9, 0xF0));
                } else {
                    setForeground(UITheme.TEXT_MUTED);
                    setBackground(sel ? UITheme.TABLE_SELECT : new Color(0xF5, 0xF5, 0xF5));
                }
                return this;
            }
        });

        // Sembunyikan kolom ID (dipakai internal)
        table.getColumnModel().getColumn(0).setMinWidth(0);
        table.getColumnModel().getColumn(0).setMaxWidth(0);
        table.getColumnModel().getColumn(0).setWidth(0);

        // Saat baris dipilih, isi form
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onRowSelected();
        });

        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        // Event listeners tombol
        btnRefresh.addActionListener(e  -> loadData());
        btnTambah.addActionListener(e   -> resetForm());
        btnEdit.addActionListener(e     -> enterEditMode());
        btnNonaktif.addActionListener(e -> doNonaktifkan());
        btnHapus.addActionListener(e    -> doHapus());

        return panel;
    }

    // ===================== PANEL FORM =====================

    private JPanel buildFormPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(UITheme.BG_CARD);
        panel.setBorder(new CompoundBorder(
            BorderFactory.createLineBorder(UITheme.BORDER),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        panel.setPreferredSize(new Dimension(300, 0));

        JLabel lblFormTitle = new JLabel("Detail Divisi");
        lblFormTitle.setFont(UITheme.FONT_SUBTITLE);
        lblFormTitle.setForeground(UITheme.PRIMARY);
        panel.add(lblFormTitle, BorderLayout.NORTH);

        // Form fields
        JPanel fields = new JPanel(new GridBagLayout());
        fields.setBackground(UITheme.BG_CARD);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 0, 4, 0);

        // Kode Divisi
        gbc.gridy = 0; fields.add(makeLabel("Kode Divisi *"), gbc);
        txtKode = makeTextField("Contoh: IT, HRD, FIN");
        gbc.gridy = 1; fields.add(txtKode, gbc);

        // Nama Divisi
        gbc.gridy = 2; fields.add(makeLabel("Nama Divisi *"), gbc);
        txtNama = makeTextField("Contoh: Information Technology");
        gbc.gridy = 3; fields.add(txtNama, gbc);

        // Deskripsi
        gbc.gridy = 4; fields.add(makeLabel("Deskripsi"), gbc);
        txtDeskripsi = new JTextArea(4, 20);
        txtDeskripsi.setFont(UITheme.FONT_BODY);
        txtDeskripsi.setLineWrap(true);
        txtDeskripsi.setWrapStyleWord(true);
        txtDeskripsi.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UITheme.BORDER),
            BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));
        gbc.gridy = 5; fields.add(new JScrollPane(txtDeskripsi), gbc);

        // Status Aktif
        gbc.gridy = 6; gbc.insets = new Insets(10, 0, 4, 0);
        chkAktif = new JCheckBox("Divisi Aktif");
        chkAktif.setFont(UITheme.FONT_BODY);
        chkAktif.setBackground(UITheme.BG_CARD);
        chkAktif.setSelected(true);
        fields.add(chkAktif, gbc);

        panel.add(fields, BorderLayout.CENTER);

        // Tombol Simpan / Batal
        JPanel btnPanel = new JPanel(new GridLayout(1, 2, 8, 0));
        btnPanel.setBackground(UITheme.BG_CARD);
        JButton btnSimpan = makeBtn("💾 Simpan", UITheme.ACCENT);
        JButton btnBatal  = makeBtn("✖ Batal",   UITheme.TEXT_SECONDARY);
        btnSimpan.setPreferredSize(new Dimension(0, 38));
        btnBatal.setPreferredSize(new Dimension(0, 38));
        btnPanel.add(btnSimpan);
        btnPanel.add(btnBatal);
        panel.add(btnPanel, BorderLayout.SOUTH);

        btnSimpan.addActionListener(e -> doSimpan());
        btnBatal.addActionListener(e  -> resetForm());

        return panel;
    }

    // ===================== AKSI =====================

    /** Load ulang semua data divisi dari database ke tabel. */
    private void loadData() {
        tableModel.setRowCount(0);
        try {
            List<Divisi> list = divisiDAO.findAll();
            for (Divisi d : list) {
                tableModel.addRow(new Object[]{
                    d.getId(),
                    d.getKodeDivisi(),
                    d.getNamaDivisi(),
                    d.getDeskripsi() != null ? d.getDeskripsi() : "",
                    d.getJumlahKaryawan(),
                    d.isAktif() ? "Aktif" : "Nonaktif"
                });
            }
        } catch (SQLException e) {
            showError("Gagal memuat data divisi: " + e.getMessage());
        }
    }

    /** Isi form dari baris yang dipilih di tabel. */
    private void onRowSelected() {
        int row = table.getSelectedRow();
        if (row < 0) { selectedDivisi = null; return; }

        int id = (int) tableModel.getValueAt(row, 0);
        try {
            selectedDivisi = divisiDAO.findById(id);
            if (selectedDivisi != null) {
                txtKode.setText(selectedDivisi.getKodeDivisi());
                txtNama.setText(selectedDivisi.getNamaDivisi());
                txtDeskripsi.setText(selectedDivisi.getDeskripsi() != null ? selectedDivisi.getDeskripsi() : "");
                chkAktif.setSelected(selectedDivisi.isAktif());
                // Mode view (read-only) sampai klik tombol Edit
                setFormEditable(false);
                isEditMode = false;
            }
        } catch (SQLException e) {
            showError("Gagal memuat detail divisi: " + e.getMessage());
        }
    }

    /** Aktifkan mode edit form. */
    private void enterEditMode() {
        if (selectedDivisi == null) {
            showWarning("Pilih divisi yang ingin diedit terlebih dahulu.");
            return;
        }
        isEditMode = true;
        setFormEditable(true);
        // Kode divisi tidak boleh diubah jika sudah ada karyawan
        try {
            txtKode.setEditable(divisiDAO.countKaryawanByKode(selectedDivisi.getKodeDivisi()) == 0);
        } catch (SQLException ignored) {
            txtKode.setEditable(false);
        }
        txtNama.requestFocus();
    }

    /** Reset form ke keadaan kosong (mode tambah baru). */
    private void resetForm() {
        selectedDivisi = null;
        isEditMode     = true;
        txtKode.setText("");
        txtNama.setText("");
        txtDeskripsi.setText("");
        chkAktif.setSelected(true);
        setFormEditable(true);
        txtKode.requestFocus();
        table.clearSelection();
    }

    private void setFormEditable(boolean editable) {
        txtKode.setEditable(editable);
        txtNama.setEditable(editable);
        txtDeskripsi.setEditable(editable);
        chkAktif.setEnabled(editable);
    }

    /** Simpan data divisi (insert atau update). */
    private void doSimpan() {
        String kode  = txtKode.getText().trim().toUpperCase();
        String nama  = txtNama.getText().trim();
        String desk  = txtDeskripsi.getText().trim();
        boolean aktif = chkAktif.isSelected();

        // Validasi
        if (kode.isEmpty()) { showWarning("Kode divisi wajib diisi."); txtKode.requestFocus(); return; }
        if (nama.isEmpty()) { showWarning("Nama divisi wajib diisi."); txtNama.requestFocus(); return; }
        if (kode.length() > 20) { showWarning("Kode divisi maksimal 20 karakter."); return; }

        try {
            if (selectedDivisi == null) {
                // MODE TAMBAH BARU
                if (divisiDAO.isKodeExist(kode)) {
                    showWarning("Kode divisi '" + kode + "' sudah digunakan.");
                    return;
                }
                Divisi baru = new Divisi(kode, nama, desk);
                baru.setAktif(aktif);
                divisiDAO.insert(baru);
                showSuccess("Divisi '" + nama + "' berhasil ditambahkan.");
            } else {
                // MODE EDIT
                if (divisiDAO.isKodeExistExclude(kode, selectedDivisi.getId())) {
                    showWarning("Kode divisi '" + kode + "' sudah digunakan oleh divisi lain.");
                    return;
                }
                selectedDivisi.setKodeDivisi(kode);
                selectedDivisi.setNamaDivisi(nama);
                selectedDivisi.setDeskripsi(desk);
                selectedDivisi.setAktif(aktif);
                divisiDAO.update(selectedDivisi);
                showSuccess("Divisi '" + nama + "' berhasil diperbarui.");
            }

            loadData();
            resetForm();

        } catch (SQLException e) {
            showError("Gagal menyimpan divisi: " + e.getMessage());
        }
    }

    /** Nonaktifkan divisi yang dipilih. */
    private void doNonaktifkan() {
        if (selectedDivisi == null) { showWarning("Pilih divisi terlebih dahulu."); return; }
        if (!selectedDivisi.isAktif()) { showWarning("Divisi sudah dalam keadaan nonaktif."); return; }

        int confirm = JOptionPane.showConfirmDialog(this,
            "Nonaktifkan divisi '" + selectedDivisi.getNamaDivisi() + "'?\n" +
            "Divisi tidak akan muncul di form registrasi baru,\nnamun data karyawan tidak terhapus.",
            "Konfirmasi Nonaktifkan", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                divisiDAO.nonaktifkan(selectedDivisi.getId());
                showSuccess("Divisi berhasil dinonaktifkan.");
                loadData();
                resetForm();
            } catch (SQLException e) { showError(e.getMessage()); }
        }
    }

    /** Hapus permanen divisi (hanya jika tidak ada karyawan). */
    private void doHapus() {
        if (selectedDivisi == null) { showWarning("Pilih divisi terlebih dahulu."); return; }

        int confirm = JOptionPane.showConfirmDialog(this,
            "Hapus permanen divisi '" + selectedDivisi.getNamaDivisi() + "'?\n" +
            "⚠️  Tindakan ini tidak dapat dibatalkan!\n" +
            "Divisi hanya bisa dihapus jika tidak ada karyawan yang terdaftar.",
            "Konfirmasi Hapus", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                divisiDAO.delete(selectedDivisi.getId());
                showSuccess("Divisi berhasil dihapus.");
                loadData();
                resetForm();
            } catch (SQLException e) {
                showError(e.getMessage()); // Pesan error dari DAO sudah jelas
            }
        }
    }

    // ===================== UI HELPERS =====================

    private void styleTable() {
        table.setFont(UITheme.FONT_BODY);
        table.setRowHeight(36);
        table.setGridColor(UITheme.BORDER);
        table.setSelectionBackground(UITheme.TABLE_SELECT);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));

        JTableHeader header = table.getTableHeader();
        header.setBackground(UITheme.TABLE_HEADER);
        header.setForeground(Color.WHITE);
        header.setFont(UITheme.FONT_BOLD);
        header.setPreferredSize(new Dimension(0, 40));
        header.setReorderingAllowed(false);

        // Striped rows default renderer
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, value, sel, foc, row, col);
                setBackground(sel ? UITheme.TABLE_SELECT
                    : (row % 2 == 0 ? UITheme.TABLE_ROW_ODD : UITheme.TABLE_ROW_EVEN));
                setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                return this;
            }
        });

        // Lebar kolom
        table.getColumnModel().getColumn(1).setPreferredWidth(70);   // Kode
        table.getColumnModel().getColumn(2).setPreferredWidth(180);  // Nama
        table.getColumnModel().getColumn(3).setPreferredWidth(260);  // Deskripsi
        table.getColumnModel().getColumn(4).setPreferredWidth(80);   // Karyawan
        table.getColumnModel().getColumn(5).setPreferredWidth(80);   // Status
    }

    private JButton makeBtn(String text, Color color) {
        JButton btn = new JButton(text);
        btn.setBackground(color);
        btn.setForeground(Color.WHITE);
        btn.setFont(UITheme.FONT_SMALL);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JLabel makeLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lbl.setForeground(UITheme.TEXT_PRIMARY);
        return lbl;
    }

    private JTextField makeTextField(String placeholder) {
        JTextField tf = new JTextField();
        tf.setFont(UITheme.FONT_BODY);
        tf.putClientProperty("JTextField.placeholderText", placeholder);
        tf.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UITheme.BORDER),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        return tf;
    }

    private void showSuccess(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Sukses", JOptionPane.INFORMATION_MESSAGE);
    }
    private void showWarning(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Peringatan", JOptionPane.WARNING_MESSAGE);
    }
    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }
}