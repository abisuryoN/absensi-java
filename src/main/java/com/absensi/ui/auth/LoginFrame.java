package com.absensi.ui.auth;

import com.absensi.dao.DivisiDAO;
import com.absensi.model.Divisi;
import com.absensi.model.Karyawan;
import com.absensi.service.AuthService;
import com.absensi.service.AuthService.ServiceResult;
import com.absensi.ui.components.WebcamCaptureDialog;
import com.absensi.ui.employee.EmployeeDashboard;
import com.absensi.ui.hrd.HRDDashboard;
import com.absensi.util.UITheme;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.SQLException;

/**
 * Frame utama untuk Login dan Registrasi pengguna.
 */
public class LoginFrame extends JFrame {

    private final AuthService authService = new AuthService();
    private final DivisiDAO   divisiDAO   = new DivisiDAO();

    // Panel cards
    private JPanel cardPanel;
    private CardLayout cardLayout;

    // Login components
    private JTextField txtNama;
    private JPasswordField txtPassword;

    // Register components
    private JTextField       txtRegNama;
    private JTextField       txtRegIdKaryawan;
    private JComboBox<Divisi> cbRegBagian;   // Objek Divisi, bukan String statis
    private JLabel           lblFotoPreview;
    private byte[]           fotoData;

    /** Load daftar divisi aktif dari database untuk ComboBox */
    private Divisi[] loadDivisiOptions() {
        try {
            java.util.List<Divisi> list = divisiDAO.findAllAktif();
            // Tambahkan placeholder di depan
            Divisi placeholder = new Divisi();
            placeholder.setNamaDivisi("-- Pilih Divisi --");
            placeholder.setKodeDivisi("");
            list.add(0, placeholder);
            return list.toArray(new Divisi[0]);
        } catch (java.sql.SQLException e) {
            System.err.println("[LoginFrame] Gagal load divisi: " + e.getMessage());
            // Fallback: divisi default jika database belum siap
            return new Divisi[]{
                buildDivisi("", "-- Pilih Divisi --"),
                buildDivisi("HRD",        "Human Resources"),
                buildDivisi("IT",         "Information Technology"),
                buildDivisi("KEUANGAN",   "Keuangan"),
                buildDivisi("MARKETING",  "Marketing"),
                buildDivisi("OPERASIONAL","Operasional"),
                buildDivisi("PRODUKSI",   "Produksi"),
                buildDivisi("LEGAL",      "Legal & Compliance"),
                buildDivisi("LOGISTIK",   "Logistik")
            };
        }
    }

    private Divisi buildDivisi(String kode, String nama) {
        Divisi d = new Divisi();
        d.setKodeDivisi(kode);
        d.setNamaDivisi(nama);
        return d;
    }

    public LoginFrame() {
        initUI();
    }

    private void initUI() {
        setTitle("Sistem Absensi Karyawan");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(950, 600);
        setLocationRelativeTo(null);
        setResizable(false);

        // Layout utama: split kiri (branding) + kanan (form)
        setLayout(new BorderLayout());
        add(createBrandingPanel(), BorderLayout.WEST);
        add(createFormPanel(), BorderLayout.CENTER);
    }

    // ===================== PANEL KIRI: BRANDING =====================

    private JPanel createBrandingPanel() {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                // Gradient background
                GradientPaint gp = new GradientPaint(
                    0, 0, UITheme.PRIMARY,
                    0, getHeight(), new Color(0x00C9A7)
                );
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        panel.setPreferredSize(new Dimension(380, 600));
        panel.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(8, 30, 8, 30);

        // Icon/Logo
        JLabel lblIcon = new JLabel("🏢", SwingConstants.CENTER);
        lblIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 64));
        gbc.gridy = 0;
        panel.add(lblIcon, gbc);

        // Judul
        JLabel lblTitle = new JLabel("ABSENSI", SwingConstants.CENTER);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 32));
        lblTitle.setForeground(Color.WHITE);
        gbc.gridy = 1;
        panel.add(lblTitle, gbc);

        // Subtitle
        JLabel lblSubtitle = new JLabel("SISTEM MANAJEMEN KARYAWAN", SwingConstants.CENTER);
        lblSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSubtitle.setForeground(new Color(255, 255, 255, 180));
        gbc.gridy = 2;
        panel.add(lblSubtitle, gbc);

        // Separator
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(255, 255, 255, 80));
        gbc.gridy = 3;
        gbc.insets = new Insets(20, 50, 20, 50);
        panel.add(sep, gbc);

        // Fitur-fitur
        String[] fitur = {"✔  Absensi Real-time", "✔  Monitoring Kehadiran", "✔  Pengelolaan Gaji", "✔  Laporan Otomatis"};
        for (int i = 0; i < fitur.length; i++) {
            JLabel lblFitur = new JLabel(fitur[i]);
            lblFitur.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            lblFitur.setForeground(new Color(255, 255, 255, 200));
            gbc.gridy = 4 + i;
            gbc.insets = new Insets(4, 50, 4, 30);
            panel.add(lblFitur, gbc);
        }

        return panel;
    }

    // ===================== PANEL KANAN: FORM =====================

    private JPanel createFormPanel() {
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.setBackground(UITheme.BG_MAIN);
        cardPanel.add(createLoginCard(), "login");
        cardPanel.add(createRegisterCard(), "register");
        return cardPanel;
    }

    // ==================== KARTU LOGIN ====================

    private JPanel createLoginCard() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(UITheme.BG_MAIN);
        card.setBorder(BorderFactory.createEmptyBorder(30, 50, 30, 50));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 0, 6, 0);

        // Judul
        JLabel lblTitle = new JLabel("Selamat Datang");
        lblTitle.setFont(UITheme.FONT_TITLE);
        lblTitle.setForeground(UITheme.PRIMARY);
        gbc.gridy = 0;
        card.add(lblTitle, gbc);

        JLabel lblSub = new JLabel("Silakan login untuk melanjutkan");
        lblSub.setFont(UITheme.FONT_SMALL);
        lblSub.setForeground(UITheme.TEXT_SECONDARY);
        gbc.gridy = 1;
        gbc.insets = new Insets(0, 0, 20, 0);
        card.add(lblSub, gbc);

        // Field Nama
        gbc.insets = new Insets(6, 0, 6, 0);
        gbc.gridy = 2;
        card.add(createLabel("Nama Lengkap (Username)"), gbc);
        txtNama = createTextField("Masukkan nama lengkap Anda");
        gbc.gridy = 3;
        card.add(txtNama, gbc);

        // Field Password
        gbc.gridy = 4;
        card.add(createLabel("ID Karyawan (Password)"), gbc);
        txtPassword = new JPasswordField();
        styleTextField(txtPassword);
        txtPassword.addActionListener(e -> doLogin());
        gbc.gridy = 5;
        card.add(txtPassword, gbc);

        // Tombol Login
        JButton btnLogin = createPrimaryButton("MASUK");
        btnLogin.addActionListener(e -> doLogin());
        gbc.gridy = 6;
        gbc.insets = new Insets(20, 0, 10, 0);
        card.add(btnLogin, gbc);

        // Link ke registrasi
        JPanel pnlReg = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        pnlReg.setBackground(UITheme.BG_MAIN);
        pnlReg.add(new JLabel("Belum punya akun?"));
        JButton btnToRegister = createLinkButton("Daftar di sini");
        btnToRegister.addActionListener(e -> cardLayout.show(cardPanel, "register"));
        pnlReg.add(btnToRegister);
        gbc.gridy = 7;
        gbc.insets = new Insets(0, 0, 0, 0);
        card.add(pnlReg, gbc);

        return card;
    }

    // ==================== KARTU REGISTRASI ====================

    private JPanel createRegisterCard() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(UITheme.BG_MAIN);
        card.setBorder(BorderFactory.createEmptyBorder(20, 50, 20, 50));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 0, 5, 0);

        JLabel lblTitle = new JLabel("Registrasi Akun");
        lblTitle.setFont(UITheme.FONT_TITLE);
        lblTitle.setForeground(UITheme.PRIMARY);
        gbc.gridy = 0;
        card.add(lblTitle, gbc);

        JLabel lblSub = new JLabel("Isi data diri Anda untuk mendaftar");
        lblSub.setFont(UITheme.FONT_SMALL);
        lblSub.setForeground(UITheme.TEXT_SECONDARY);
        gbc.gridy = 1;
        gbc.insets = new Insets(0, 0, 15, 0);
        card.add(lblSub, gbc);

        gbc.insets = new Insets(5, 0, 5, 0);

        // Nama
        gbc.gridy = 2;
        card.add(createLabel("Nama Lengkap"), gbc);
        txtRegNama = createTextField("Nama lengkap sesuai identitas");
        gbc.gridy = 3;
        card.add(txtRegNama, gbc);

        // ID Karyawan
        gbc.gridy = 4;
        card.add(createLabel("ID Karyawan"), gbc);
        txtRegIdKaryawan = createTextField("Contoh: EMP001");
        gbc.gridy = 5;
        card.add(txtRegIdKaryawan, gbc);

        // Bagian
        gbc.gridy = 6;
        card.add(createLabel("Divisi / Bagian Kerja"), gbc);
        cbRegBagian = new JComboBox<>(loadDivisiOptions());
        cbRegBagian.setFont(UITheme.FONT_BODY);
        cbRegBagian.setPreferredSize(new Dimension(300, UITheme.BTN_HEIGHT));
        cbRegBagian.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean hasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, hasFocus);
                if (value instanceof Divisi) {
                    Divisi d = (Divisi) value;
                    // Tampilkan nama divisi lengkap; placeholder ditampilkan apa adanya
                    setText(d.getNamaDivisi() != null ? d.getNamaDivisi() : "");
                }
                return this;
            }
        });
        gbc.gridy = 7;
        card.add(cbRegBagian, gbc);

        // Foto Selfie
        gbc.gridy = 8;
        card.add(createLabel("Foto Selfie"), gbc);

        JPanel fotoPanel = new JPanel(new BorderLayout(8, 0));
        fotoPanel.setBackground(UITheme.BG_MAIN);
        lblFotoPreview = new JLabel("Belum ada foto", SwingConstants.CENTER);
        lblFotoPreview.setFont(UITheme.FONT_SMALL);
        lblFotoPreview.setForeground(UITheme.TEXT_SECONDARY);
        lblFotoPreview.setPreferredSize(new Dimension(80, 80));
        lblFotoPreview.setBorder(BorderFactory.createLineBorder(UITheme.BORDER));
        lblFotoPreview.setOpaque(true);
        lblFotoPreview.setBackground(Color.WHITE);

        JButton btnFoto = createSecondaryButton("📷 Ambil Foto");
        btnFoto.addActionListener(e -> ambilFotoSelfie());
        fotoPanel.add(lblFotoPreview, BorderLayout.WEST);
        fotoPanel.add(btnFoto, BorderLayout.CENTER);
        gbc.gridy = 9;
        card.add(fotoPanel, gbc);

        // Tombol Daftar
        JButton btnDaftar = createPrimaryButton("DAFTAR");
        btnDaftar.addActionListener(e -> doRegistrasi());
        gbc.gridy = 10;
        gbc.insets = new Insets(15, 0, 5, 0);
        card.add(btnDaftar, gbc);

        // Link ke login
        JPanel pnlLogin = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        pnlLogin.setBackground(UITheme.BG_MAIN);
        pnlLogin.add(new JLabel("Sudah punya akun?"));
        JButton btnToLogin = createLinkButton("Login di sini");
        btnToLogin.addActionListener(e -> cardLayout.show(cardPanel, "login"));
        pnlLogin.add(btnToLogin);
        gbc.gridy = 11;
        gbc.insets = new Insets(0, 0, 0, 0);
        card.add(pnlLogin, gbc);

        return card;
    }

    // ===================== AKSI LOGIN =====================

    private void doLogin() {
        String nama     = txtNama.getText().trim();
        String password = new String(txtPassword.getPassword()).trim();

        // Validasi kosong dilakukan di AuthService, tapi tampilkan feedback cepat
        if (nama.isEmpty() || password.isEmpty()) {
            showError("Nama dan ID Karyawan tidak boleh kosong.");
            return;
        }

        // Delegasikan seluruh logika ke AuthService
        ServiceResult result = authService.login(nama, password);

        if (!result.isSukses()) {
            showError(result.getPesan());
            return;
        }

        // Login berhasil — AuthService mengembalikan objek Karyawan sebagai data
        Karyawan karyawan = result.getData();
        dispose();
        if (karyawan.isHRD()) {
            new HRDDashboard(karyawan).setVisible(true);
        } else {
            new EmployeeDashboard(karyawan).setVisible(true);
        }
    }

    // ===================== AKSI REGISTRASI =====================

    private void doRegistrasi() {
        String nama       = txtRegNama.getText().trim();
        String idKaryawan = txtRegIdKaryawan.getText().trim().toUpperCase();
        Divisi divisiDipilih = (Divisi) cbRegBagian.getSelectedItem();

        // Validasi input
        if (nama.isEmpty() || idKaryawan.isEmpty()) {
            showError("Nama dan ID Karyawan wajib diisi.");
            return;
        }
        if (divisiDipilih == null || divisiDipilih.getKodeDivisi() == null
                || divisiDipilih.getKodeDivisi().isEmpty()) {
            showError("Silakan pilih divisi/bagian kerja Anda.");
            return;
        }

        String bagian = divisiDipilih.getNamaDivisi();

        // Delegasikan seluruh logika validasi dan penyimpanan ke AuthService
        ServiceResult result = authService.registrasi(idKaryawan, nama, bagian, fotoData);

        if (!result.isSukses()) {
            showError(result.getPesan());
            return;
        }

        JOptionPane.showMessageDialog(this,
            result.getPesan(), "Registrasi Berhasil", JOptionPane.INFORMATION_MESSAGE);
        resetRegisterForm();
        cardLayout.show(cardPanel, "login");
    }

    private void ambilFotoSelfie() {
        WebcamCaptureDialog dialog = new WebcamCaptureDialog(this, "Foto Selfie Registrasi");
        dialog.setVisible(true);

        if (dialog.isConfirmed() && dialog.getCapturedImageBytes() != null) {
            fotoData = dialog.getCapturedImageBytes();
            // Tampilkan thumbnail preview
            ImageIcon icon = new ImageIcon(dialog.getCapturedImage().getScaledInstance(80, 80, java.awt.Image.SCALE_SMOOTH));
            lblFotoPreview.setIcon(icon);
            lblFotoPreview.setText("");
        }
    }

    private void resetRegisterForm() {
        txtRegNama.setText("");
        txtRegIdKaryawan.setText("");
        cbRegBagian.setSelectedIndex(0);
        lblFotoPreview.setIcon(null);
        lblFotoPreview.setText("Belum ada foto");
        fotoData = null;
    }

    // ===================== UI HELPER METHODS =====================

    private JLabel createLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lbl.setForeground(UITheme.TEXT_PRIMARY);
        return lbl;
    }

    private JTextField createTextField(String placeholder) {
        JTextField tf = new JTextField();
        styleTextField(tf);
        tf.putClientProperty("JTextField.placeholderText", placeholder);
        return tf;
    }

    private void styleTextField(JComponent tf) {
        tf.setFont(UITheme.FONT_BODY);
        tf.setPreferredSize(new Dimension(300, UITheme.BTN_HEIGHT));
        tf.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UITheme.BORDER),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
    }

    private JButton createPrimaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setBackground(UITheme.PRIMARY);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(300, UITheme.BTN_HEIGHT));
        return btn;
    }

    private JButton createSecondaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setBackground(UITheme.ACCENT);
        btn.setForeground(Color.WHITE);
        btn.setFont(UITheme.FONT_BOLD);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JButton createLinkButton(String text) {
        JButton btn = new JButton(text);
        btn.setBackground(UITheme.BG_MAIN);
        btn.setForeground(UITheme.ACCENT);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void showWarning(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Perhatian", JOptionPane.WARNING_MESSAGE);
    }
}