package com.absensi.absensiapp;

import com.absensi.config.DatabaseConfig;
import com.absensi.ui.auth.LoginFrame;
import com.absensi.util.FaceDetectionUtil;
import com.absensi.util.LocationServer;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;

/**
 * Entry point aplikasi Sistem Absensi Karyawan.
 */
public class AbsensiApp {

    public static void main(String[] args) {

        // ── 1. FlatLaf Look and Feel ──────────────────────────
        try {
            FlatLightLaf.setup();
            UIManager.put("Button.arc",         8);
            UIManager.put("Component.arc",      8);
            UIManager.put("ProgressBar.arc",    6);
            UIManager.put("TextComponent.arc",  6);
            UIManager.put("ScrollBar.width",    8);
        } catch (Exception e) {
            System.err.println("Gagal set FlatLaf: " + e.getMessage());
        }

        // ── 2. Test koneksi database ──────────────────────────
        System.out.println("=== Sistem Absensi Karyawan v1.0 ===");
        System.out.println("Menguji koneksi database...");

        if (!DatabaseConfig.testConnection()) {
            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(null,
                    "Gagal terhubung ke database MySQL!\n\n" +
                    "Pastikan:\n" +
                    "1. MySQL Server sudah berjalan\n" +
                    "2. Database 'absensi_db' sudah dibuat\n" +
                    "3. Username/Password di DatabaseConfig.java sudah benar\n\n" +
                    "Jalankan file: database/schema.sql terlebih dahulu.",
                    "Koneksi Database Gagal",
                    JOptionPane.ERROR_MESSAGE)
            );
            System.exit(1);
        }
        System.out.println("Koneksi database berhasil!");

        // ── 3. Inisialisasi OpenCV / Face Detection ───────────
        System.out.println("Menginisialisasi OpenCV...");
        FaceDetectionUtil.init();
        if (FaceDetectionUtil.isAvailable()) {
            System.out.println("OpenCV siap.");
        } else {
            System.out.println("OpenCV tidak tersedia — validasi wajah dinonaktifkan.");
        }

        // ── 4. Start GPS HTTP Server ──────────────────────────
        LocationServer.start();
        System.out.println("=========================================");
        System.out.println("GPS URL untuk HP karyawan:");
        System.out.println("  " + LocationServer.getGpsUrl());
        System.out.println("Buka URL di atas di browser HP karyawan");
        System.out.println("(HP dan PC harus terhubung WiFi yang sama)");
        System.out.println("=========================================");

        // ── 5. Jalankan UI di Event Dispatch Thread ───────────
        SwingUtilities.invokeLater(() -> {
            LoginFrame loginFrame = new LoginFrame();
            loginFrame.setVisible(true);
        });

        // ── 6. Shutdown hook ──────────────────────────────────
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Menutup aplikasi...");
            LocationServer.stop();
            DatabaseConfig.closeConnection();
            System.out.println("Selesai.");
        }));
    }
}