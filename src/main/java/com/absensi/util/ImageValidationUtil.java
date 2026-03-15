package com.absensi.util;

import java.awt.image.BufferedImage;

/**
 * Wrapper validasi gambar.
 *
 * Logika:
 *  - Deteksi wajah DULU
 *  - Brightness dicek HANYA di area wajah
 *  - Foto terang tapi tanpa wajah = DITOLAK
 *  - Foto sedikit gelap tapi wajah terdeteksi dan wajah cukup terang = DITERIMA
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

    public static ValidationResult validateRegistrasi(BufferedImage image) {
        if (image == null)
            return new ValidationResult(false,
                "Gagal mengambil gambar dari kamera.", null);
        FaceDetectionUtil.FaceDetectionResult r =
            FaceDetectionUtil.validate(image);
        return new ValidationResult(r.valid, r.errorMessage, r);
    }

    public static ValidationResult validateAbsensi(BufferedImage image) {
        if (image == null)
            return new ValidationResult(false,
                "Gagal mengambil gambar dari kamera.", null);
        FaceDetectionUtil.FaceDetectionResult r =
            FaceDetectionUtil.validateAbsensi(image);
        return new ValidationResult(r.valid, r.errorMessage, r);
    }
}