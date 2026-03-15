package com.absensi.util;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Face verification — bandingkan wajah absensi dengan foto registrasi.
 *
 * Metode: LBP (Local Binary Pattern) histogram comparison
 *  - Tidak butuh opencv-contrib
 *  - LBP sangat tahan terhadap perubahan pencahayaan
 *  - Confidence = jarak histogram (0 = identik, makin besar makin berbeda)
 *
 * Threshold DIPERKETAT:
 *  - THRESHOLD = 40 (dari 50) — lebih ketat, wajah harus lebih mirip
 *  - Jika confidence > 40 → DITOLAK
 *
 * Catatan: threshold bisa disesuaikan di konfigurasi database
 * (FACE_THRESHOLD) jika terlalu sering false reject/accept.
 */
public class FaceRecognitionUtil {

    // Semakin kecil = semakin ketat
    // 40 = cukup ketat untuk membedakan orang berbeda
    // 50 = lebih longgar (gunakan jika sering false reject)
    public static double THRESHOLD = 40.0;

    private static final int FACE_W = 100;
    private static final int FACE_H = 100;

    // ── Hasil verifikasi ──────────────────────────────────────

    public static class VerificationResult {
        public final boolean match;
        public final double  confidence;
        public final String  message;

        public VerificationResult(boolean match, double confidence,
                                   String message) {
            this.match      = match;
            this.confidence = confidence;
            this.message    = message;
        }

        /** Persentase kemiripan 0–100% */
        public int getSimilarityPercent() {
            return (int) Math.max(0, Math.min(100, 100.0 - confidence));
        }
    }

    // ── Verifikasi utama ──────────────────────────────────────

    /**
     * Verifikasi apakah foto absensi cocok dengan foto registrasi.
     *
     * @param pathFotoRegistrasi  Path file foto saat registrasi
     * @param fotoAbsensi         BufferedImage foto saat absensi
     */
    public static VerificationResult verify(String pathFotoRegistrasi,
                                             BufferedImage fotoAbsensi) {
        if (!FaceDetectionUtil.isAvailable()) {
            System.out.println("[FaceRecog] OpenCV tidak tersedia, " +
                "verifikasi dilewati.");
            return new VerificationResult(true, 0,
                "Verifikasi dilewati (OpenCV tidak tersedia).");
        }

        // Load foto registrasi
        Mat matReg = loadWajah(pathFotoRegistrasi);
        if (matReg == null) {
            return new VerificationResult(false, 999,
                "Foto registrasi tidak ditemukan.\n" +
                "Silakan hubungi HRD untuk registrasi ulang.");
        }

        // Siapkan foto absensi
        Mat matAbs = bufferedToWajah(fotoAbsensi);
        if (matAbs == null) {
            matReg.release();
            return new VerificationResult(false, 999,
                "Wajah tidak terdeteksi pada foto absensi.\n" +
                "Pastikan wajah terlihat jelas di kamera.");
        }

        // Hitung kemiripan dengan LBP + histogram
        double scoreLBP  = bandingkanLBP(matReg, matAbs);
        double scoreHist = bandingkanHistogram(matReg, matAbs);

        // Gabungkan: LBP lebih akurat, histogram sebagai pendukung
        double scoreFinal = (scoreLBP * 0.65) + (scoreHist * 0.35);

        matReg.release();
        matAbs.release();

        boolean match = scoreFinal <= THRESHOLD;

        System.out.printf("[FaceRecog] LBP=%.1f Hist=%.1f Final=%.1f " +
            "Threshold=%.0f Match=%s%n",
            scoreLBP, scoreHist, scoreFinal, THRESHOLD, match);

        if (match) {
            return new VerificationResult(true, scoreFinal,
                String.format("Verifikasi wajah berhasil.\nKemiripan: %d%%",
                    (int) Math.max(0, 100 - scoreFinal)));
        } else {
            return new VerificationResult(false, scoreFinal,
                "Wajah tidak cocok dengan data registrasi.\n" +
                "Absensi ditolak.\n\n" +
                String.format("Kemiripan: %d%% (minimum 60%%)",
                    (int) Math.max(0, 100 - scoreFinal)));
        }
    }

    // ── Load & normalisasi wajah ──────────────────────────────

    private static Mat loadWajah(String path) {
        if (path == null || path.isBlank()) return null;
        File f = new File(path);
        if (!f.exists()) {
            System.err.println("[FaceRecog] File tidak ada: " + path);
            return null;
        }
        Mat mat = Imgcodecs.imread(path);
        if (mat.empty()) return null;
        return normalisasiWajah(mat);
    }

    private static Mat bufferedToWajah(BufferedImage img) {
        if (img == null) return null;
        Mat mat = FaceDetectionUtil.bufferedImageToMat(img);
        return normalisasiWajah(mat);
    }

    /**
     * 1. Deteksi wajah → crop region terbesar
     * 2. Grayscale
     * 3. Resize ke 100x100
     * 4. Histogram equalization (normalisasi cahaya)
     */
    private static Mat normalisasiWajah(Mat mat) {
        if (mat == null || mat.empty()) return null;

        // Crop region wajah
        MatOfRect faces   = FaceDetectionUtil.detectFaces(mat);
        Rect[]    faceArr = faces.toArray();
        Mat region;

        if (faceArr.length > 0) {
            Rect biggest = faceArr[0];
            for (Rect r : faceArr)
                if (r.area() > biggest.area()) biggest = r;
            biggest = clamp(biggest, mat.cols(), mat.rows());
            region  = new Mat(mat, biggest);
        } else {
            // Tidak ada wajah — pakai seluruh gambar sebagai fallback
            region = mat.clone();
        }

        // Grayscale
        Mat gray = new Mat();
        if (region.channels() == 3)
            Imgproc.cvtColor(region, gray, Imgproc.COLOR_BGR2GRAY);
        else
            gray = region.clone();

        // Resize + equalize
        Mat norm = new Mat();
        Imgproc.resize(gray, norm, new Size(FACE_W, FACE_H));
        Imgproc.equalizeHist(norm, norm);

        gray.release();
        if (region != mat) region.release();
        mat.release();
        return norm;
    }

    // ── LBP Comparison ───────────────────────────────────────

    /**
     * Local Binary Pattern — sangat tahan terhadap perubahan cahaya.
     * Bandingkan histogram LBP dari dua wajah.
     * Return nilai 0–100 (0 = identik, 100 = sangat berbeda).
     */
    private static double bandingkanLBP(Mat face1, Mat face2) {
        try {
            Mat lbp1 = hitungLBP(face1);
            Mat lbp2 = hitungLBP(face2);
            if (lbp1 == null || lbp2 == null) return 50.0;

            double chiSq = hitungChiSquare(lbp1, lbp2);
            lbp1.release();
            lbp2.release();
            return Math.min(100.0, chiSq * 8.0);

        } catch (Exception e) {
            System.err.println("[FaceRecog] LBP error: " + e.getMessage());
            return 50.0;
        }
    }

    /**
     * Hitung LBP image dari grayscale Mat.
     * Setiap pixel dibandingkan dengan 8 tetangga → nilai 0–255.
     */
    private static Mat hitungLBP(Mat gray) {
        if (gray == null || gray.empty()) return null;
        int rows = gray.rows(), cols = gray.cols();
        Mat lbp  = Mat.zeros(rows - 2, cols - 2, CvType.CV_8UC1);

        int[] dy = {-1,-1,-1, 0, 1, 1, 1, 0};
        int[] dx = {-1, 0, 1, 1, 1, 0,-1,-1};

        for (int r = 1; r < rows-1; r++) {
            for (int c = 1; c < cols-1; c++) {
                double center = gray.get(r, c)[0];
                int code = 0;
                for (int b = 0; b < 8; b++) {
                    if (gray.get(r+dy[b], c+dx[b])[0] >= center)
                        code |= (1 << b);
                }
                lbp.put(r-1, c-1, code);
            }
        }
        return lbp;
    }

    // ── Histogram comparison ──────────────────────────────────

    /**
     * Chi-Square histogram comparison.
     * Return 0–100 (0 = identik).
     */
    private static double bandingkanHistogram(Mat face1, Mat face2) {
        try {
            return hitungChiSquare(face1, face2) * 8.0;
        } catch (Exception e) {
            return 50.0;
        }
    }

    private static double hitungChiSquare(Mat img1, Mat img2) {
        List<Mat>  imgs1    = new ArrayList<>();
        List<Mat>  imgs2    = new ArrayList<>();
        MatOfInt   channels = new MatOfInt(0);
        MatOfInt   histSize = new MatOfInt(256);
        MatOfFloat ranges   = new MatOfFloat(0f, 256f);
        Mat        hist1    = new Mat();
        Mat        hist2    = new Mat();
        Mat        mask     = new Mat();

        imgs1.add(img1);
        imgs2.add(img2);

        Imgproc.calcHist(imgs1, channels, mask, hist1, histSize, ranges);
        Imgproc.calcHist(imgs2, channels, mask, hist2, histSize, ranges);
        Core.normalize(hist1, hist1, 0, 1, Core.NORM_MINMAX);
        Core.normalize(hist2, hist2, 0, 1, Core.NORM_MINMAX);

        double chiSq = Imgproc.compareHist(hist1, hist2,
            Imgproc.CV_COMP_CHISQR);

        hist1.release(); hist2.release();
        return Math.min(12.5, chiSq);
    }

    // ── Helper ───────────────────────────────────────────────

    private static Rect clamp(Rect r, int maxW, int maxH) {
        int x = Math.max(0, r.x);
        int y = Math.max(0, r.y);
        int w = Math.min(r.width,  maxW - x);
        int h = Math.min(r.height, maxH - y);
        return new Rect(x, y, w, h);
    }
}