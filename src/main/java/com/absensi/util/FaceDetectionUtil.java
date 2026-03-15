package com.absensi.util;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Deteksi wajah menggunakan OpenCV Haar Cascade.
 *
 * Threshold yang diperketat:
 *  - Brightness minimum 80 (dari 60) — foto gelap ditolak
 *  - Face size minimum 80px (dari 100px) — lebih toleran tapi tetap reject blur
 *  - Validasi tanpa OpenCV: TETAP cek brightness manual
 */
public class FaceDetectionUtil {

    private static CascadeClassifier faceDetector;
    private static boolean           opencvLoaded = false;

    // ── Threshold — diperketat ────────────────────────────────
    public static final int    MIN_FACE_WIDTH   = 80;
    public static final int    MIN_FACE_HEIGHT  = 80;
    public static final double MIN_BRIGHTNESS   = 80.0;  // dinaikkan dari 60
    public static final double CENTER_TOLERANCE = 0.35;

    // ── Hasil deteksi ─────────────────────────────────────────

    public static class FaceDetectionResult {
        public final boolean valid;
        public final String  errorMessage;
        public final int     faceCount;
        public final Rect    faceRect;
        public final double  brightness;

        public FaceDetectionResult(boolean valid, String errorMessage,
                                    int faceCount, Rect faceRect,
                                    double brightness) {
            this.valid        = valid;
            this.errorMessage = errorMessage;
            this.faceCount    = faceCount;
            this.faceRect     = faceRect;
            this.brightness   = brightness;
        }

        public static FaceDetectionResult sukses(int fc, Rect r, double b) {
            return new FaceDetectionResult(true, null, fc, r, b);
        }
        public static FaceDetectionResult gagal(String msg) {
            return new FaceDetectionResult(false, msg, 0, null, 0);
        }
        public static FaceDetectionResult gagal(String msg, int fc, double b) {
            return new FaceDetectionResult(false, msg, fc, null, b);
        }
    }

    // ── Init OpenCV ───────────────────────────────────────────

    public static void init() {
        if (opencvLoaded) return;
        try {
            nu.pattern.OpenCV.loadLocally();
            opencvLoaded = true;
            String cascadePath = extractCascadeFile();
            faceDetector = new CascadeClassifier(cascadePath);
            if (faceDetector.empty()) {
                System.err.println("[FaceDetection] Haar Cascade gagal dimuat.");
                faceDetector = null;
            } else {
                System.out.println("[FaceDetection] OpenCV siap.");
            }
        } catch (Exception e) {
            System.err.println("[FaceDetection] OpenCV tidak tersedia: " +
                e.getMessage());
            opencvLoaded = false;
        }
    }

    private static String extractCascadeFile() throws Exception {
        String[] paths = {
            "/haarcascades/haarcascade_frontalface_default.xml",
            "/opencv/haarcascade_frontalface_default.xml"
        };
        for (String p : paths) {
            InputStream is = FaceDetectionUtil.class.getResourceAsStream(p);
            if (is != null) {
                Path tmp = Files.createTempFile("haarcascade_", ".xml");
                Files.copy(is, tmp, StandardCopyOption.REPLACE_EXISTING);
                is.close();
                tmp.toFile().deleteOnExit();
                return tmp.toString();
            }
        }
        File local = new File("haarcascade_frontalface_default.xml");
        if (local.exists()) return local.getAbsolutePath();
        throw new RuntimeException("haarcascade_frontalface_default.xml tidak ditemukan.");
    }

    public static boolean isAvailable() {
        return opencvLoaded && faceDetector != null;
    }

    // ── Konversi ──────────────────────────────────────────────

    public static Mat bufferedImageToMat(BufferedImage image) {
        BufferedImage bgr = new BufferedImage(
            image.getWidth(), image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        bgr.getGraphics().drawImage(image, 0, 0, null);
        byte[] pixels = ((DataBufferByte) bgr.getRaster()
            .getDataBuffer()).getData();
        Mat mat = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC3);
        mat.put(0, 0, pixels);
        return mat;
    }

    // ── Validasi REGISTRASI (ketat) ───────────────────────────

    /**
     * Validasi foto untuk REGISTRASI:
     *  1. Brightness >= 80 (wajib terang)
     *  2. Tepat 1 wajah
     *  3. Wajah ukuran cukup (tidak terlalu jauh)
     *  4. Wajah di tengah frame
     *  5. Wajah tidak terpotong
     *
     * Jika OpenCV tidak tersedia: tetap cek brightness manual,
     * tapi skip deteksi wajah.
     */
    public static FaceDetectionResult validate(BufferedImage image) {
        if (image == null)
            return FaceDetectionResult.gagal("Gagal mengambil gambar dari kamera.");

        // Cek brightness SELALU — bahkan tanpa OpenCV
        double brightness = hitungBrightness(image);
        System.out.printf("[FaceDetection] Brightness: %.1f (min: %.0f)%n",
            brightness, MIN_BRIGHTNESS);

        if (brightness < MIN_BRIGHTNESS) {
            return FaceDetectionResult.gagal(
                "Foto terlalu gelap (brightness: " + (int)brightness + "/255).\n\n" +
                "Pastikan:\n" +
                "• Berada di tempat dengan cahaya yang cukup\n" +
                "• Wajah tidak tertutup bayangan\n" +
                "• Lampu menyala jika di dalam ruangan",
                0, brightness);
        }

        // Jika OpenCV tidak tersedia — hanya lolos brightness check
        if (!isAvailable()) {
            System.out.println("[FaceDetection] OpenCV tidak tersedia, " +
                "hanya brightness yang dicek.");
            return FaceDetectionResult.sukses(1, null, brightness);
        }

        Mat mat = bufferedImageToMat(image);

        // Deteksi wajah
        Mat gray = new Mat();
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.equalizeHist(gray, gray);

        MatOfRect faces = new MatOfRect();
        faceDetector.detectMultiScale(gray, faces, 1.1, 5, 0,
            new Size(MIN_FACE_WIDTH, MIN_FACE_HEIGHT), new Size());

        Rect[] faceArr = faces.toArray();
        int    fc      = faceArr.length;
        gray.release();
        mat.release();

        System.out.printf("[FaceDetection] Wajah terdeteksi: %d%n", fc);

        if (fc == 0) {
            return FaceDetectionResult.gagal(
                "Wajah tidak terdeteksi.\n\n" +
                "Pastikan:\n" +
                "• Wajah menghadap langsung ke kamera\n" +
                "• Tidak menggunakan masker/penutup wajah\n" +
                "• Pencahayaan cukup terang\n" +
                "• Jarak tidak terlalu jauh dari kamera",
                0, brightness);
        }

        if (fc > 1) {
            return FaceDetectionResult.gagal(
                "Terdeteksi " + fc + " wajah.\n" +
                "Pastikan hanya satu orang di depan kamera.",
                fc, brightness);
        }

        Rect face = faceArr[0];

        // Ukuran wajah — terlalu jauh dari kamera
        if (face.width < MIN_FACE_WIDTH || face.height < MIN_FACE_HEIGHT) {
            return FaceDetectionResult.gagal(
                "Wajah terlalu jauh dari kamera.\n" +
                "Silakan mendekat agar wajah terlihat lebih besar.",
                fc, brightness);
        }

        // Posisi — harus di tengah frame
        String posErr = cekPosisi(face, image.getWidth(), image.getHeight());
        if (posErr != null)
            return FaceDetectionResult.gagal(posErr, fc, brightness);

        // Tidak terpotong
        if (face.x <= 0 || face.y <= 0
                || (face.x + face.width)  >= image.getWidth()
                || (face.y + face.height) >= image.getHeight()) {
            return FaceDetectionResult.gagal(
                "Wajah terpotong di tepi frame.\n" +
                "Pastikan seluruh wajah terlihat di kamera.",
                fc, brightness);
        }

        return FaceDetectionResult.sukses(fc, face, brightness);
    }

    // ── Validasi ABSENSI (brightness + jumlah wajah) ──────────

    /**
     * Validasi foto untuk ABSENSI — lebih longgar dari registrasi
     * tapi tetap wajib:
     *  1. Brightness >= 80 (wajib terang — foto gelap DITOLAK)
     *  2. Wajah WAJIB terdeteksi (tidak boleh 0)
     *  3. Tidak boleh lebih dari 1 wajah
     */
    public static FaceDetectionResult validateAbsensi(BufferedImage image) {
        if (image == null)
            return FaceDetectionResult.gagal("Gagal mengambil gambar.");

        // Brightness selalu dicek
        double brightness = hitungBrightness(image);
        System.out.printf("[FaceDetection] Absensi brightness: %.1f%n", brightness);

        if (brightness < MIN_BRIGHTNESS) {
            return FaceDetectionResult.gagal(
                "Foto terlalu gelap.\n\n" +
                "Pastikan berada di tempat yang cukup terang\n" +
                "sebelum mengambil foto absensi.",
                0, brightness);
        }

        if (!isAvailable()) {
            System.out.println("[FaceDetection] OpenCV tidak ada, skip deteksi wajah.");
            return FaceDetectionResult.sukses(1, null, brightness);
        }

        Mat mat  = bufferedImageToMat(image);
        MatOfRect faces = detectFaces(mat);
        int fc   = faces.toArray().length;
        mat.release();

        System.out.printf("[FaceDetection] Absensi wajah: %d%n", fc);

        if (fc == 0) {
            return FaceDetectionResult.gagal(
                "Wajah tidak terdeteksi.\n\n" +
                "Pastikan:\n" +
                "• Wajah menghadap langsung ke kamera\n" +
                "• Tidak ada yang menutupi wajah\n" +
                "• Pencahayaan cukup terang",
                0, brightness);
        }

        if (fc > 1) {
            return FaceDetectionResult.gagal(
                "Terdeteksi lebih dari satu wajah.\n" +
                "Pastikan hanya Anda yang berada di depan kamera.",
                fc, brightness);
        }

        return FaceDetectionResult.sukses(fc, faces.toArray()[0], brightness);
    }

    // ── Deteksi wajah saja (untuk FaceRecognitionUtil) ────────

    public static MatOfRect detectFaces(Mat mat) {
        if (!isAvailable()) return new MatOfRect();
        Mat gray = new Mat();
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.equalizeHist(gray, gray);
        MatOfRect faces = new MatOfRect();
        faceDetector.detectMultiScale(gray, faces, 1.1, 5, 0,
            new Size(MIN_FACE_WIDTH, MIN_FACE_HEIGHT), new Size());
        gray.release();
        return faces;
    }

    // ── Brightness ────────────────────────────────────────────

    public static double hitungBrightness(Mat mat) {
        Mat gray = new Mat();
        if (mat.channels() == 3)
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
        else
            gray = mat.clone();
        Scalar mean = Core.mean(gray);
        gray.release();
        return mean.val[0];
    }

    public static double hitungBrightness(BufferedImage image) {
        if (isAvailable()) {
            Mat mat = bufferedImageToMat(image);
            double b = hitungBrightness(mat);
            mat.release();
            return b;
        }
        return hitungBrightnessManual(image);
    }

    /**
     * Hitung brightness tanpa OpenCV.
     * Dipakai sebagai fallback — tetap memblokir foto gelap
     * meski OpenCV tidak tersedia.
     */
    public static double hitungBrightnessManual(BufferedImage image) {
        long total = 0;
        int  count = 0;
        int  step  = 3;
        for (int y = 0; y < image.getHeight(); y += step) {
            for (int x = 0; x < image.getWidth(); x += step) {
                int rgb = image.getRGB(x, y);
                int r   = (rgb >> 16) & 0xFF;
                int g   = (rgb >> 8)  & 0xFF;
                int b   = rgb         & 0xFF;
                total  += (int)(0.299*r + 0.587*g + 0.114*b);
                count++;
            }
        }
        double brightness = count == 0 ? 0 : (double)total / count;
        System.out.printf("[FaceDetection] Brightness manual: %.1f%n", brightness);
        return brightness;
    }

    private static String cekPosisi(Rect face, int fw, int fh) {
        double cx = face.x + face.width  / 2.0;
        double cy = face.y + face.height / 2.0;
        double tx = fw * CENTER_TOLERANCE;
        double ty = fh * CENTER_TOLERANCE;
        if (Math.abs(cx - fw/2.0) > tx || Math.abs(cy - fh/2.0) > ty)
            return "Posisikan wajah di tengah kamera.";
        return null;
    }
}