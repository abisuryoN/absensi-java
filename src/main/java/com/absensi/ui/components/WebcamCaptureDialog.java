package com.absensi.ui.components;

import com.absensi.util.FaceDetectionUtil;
import com.absensi.util.ImageValidationUtil;
import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class WebcamCaptureDialog extends JDialog {

    private final boolean isRegistrasi;

    private Webcam        webcam;
    private WebcamPanel   webcamPanel;
    private JButton       btnCapture;
    private JButton       btnRetake;
    private JButton       btnConfirm;
    private JLabel        lblPreview;
    private JLabel        lblValidasi;
    private JPanel        pnlCamera;
    private JPanel        pnlPreview;

    private BufferedImage capturedImage;
    private byte[]        capturedImageBytes;
    private boolean       confirmed = false;
    private boolean       fotoValid = false;

    public WebcamCaptureDialog(Frame parent, String title) {
        this(parent, title, false);
    }

    public WebcamCaptureDialog(Frame parent, String title, boolean isRegistrasi) {
        super(parent, title, true);
        this.isRegistrasi = isRegistrasi;
        initComponents();
        initWebcam();
        pack();
        setLocationRelativeTo(parent);
        setResizable(false);
    }

    // ── Init UI ───────────────────────────────────────────────

    private void initComponents() {
        setLayout(new BorderLayout(8, 8));
        getContentPane().setBackground(new Color(0x1B2A4A));

        JPanel topPanel = new JPanel(new BorderLayout(0, 2));
        topPanel.setBackground(new Color(0x1B2A4A));

        JLabel lblTitle = new JLabel(
            isRegistrasi ? "Foto Selfie Registrasi" : getTitle(),
            SwingConstants.CENTER);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblTitle.setForeground(Color.WHITE);
        lblTitle.setBorder(BorderFactory.createEmptyBorder(14, 10, 4, 10));
        topPanel.add(lblTitle, BorderLayout.NORTH);

        if (isRegistrasi) {
            JLabel petunjuk = new JLabel(
                "<html><center>Wajah harus jelas • Tidak ada yang menutupi • " +
                "Pencahayaan cukup • Wajah di tengah</center></html>",
                SwingConstants.CENTER);
            petunjuk.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            petunjuk.setForeground(new Color(0x00, 0xC9, 0xA7));
            petunjuk.setBorder(BorderFactory.createEmptyBorder(0, 15, 6, 15));
            topPanel.add(petunjuk, BorderLayout.CENTER);
        }
        add(topPanel, BorderLayout.NORTH);

        pnlCamera = new JPanel(new BorderLayout());
        pnlCamera.setBackground(Color.BLACK);
        pnlCamera.setPreferredSize(new Dimension(640, 480));
        pnlCamera.setBorder(BorderFactory.createEmptyBorder(5, 15, 5, 15));

        pnlPreview = new JPanel(new BorderLayout());
        pnlPreview.setBackground(new Color(0x1B2A4A));
        pnlPreview.setBorder(BorderFactory.createEmptyBorder(5, 15, 5, 15));
        lblPreview = new JLabel("", SwingConstants.CENTER);
        lblPreview.setPreferredSize(new Dimension(640, 480));
        pnlPreview.add(lblPreview, BorderLayout.CENTER);
        pnlPreview.setVisible(false);

        JPanel center = new JPanel(new CardLayout());
        center.add(pnlCamera,  "camera");
        center.add(pnlPreview, "preview");
        add(center, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(0, 4));
        south.setBackground(new Color(0x1B2A4A));
        south.setBorder(BorderFactory.createEmptyBorder(4, 0, 14, 0));

        lblValidasi = new JLabel(" ", SwingConstants.CENTER);
        lblValidasi.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblValidasi.setForeground(new Color(0xFF, 0xC1, 0x07));
        lblValidasi.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 10));
        south.add(lblValidasi, BorderLayout.NORTH);

        JPanel pnlBtn = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
        pnlBtn.setBackground(new Color(0x1B2A4A));

        btnCapture = buatBtn("Ambil Foto",   new Color(0x00C9A7), Color.WHITE);
        btnRetake  = buatBtn("Foto Ulang",   new Color(0xFFC107), Color.BLACK);
        btnConfirm = buatBtn("Gunakan Foto", new Color(0x28A745), Color.WHITE);
        JButton btnCancel = buatBtn("Batal", new Color(0xDC3545), Color.WHITE);

        btnRetake.setVisible(false);
        btnConfirm.setVisible(false);

        pnlBtn.add(btnCapture);
        pnlBtn.add(btnRetake);
        pnlBtn.add(btnConfirm);
        pnlBtn.add(btnCancel);
        south.add(pnlBtn, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        btnCapture.addActionListener(e -> capturePhoto(center));
        btnRetake.addActionListener(e  -> retakePhoto(center));
        btnConfirm.addActionListener(e -> doConfirm());
        btnCancel.addActionListener(e  -> { confirmed = false; stopWebcam(); dispose(); });

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                confirmed = false; stopWebcam();
            }
        });
    }

    private void initWebcam() {
        new Thread(() -> {
            try {
                webcam = Webcam.getDefault();
                if (webcam != null) {
                    webcam.setViewSize(new Dimension(640, 480));
                    webcamPanel = new WebcamPanel(webcam);
                    webcamPanel.setFPSDisplayed(false);
                    webcamPanel.setDisplayDebugInfo(false);
                    webcamPanel.setMirrored(true); // preview mirror (natural selfie)
                    SwingUtilities.invokeLater(() -> {
                        pnlCamera.add(webcamPanel, BorderLayout.CENTER);
                        pnlCamera.revalidate();
                        pnlCamera.repaint();
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        JLabel lbl = new JLabel(
                            "<html><center>Kamera tidak tersedia.<br>" +
                            "Gunakan foto dari file.</center></html>",
                            SwingConstants.CENTER);
                        lbl.setForeground(Color.WHITE);
                        pnlCamera.add(lbl, BorderLayout.CENTER);
                        btnCapture.setText("Pilih Foto dari File");
                        btnCapture.removeActionListener(btnCapture.getActionListeners()[0]);
                        btnCapture.addActionListener(ev -> pickFromFile());
                    });
                }
            } catch (Exception e) {
                System.err.println("[WebcamDialog] Error: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    JLabel lbl = new JLabel("Gagal membuka kamera: " + e.getMessage(),
                        SwingConstants.CENTER);
                    lbl.setForeground(Color.RED);
                    pnlCamera.add(lbl, BorderLayout.CENTER);
                });
            }
        }).start();
    }

    // ── Capture ───────────────────────────────────────────────

    private void capturePhoto(JPanel cardPanel) {
        if (webcam == null) {
            JOptionPane.showMessageDialog(this, "Kamera tidak tersedia.", "Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        btnCapture.setEnabled(false);
        btnCapture.setText("Mengambil...");

        new Thread(() -> {
            try {
                if (!webcam.isOpen()) webcam.open();
                final BufferedImage raw = webcam.getImage();
                SwingUtilities.invokeLater(() -> {
                    btnCapture.setEnabled(true);
                    btnCapture.setText("Ambil Foto");
                    if (raw == null) {
                        JOptionPane.showMessageDialog(WebcamCaptureDialog.this,
                            "Gagal mengambil foto. Coba lagi.", "Error",
                            JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    prosesGambar(raw, cardPanel);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    btnCapture.setEnabled(true);
                    btnCapture.setText("Ambil Foto");
                    JOptionPane.showMessageDialog(WebcamCaptureDialog.this,
                        "Error kamera: " + ex.getMessage(), "Error",
                        JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void prosesGambar(BufferedImage raw, JPanel cardPanel) {
        // Konversi ke TYPE_INT_RGB (webcam kadang return TYPE_CUSTOM=0)
        // Tidak ada flip — webcam driver Windows sudah output normal
        capturedImage = toStandardRGB(raw);

        // Tampilkan kotak wajah di preview
        BufferedImage preview = gambarFaceBox(capturedImage);
        lblPreview.setIcon(new ImageIcon(
            preview.getScaledInstance(640, 480, Image.SCALE_SMOOTH)));

        ((CardLayout) cardPanel.getLayout()).show(cardPanel, "preview");
        pnlPreview.setVisible(true);
        btnCapture.setVisible(false);
        btnRetake.setVisible(true);
        btnConfirm.setVisible(true);
        btnConfirm.setEnabled(false); // disable sampai validasi selesai

        capturedImageBytes = imageToBytes(capturedImage);
        lblValidasi.setForeground(new Color(0xFF, 0xC1, 0x07));
        lblValidasi.setText("Memeriksa foto...");
        jalankanValidasi();
    }

    private void jalankanValidasi() {
        if (capturedImage == null) return;

        new SwingWorker<ImageValidationUtil.ValidationResult, Void>() {
            @Override
            protected ImageValidationUtil.ValidationResult doInBackground() {
                return isRegistrasi
                    ? ImageValidationUtil.validateRegistrasi(capturedImage)
                    : ImageValidationUtil.validateAbsensi(capturedImage);
            }
            @Override
            protected void done() {
                try {
                    ImageValidationUtil.ValidationResult r = get();
                    fotoValid = r.valid;
                    if (r.valid) {
                        lblValidasi.setForeground(new Color(0x28, 0xA7, 0x45));
                        String detail = "";
                        if (r.faceResult != null) {
                            detail = String.format(" | Wajah: %d | Cahaya wajah: %.0f/255",
                                r.faceResult.faceCount, r.faceResult.brightness);
                        }
                        lblValidasi.setText("Foto valid" + detail);
                        btnConfirm.setEnabled(true);
                    } else {
                        lblValidasi.setForeground(new Color(0xDC, 0x35, 0x45));
                        String msg = r.message != null
                            ? r.message.split("\n")[0] : "Foto tidak valid";
                        lblValidasi.setText(msg);
                        btnConfirm.setEnabled(false); // wajib foto ulang
                    }
                } catch (Exception e) {
                    lblValidasi.setText(" ");
                    btnConfirm.setEnabled(true);
                }
            }
        }.execute();
    }

    private void retakePhoto(JPanel cardPanel) {
        capturedImage      = null;
        capturedImageBytes = null;
        fotoValid          = false;
        lblPreview.setIcon(null);
        lblValidasi.setText(" ");
        btnConfirm.setEnabled(false);
        ((CardLayout) cardPanel.getLayout()).show(cardPanel, "camera");
        btnCapture.setVisible(true);
        btnRetake.setVisible(false);
        btnConfirm.setVisible(false);
    }

    private void doConfirm() {
        if (capturedImageBytes == null) return;
        btnConfirm.setEnabled(false);
        btnConfirm.setText("Memvalidasi...");

        new SwingWorker<ImageValidationUtil.ValidationResult, Void>() {
            @Override
            protected ImageValidationUtil.ValidationResult doInBackground() {
                return isRegistrasi
                    ? ImageValidationUtil.validateRegistrasi(capturedImage)
                    : ImageValidationUtil.validateAbsensi(capturedImage);
            }
            @Override
            protected void done() {
                btnConfirm.setText("Gunakan Foto");
                btnConfirm.setEnabled(true);
                try {
                    ImageValidationUtil.ValidationResult r = get();
                    if (!r.valid) {
                        JOptionPane.showMessageDialog(WebcamCaptureDialog.this,
                            r.message, "Foto Tidak Valid", JOptionPane.WARNING_MESSAGE);
                        btnRetake.doClick();
                        return;
                    }
                    confirmed = true;
                    stopWebcam();
                    dispose();
                } catch (Exception e) {
                    confirmed = true;
                    stopWebcam();
                    dispose();
                }
            }
        }.execute();
    }

    // ── Gambar kotak wajah di preview ─────────────────────────

    private BufferedImage gambarFaceBox(BufferedImage image) {
        if (!FaceDetectionUtil.isAvailable() || image == null) return image;
        try {
            org.opencv.core.Mat mat = FaceDetectionUtil.bufferedImageToMat(image);
            org.opencv.core.MatOfRect faces = FaceDetectionUtil.detectFaces(mat);
            org.opencv.core.Rect[] arr = faces.toArray();
            mat.release();
            if (arr.length == 0) return image;

            BufferedImage out = new BufferedImage(
                image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = out.createGraphics();
            g2.drawImage(image, 0, 0, null);
            g2.setColor(new Color(0x00, 0xC9, 0xA7));
            g2.setStroke(new java.awt.BasicStroke(3f));
            for (org.opencv.core.Rect r : arr)
                g2.drawRect(r.x, r.y, r.width, r.height);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
            g2.setColor(new Color(0x00, 0xC9, 0xA7));
            g2.drawString(arr.length + " wajah", 10, 24);
            g2.dispose();
            return out;
        } catch (Exception e) {
            return image;
        }
    }

    // ── Pilih dari file ───────────────────────────────────────

    private void pickFromFile() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Gambar (*.jpg, *.png)", "jpg", "jpeg", "png"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                capturedImage = ImageIO.read(fc.getSelectedFile());
                if (capturedImage != null) {
                    capturedImageBytes = imageToBytes(capturedImage);
                    lblPreview.setIcon(new ImageIcon(
                        capturedImage.getScaledInstance(400, 300, Image.SCALE_SMOOTH)));
                    btnRetake.setVisible(true);
                    btnConfirm.setVisible(true);
                    btnConfirm.setEnabled(false);
                    btnCapture.setVisible(false);
                    lblValidasi.setText("Memeriksa foto...");
                    jalankanValidasi();
                }
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Gagal memuat foto: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    /**
     * Konversi ke TYPE_INT_RGB.
     * Sarxos webcam kadang return TYPE_CUSTOM (0) yang menyebabkan error.
     * Tidak ada flip — driver Windows sudah output normal.
     */
    private BufferedImage toStandardRGB(BufferedImage src) {
        if (src == null) return null;
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return dst;
    }

    private byte[] imageToBytes(BufferedImage image) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "JPG", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            System.err.println("[WebcamDialog] Convert error: " + e.getMessage());
            return null;
        }
    }

    private JButton buatBtn(String text, Color bg, Color fg) {
        JButton b = new JButton(text);
        b.setBackground(bg);
        b.setForeground(fg);
        b.setFont(new Font("Segoe UI", Font.BOLD, 13));
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(160, 40));
        return b;
    }

    private void stopWebcam() {
        if (webcam != null && webcam.isOpen())
            new Thread(() -> webcam.close()).start();
    }

    // ── Public API ────────────────────────────────────────────
    public boolean       isConfirmed()          { return confirmed; }
    public byte[]        getCapturedImageBytes() { return capturedImageBytes; }
    public BufferedImage getCapturedImage()      { return capturedImage; }
}