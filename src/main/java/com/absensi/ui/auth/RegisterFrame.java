package com.absensi.ui.auth;

import com.absensi.model.Karyawan;
import com.absensi.service.AuthService;
import com.absensi.service.AuthService.ServiceResult;
import com.absensi.ui.employee.EmployeeDashboard;
import com.absensi.ui.hrd.HRDDashboard;
import com.absensi.util.UITheme;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Frame Login — hanya menangani autentikasi pengguna.
 *
 * Alur navigasi:
 *   LoginFrame  ──login berhasil──►  EmployeeDashboard  (role KARYAWAN)
 *                                 ►  HRDDashboard        (role HRD)
 *   LoginFrame  ──"Daftar"──────►  RegisterFrame  ──selesai──► LoginFrame
 *
 * LoginFrame TIDAK lagi memuat form registrasi secara internal.
 * Registrasi dipindah sepenuhnya ke RegisterFrame.java.
 */
public class RegisterFrame extends JFrame {

    private final AuthService authService = new AuthService();

    private JTextField     txtNama;
    private JPasswordField txtPassword;
    private JButton        btnLogin;
    private JLabel         lblError;

    public RegisterFrame() {
        initUI();
    }

    // =========================================================
    //  INISIALISASI UI
    // =========================================================

    private void initUI() {
        setTitle("Sistem Absensi Karyawan — Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(860, 540);
        setLocationRelativeTo(null);
        setResizable(false);
        setLayout(new BorderLayout());

        add(buildBrandingPanel(), BorderLayout.WEST);
        add(buildLoginPanel(),    BorderLayout.CENTER);
    }

    // =========================================================
    //  PANEL KIRI — Branding
    // =========================================================

    private JPanel buildBrandingPanel() {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(
                    0, 0,           UITheme.PRIMARY,
                    0, getHeight(), new Color(0x00, 0x8C, 0x75)
                );
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        panel.setPreferredSize(new Dimension(360, 0));
        panel.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx  = 0;
        gbc.fill   = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(8, 35, 8, 35);

        // Ikon
        JLabel lblIcon = new JLabel("🏢", SwingConstants.CENTER);
        lblIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 60));
        gbc.gridy = 0; panel.add(lblIcon, gbc);

        // Judul besar
        JLabel lblTitle = new JLabel("ABSENSI", SwingConstants.CENTER);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 34));
        lblTitle.setForeground(Color.WHITE);
        gbc.gridy = 1; panel.add(lblTitle, gbc);

        // Sub-judul
        JLabel lblSub = new JLabel("SISTEM MANAJEMEN KARYAWAN", SwingConstants.CENTER);
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblSub.setForeground(new Color(255, 255, 255, 170));
        gbc.gridy = 2; panel.add(lblSub, gbc);

        // Separator
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(255, 255, 255, 60));
        gbc.gridy = 3; gbc.insets = new Insets(20, 55, 20, 55); panel.add(sep, gbc);
        gbc.insets = new Insets(5, 50, 5, 35);

        // Fitur-fitur
        String[] fitur = {
            "✔  Absensi Masuk & Keluar Real-time",
            "✔  Validasi Lokasi Kantor (Geofencing)",
            "✔  Penghitungan Lembur Otomatis",
            "✔  Manajemen Gaji & Slip Gaji",
            "✔  Laporan Kehadiran & Keterlambatan",
        };
        for (int i = 0; i < fitur.length; i++) {
            JLabel lbl = new JLabel(fitur[i]);
            lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            lbl.setForeground(new Color(255, 255, 255, 195));
            gbc.gridy = 4 + i; panel.add(lbl, gbc);
        }

        return panel;
    }

    // =========================================================
    //  PANEL KANAN — Form Login
    // =========================================================

    private JPanel buildLoginPanel() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBackground(UITheme.BG_MAIN);

        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UITheme.BORDER),
            BorderFactory.createEmptyBorder(35, 40, 35, 40)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx  = 0;
        gbc.fill   = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 0, 5, 0);

        // Judul
        JLabel lblTitle = new JLabel("Selamat Datang 👋");
        lblTitle.setFont(UITheme.FONT_TITLE);
        lblTitle.setForeground(UITheme.PRIMARY);
        gbc.gridy = 0; card.add(lblTitle, gbc);

        JLabel lblSub = new JLabel("Masuk ke akun Anda untuk melanjutkan");
        lblSub.setFont(UITheme.FONT_SMALL);
        lblSub.setForeground(UITheme.TEXT_SECONDARY);
        gbc.gridy = 1; gbc.insets = new Insets(0, 0, 22, 0);
        card.add(lblSub, gbc);
        gbc.insets = new Insets(5, 0, 5, 0);

        // Field: Nama
        gbc.gridy = 2; card.add(buildLabel("Nama Lengkap  (Username)"), gbc);
        txtNama = buildTextField("Masukkan nama lengkap Anda");
        txtNama.addActionListener(e -> doLogin());
        gbc.gridy = 3; card.add(txtNama, gbc);

        // Field: Password
        gbc.gridy = 4; card.add(buildLabel("ID Karyawan  (Password)"), gbc);
        txtPassword = new JPasswordField();
        txtPassword.setFont(UITheme.FONT_BODY);
        txtPassword.setPreferredSize(new Dimension(310, UITheme.BTN_HEIGHT));
        txtPassword.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UITheme.BORDER),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        txtPassword.addActionListener(e -> doLogin());
        gbc.gridy = 5; card.add(txtPassword, gbc);

        // Label error (tersembunyi saat tidak ada error)
        lblError = new JLabel(" ");
        lblError.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblError.setForeground(UITheme.DANGER);
        gbc.gridy = 6; card.add(lblError, gbc);

        // Tombol Login
        btnLogin = new JButton("MASUK →");
        btnLogin.setBackground(UITheme.PRIMARY);
        btnLogin.setForeground(Color.WHITE);
        btnLogin.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnLogin.setBorderPainted(false);
        btnLogin.setFocusPainted(false);
        btnLogin.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnLogin.setPreferredSize(new Dimension(310, 42));
        btnLogin.addActionListener(e -> doLogin());

        // Hover effect
        btnLogin.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btnLogin.setBackground(UITheme.PRIMARY_LIGHT); }
            @Override public void mouseExited(MouseEvent e)  { btnLogin.setBackground(UITheme.PRIMARY); }
        });

        gbc.gridy = 7; gbc.insets = new Insets(14, 0, 10, 0);
        card.add(btnLogin, gbc);

        // Separator + link registrasi
        JSeparator sepLine = new JSeparator();
        gbc.gridy = 8; gbc.insets = new Insets(5, 0, 10, 0);
        card.add(sepLine, gbc);

        JPanel linkPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        linkPanel.setBackground(Color.WHITE);
        JLabel lblBelum = new JLabel("Belum punya akun?");
        lblBelum.setFont(UITheme.FONT_SMALL);
        lblBelum.setForeground(UITheme.TEXT_SECONDARY);

        JButton btnDaftar = new JButton("Daftar di sini");
        btnDaftar.setBackground(Color.WHITE);
        btnDaftar.setForeground(UITheme.ACCENT);
        btnDaftar.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnDaftar.setBorderPainted(false);
        btnDaftar.setFocusPainted(false);
        btnDaftar.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnDaftar.addActionListener(e -> bukaRegisterFrame());

        linkPanel.add(lblBelum);
        linkPanel.add(btnDaftar);
        gbc.gridy = 9; gbc.insets = new Insets(0, 0, 0, 0);
        card.add(linkPanel, gbc);

        // Tempatkan card di tengah outer panel
        outer.add(card);
        return outer;
    }

    // =========================================================
    //  AKSI: LOGIN
    // =========================================================

    private void doLogin() {
        String nama     = txtNama.getText().trim();
        String password = new String(txtPassword.getPassword()).trim();

        if (nama.isEmpty() || password.isEmpty()) {
            setError("Nama dan ID Karyawan tidak boleh kosong.");
            return;
        }

        // Disable tombol saat proses berlangsung
        btnLogin.setEnabled(false);
        btnLogin.setText("Memeriksa...");
        clearError();

        // Jalankan di background thread agar UI tidak freeze
        new SwingWorker<ServiceResult, Void>() {
            @Override
            protected ServiceResult doInBackground() {
                return authService.login(nama, password);
            }

            @Override
            protected void done() {
                btnLogin.setEnabled(true);
                btnLogin.setText("MASUK →");
                try {
                    ServiceResult result = get();
                    if (!result.isSukses()) {
                        setError(result.getPesan());
                        txtPassword.setText("");
                        txtPassword.requestFocus();
                    } else {
                        Karyawan karyawan = result.getData();
                        onLoginBerhasil(karyawan);
                    }
                } catch (Exception ex) {
                    setError("Kesalahan sistem: " + ex.getMessage());
                }
            }
        }.execute();
    }

    /** Dipanggil saat login sukses — buka dashboard sesuai role. */
    private void onLoginBerhasil(Karyawan karyawan) {
        setVisible(false);      // sembunyikan dulu, jangan langsung dispose
        if (karyawan.isHRD()) {
            new HRDDashboard(karyawan).setVisible(true);
        } else {
            new EmployeeDashboard(karyawan).setVisible(true);
        }
        dispose();
    }

    // =========================================================
    //  AKSI: BUKA REGISTER FRAME
    // =========================================================

    private void bukaRegister() {
    setVisible(false);
    new RegisterFrame().setVisible(true);
    }

    // =========================================================
    //  HELPER UI
    // =========================================================

    private JLabel buildLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lbl.setForeground(UITheme.TEXT_PRIMARY);
        return lbl;
    }

    private JTextField buildTextField(String placeholder) {
        JTextField tf = new JTextField();
        tf.setFont(UITheme.FONT_BODY);
        tf.setPreferredSize(new Dimension(310, UITheme.BTN_HEIGHT));
        tf.putClientProperty("JTextField.placeholderText", placeholder);
        tf.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UITheme.BORDER),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        return tf;
    }

    private void setError(String msg) {
        lblError.setText("⚠  " + msg.replace("\n", "  "));
    }

    private void clearError() {
        lblError.setText(" ");
    }
    
    private void bukaRegisterFrame() {
    new RegisterFrame().setVisible(true);
    dispose();
}
}
