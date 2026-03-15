package com.absensi.util;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

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
 * LOGIKA VALIDASI YANG BENAR:
 *  1. Deteksi wajah DULU — kalau tidak ada wajah, langsung tolak
 *  2. Brightness dicek HANYA di area wajah — bukan seluruh gambar
 *  3. Ini mencegah:
 *     - Foto gelap dengan wajah terang → diterima (brightness wajah cukup)
 *     - Foto terang tanpa wajah → ditolak (tidak ada wajah)
 *     - Foto dengan tangan/benda menutupi → ditolak (tidak ada wajah)
 */
public class FaceDetectionUtil {

    private static CascadeClassifier faceDetector;
    private static boolean           opencvLoaded = false;

    // ── Threshold ─────────────────────────────────────────────
    public static final int    MIN_FACE_WIDTH      = 60;   // lebih toleran
    public static final int    MIN_FACE_HEIGHT     = 60;
    public static final double MIN_FACE_BRIGHTNESS = 50.0; // brightness WAJAH saja
    public static final double MIN_IMG_BRIGHTNESS  = 30.0; // brightness gambar (anti pitch-black)
    public static final double CENTER_TOLERANCE    = 0.40; // lebih toleran posisi

    // ── Hasil deteksi ─────────────────────────────────────────

    public static class FaceDetectionResult {
        public final boolean valid;
        public final String  errorMessage;
        public final int     faceCount;
        public final Rect    faceRect;
        public final double  brightness;      // brightness area wajah
        public final double  imgBrightness;   // brightness seluruh gambar

        public FaceDetectionResult(boolean valid, String errorMessage,
                int faceCount, Rect faceRect,
                double brightness, double imgBrightness) {
            this.valid         = valid;
            this.errorMessage  = errorMessage;
            this.faceCount     = faceCount;
            this.faceRect      = faceRect;
            this.brightness    = brightness;
            this.imgBrightness = imgBrightness;
        }

        public static FaceDetectionResult sukses(int fc, Rect r,
                double faceBright, double imgBright) {
            return new FaceDetectionResult(true, null, fc, r,
                faceBright, imgBright);
        }
        public static FaceDetectionResult gagal(String msg) {
            return new FaceDetectionResult(false, msg, 0, null, 0, 0);
        }
        public static FaceDetectionResult gagal(String msg, int fc,
                double fb, double ib) {
            return new FaceDetectionResult(false, msg, fc, null, fb, ib);
        }
    }

    // ── Init ──────────────────────────────────────────────────

    public static void init() {
        if (opencvLoaded) return;
        try {
            nu.pattern.OpenCV.loadLocally();
            opencvLoaded = true;
            String path = extractCascade();
            faceDetector = new CascadeClassifier(path);
            if (faceDetector.empty()) {
                System.err.println("[FaceDetection] Cascade gagal dimuat.");
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

    private static String extractCascade() throws Exception {
        String[] paths = {
            "/haarcascades/haarcascade_frontalface_default.xml",
            "/opencv/haarcascade_frontalface_default.xml"
        };
        for (String p : paths) {
            InputStream is = FaceDetectionUtil.class.getResourceAsStream(p);
            if (is != null) {
                Path tmp = Files.createTempFile("haar_", ".xml");
                Files.copy(is, tmp, StandardCopyOption.REPLACE_EXISTING);
                is.close();
                tmp.toFile().deleteOnExit();
                return tmp.toString();
            }
        }
        File f = new File("haarcascade_frontalface_default.xml");
        if (f.exists()) return f.getAbsolutePath();
        throw new RuntimeException("haarcascade_frontalface_default.xml tidak ditemukan.");
    }

    public static boolean isAvailable() {
        return opencvLoaded && faceDetector != null;
    }

    // ── Konversi ──────────────────────────────────────────────

    public static Mat bufferedImageToMat(BufferedImage image) {
        BufferedImage bgr = new BufferedImage(
            image.getWidth(), image.getHeight(),
            BufferedImage.TYPE_3BYTE_BGR);
        bgr.getGraphics().drawImage(image, 0, 0, null);
        byte[] px = ((DataBufferByte) bgr.getRaster()
            .getDataBuffer()).getData();
        Mat mat = new Mat(image.getHeight(), image.getWidth(),
            CvType.CV_8UC3);
        mat.put(0, 0, px);
        return mat;
    }

    // ── VALIDASI REGISTRASI ───────────────────────────────────

    /**
     * Urutan validasi yang BENAR:
     *  1. Cek gambar tidak pitch-black total
     *  2. Deteksi wajah — WAJIB ada, kalau tidak ada langsung tolak
     *  3. Cek brightness AREA WAJAH — bukan seluruh gambar
     *  4. Cek ukuran wajah
     *  5. Cek posisi wajah
     *  6. Cek wajah tidak terpotong
     */
    public static FaceDetectionResult validate(BufferedImage image) {
        if (image == null)
            return FaceDetectionResult.gagal("Gagal mengambil gambar dari kamera.");

        // Tanpa OpenCV — hanya cek brightness gambar total
        if (!isAvailable()) {
            double ib = hitungBrightnessManual(image);
            System.out.printf("[FaceDetection] Tanpa OpenCV, brightness: %.1f%n", ib);
            if (ib < MIN_IMG_BRIGHTNESS) {
                return FaceDetectionResult.gagal(
                    "Foto terlalu gelap.\nNyalakan lampu atau pindah ke tempat terang.");
            }
            return FaceDetectionResult.sukses(1, null, ib, ib);
        }

        Mat mat = bufferedImageToMat(image);

        // Step 1: Cek gambar tidak pitch-black
        double imgBright = hitungBrightnessRegion(mat, null);
        System.out.printf("[FaceDetection] Brightness gambar: %.1f%n", imgBright);

        if (imgBright < MIN_IMG_BRIGHTNESS) {
            mat.release();
            return FaceDetectionResult.gagal(
                "Foto terlalu gelap.\n\n" +
                "Nyalakan lampu atau pindah ke tempat yang lebih terang.",
                0, 0, imgBright);
        }

        // Step 2: Deteksi wajah DULU
        Mat gray = new Mat();
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.equalizeHist(gray, gray);

        MatOfRect faces = new MatOfRect();
        faceDetector.detectMultiScale(
            gray, faces, 1.1, 4, 0,
            new Size(MIN_FACE_WIDTH, MIN_FACE_HEIGHT), new Size());

        Rect[] arr = faces.toArray();
        int    fc  = arr.length;
        gray.release();

        System.out.printf("[FaceDetection] Wajah terdeteksi: %d%n", fc);

        if (fc == 0) {
            mat.release();
            return FaceDetectionResult.gagal(
                "Wajah tidak terdeteksi.\n\n" +
                "Pastikan:\n" +
                "• Wajah menghadap langsung ke kamera\n" +
                "• Tidak ada yang menutupi wajah (masker, tangan, dll)\n" +
                "• Pencahayaan cukup — hadap ke sumber cahaya\n" +
                "• Jarak tidak terlalu jauh dari kamera",
                0, 0, imgBright);
        }

        if (fc > 1) {
            mat.release();
            return FaceDetectionResult.gagal(
                "Terdeteksi " + fc + " wajah.\n" +
                "Pastikan hanya satu orang di depan kamera.",
                fc, 0, imgBright);
        }

        Rect face = arr[0];

        // Step 3: Cek brightness AREA WAJAH saja
        double faceBright = hitungBrightnessRegion(mat, face);
        System.out.printf("[FaceDetection] Brightness wajah: %.1f%n", faceBright);
        mat.release();

        if (faceBright < MIN_FACE_BRIGHTNESS) {
            return FaceDetectionResult.gagal(
                "Wajah terlalu gelap.\n\n" +
                "Wajah terdeteksi tapi kurang cahaya.\n" +
                "Hadapkan wajah ke sumber cahaya (lampu/jendela).",
                fc, faceBright, imgBright);
        }

        // Step 4: Ukuran wajah
        if (face.width < MIN_FACE_WIDTH || face.height < MIN_FACE_HEIGHT) {
            return FaceDetectionResult.gagal(
                "Wajah terlalu jauh dari kamera.\n" +
                "Silakan mendekat agar wajah lebih besar.",
                fc, faceBright, imgBright);
        }

        // Step 5: Posisi wajah
        String posErr = cekPosisi(face, image.getWidth(), image.getHeight());
        if (posErr != null)
            return FaceDetectionResult.gagal(posErr, fc, faceBright, imgBright);

        // Step 6: Tidak terpotong
        if (face.x <= 2 || face.y <= 2
                || (face.x + face.width)  >= image.getWidth()  - 2
                || (face.y + face.height) >= image.getHeight() - 2) {
            return FaceDetectionResult.gagal(
                "Wajah terpotong di tepi frame.\n" +
                "Mundur sedikit agar seluruh wajah terlihat.",
                fc, faceBright, imgBright);
        }

        return FaceDetectionResult.sukses(fc, face, faceBright, imgBright);
    }

    // ── VALIDASI ABSENSI ─────────────────────────────────────

    /**
     * Validasi absensi — prioritas: wajah HARUS ada.
     *  1. Gambar tidak pitch-black
     *  2. Deteksi wajah WAJIB
     *  3. Brightness area wajah cukup
     *
     * Tidak cek posisi/ukuran — lebih longgar dari registrasi.
     */
    public static FaceDetectionResult validateAbsensi(BufferedImage image) {
        if (image == null)
            return FaceDetectionResult.gagal("Gagal mengambil gambar.");

        if (!isAvailable()) {
            double ib = hitungBrightnessManual(image);
            if (ib < MIN_IMG_BRIGHTNESS)
                return FaceDetectionResult.gagal(
                    "Foto terlalu gelap. Pindah ke tempat lebih terang.");
            return FaceDetectionResult.sukses(1, null, ib, ib);
        }

        Mat mat = bufferedImageToMat(image);

        // Cek gambar tidak pitch-black
        double imgBright = hitungBrightnessRegion(mat, null);
        if (imgBright < MIN_IMG_BRIGHTNESS) {
            mat.release();
            return FaceDetectionResult.gagal(
                "Foto terlalu gelap.\nNyalakan lampu atau pindah ke tempat terang.",
                0, 0, imgBright);
        }

        // Deteksi wajah
        Mat gray = new Mat();
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.equalizeHist(gray, gray);
        MatOfRect faces = new MatOfRect();
        faceDetector.detectMultiScale(
            gray, faces, 1.1, 4, 0,
            new Size(MIN_FACE_WIDTH, MIN_FACE_HEIGHT), new Size());
        Rect[] arr = faces.toArray();
        int    fc  = arr.length;
        gray.release();

        System.out.printf("[FaceDetection] Absensi wajah: %d%n", fc);

        if (fc == 0) {
            mat.release();
            return FaceDetectionResult.gagal(
                "Wajah tidak terdeteksi.\n\n" +
                "Pastikan wajah menghadap kamera dan\n" +
                "tidak ada yang menutupi wajah.",
                0, 0, imgBright);
        }

        if (fc > 1) {
            mat.release();
            return FaceDetectionResult.gagal(
                "Terdeteksi lebih dari satu wajah.\n" +
                "Pastikan hanya Anda di depan kamera.",
                fc, 0, imgBright);
        }

        // Brightness area wajah
        double faceBright = hitungBrightnessRegion(mat, arr[0]);
        mat.release();

        System.out.printf("[FaceDetection] Brightness wajah absensi: %.1f%n",
            faceBright);

        if (faceBright < MIN_FACE_BRIGHTNESS) {
            return FaceDetectionResult.gagal(
                "Wajah kurang terang.\n" +
                "Hadapkan wajah ke sumber cahaya.",
                fc, faceBright, imgBright);
        }

        return FaceDetectionResult.sukses(fc, arr[0], faceBright, imgBright);
    }

    // ── Deteksi wajah saja (untuk FaceRecognitionUtil) ────────

    public static MatOfRect detectFaces(Mat mat) {
        if (!isAvailable()) return new MatOfRect();
        Mat gray = new Mat();
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.equalizeHist(gray, gray);
        MatOfRect faces = new MatOfRect();
        faceDetector.detectMultiScale(gray, faces, 1.1, 4, 0,
            new Size(MIN_FACE_WIDTH, MIN_FACE_HEIGHT), new Size());
        gray.release();
        return faces;
    }

    // ── Brightness helpers ────────────────────────────────────

    /**
     * Hitung brightness rata-rata di region tertentu.
     * Jika region = null → hitung seluruh gambar.
     * Menggunakan channel grayscale untuk akurasi.
     */
    public static double hitungBrightnessRegion(Mat mat, Rect region) {
        try {
            Mat gray = new Mat();
            if (mat.channels() == 3)
                Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
            else
                gray = mat.clone();

            Mat target;
            if (region != null) {
                // Clamp region agar tidak keluar batas
                Rect r = clampRect(region, mat.cols(), mat.rows());
                target = new Mat(gray, r);
            } else {
                target = gray;
            }

            Scalar mean = Core.mean(target);
            double b    = mean.val[0];

            if (region != null) target.release();
            gray.release();
            return b;
        } catch (Exception e) {
            return 128; // fallback neutral
        }
    }

    /**
     * Hitung brightness tanpa OpenCV (fallback manual).
     * Menggunakan formula luminance standar.
     */
    public static double hitungBrightnessManual(BufferedImage image) {
        if (image == null) return 0;
        long total = 0;
        int  count = 0;
        int  step  = 4;
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
        return count == 0 ? 0 : (double) total / count;
    }

    public static double hitungBrightness(BufferedImage image) {
        if (isAvailable()) {
            Mat mat = bufferedImageToMat(image);
            double b = hitungBrightnessRegion(mat, null);
            mat.release();
            return b;
        }
        return hitungBrightnessManual(image);
    }

    // ── Helper ───────────────────────────────────────────────

    private static String cekPosisi(Rect face, int fw, int fh) {
        double cx = face.x + face.width  / 2.0;
        double cy = face.y + face.height / 2.0;
        if (Math.abs(cx - fw/2.0) > fw * CENTER_TOLERANCE
                || Math.abs(cy - fh/2.0) > fh * CENTER_TOLERANCE)
            return "Posisikan wajah di tengah kamera.";
        return null;
    }

    private static Rect clampRect(Rect r, int maxW, int maxH) {
        int x = Math.max(0, r.x);
        int y = Math.max(0, r.y);
        int w = Math.min(r.width,  maxW - x);
        int h = Math.min(r.height, maxH - y);
        return new Rect(x, y, w, h);
    }
}