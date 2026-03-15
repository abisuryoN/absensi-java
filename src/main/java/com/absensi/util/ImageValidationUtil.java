package com.absensi.util;

import java.awt.image.BufferedImage;

/**
 * Validasi gambar sebelum disimpan/dipakai absensi.
 *
 * Aturan REGISTRASI (ketat):
 *  - Brightness >= 80
 *  - 1 wajah tepat
 *  - Wajah di tengah, tidak terpotong, ukuran cukup
 *
 * Aturan ABSENSI (ketat untuk brightness, longgar posisi):
 *  - Brightness >= 80 — FOTO GELAP SELALU DITOLAK
 *  - Wajah WAJIB ada minimal 1
 *  - Tidak boleh lebih dari 1 wajah
 *
 * Jika OpenCV tidak tersedia:
 *  - Brightness tetap dicek (manual)
 *  - Deteksi wajah dilewati
 */
public class ImageValidationUtil {

    public static class ValidationResult {
        public final boolean valid;
        public final String  message;
        public final FaceDetectionUtil.FaceDetectionResult faceResult;

        public ValidationResult(boolean valid, String message,
                FaceDetectionUtil.FaceDetectionResult faceResult) {
            this.valid      = valid;
            this.message    = message;
            this.faceResult = faceResult;
        }
    }

    // ── Validasi REGISTRASI ───────────────────────────────────

    public static ValidationResult validateRegistrasi(BufferedImage image) {
        if (image == null)
            return new ValidationResult(false,
                "Gagal mengambil gambar dari kamera.", null);

        FaceDetectionUtil.FaceDetectionResult r =
            FaceDetectionUtil.validate(image);
        return new ValidationResult(r.valid, r.errorMessage, r);
    }

    // ── Validasi ABSENSI ─────────────────────────────────────

    public static ValidationResult validateAbsensi(BufferedImage image) {
        if (image == null)
            return new ValidationResult(false,
                "Gagal mengambil gambar dari kamera.", null);

        FaceDetectionUtil.FaceDetectionResult r =
            FaceDetectionUtil.validateAbsensi(image);
        return new ValidationResult(r.valid, r.errorMessage, r);
    }
}