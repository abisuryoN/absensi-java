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

public class FaceDetectionUtil {

    private static CascadeClassifier faceDetector;
    private static boolean opencvLoaded = false;

    static { init(); }

    // ── Threshold ─────────────────────────────────────────────
    public static final int    MIN_FACE_WIDTH      = 80;
    public static final int    MIN_FACE_HEIGHT     = 80;
    public static final double MIN_FACE_BRIGHTNESS = 40.0; // brightness area wajah
    public static final double MIN_IMG_BRIGHTNESS  = 70.0; // brightness gambar total (tanpa OpenCV)
    public static final double CENTER_TOLERANCE    = 0.40;

    // ── Result ────────────────────────────────────────────────
    public static class FaceDetectionResult {
        public final boolean valid;
        public final String  errorMessage;
        public final int     faceCount;
        public final Rect    faceRect;
        public final double  brightness;
        public final double  imgBrightness;

        public FaceDetectionResult(boolean valid, String errorMessage,
                int faceCount, Rect faceRect,
                double brightness, double imgBrightness) {
            this.valid        = valid;
            this.errorMessage = errorMessage;
            this.faceCount    = faceCount;
            this.faceRect     = faceRect;
            this.brightness   = brightness;
            this.imgBrightness = imgBrightness;
        }

        public static FaceDetectionResult sukses(int fc, Rect r, double fb, double ib) {
            return new FaceDetectionResult(true, null, fc, r, fb, ib);
        }
        public static FaceDetectionResult gagal(String msg) {
            return new FaceDetectionResult(false, msg, 0, null, 0, 0);
        }
        public static FaceDetectionResult gagal(String msg, int fc, double fb, double ib) {
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
            System.err.println("[FaceDetection] OpenCV tidak tersedia: " + e.getMessage());
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
        if (!opencvLoaded) init();
        return opencvLoaded && faceDetector != null;
    }

    // ── Konversi ──────────────────────────────────────────────
    public static Mat bufferedImageToMat(BufferedImage image) {
        BufferedImage bgr = new BufferedImage(
            image.getWidth(), image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        bgr.getGraphics().drawImage(image, 0, 0, null);
        byte[] px = ((DataBufferByte) bgr.getRaster().getDataBuffer()).getData();
        Mat mat = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC3);
        mat.put(0, 0, px);
        return mat;
    }

    // ── Deteksi wajah (dipakai internal + FaceRecognitionUtil) ─
    public static MatOfRect detectFaces(Mat mat) {
        if (!isAvailable()) return new MatOfRect();
        Mat gray = new Mat();
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.equalizeHist(gray, gray);
        MatOfRect faces = new MatOfRect();
        // minNeighbors = 6 (lebih ketat, kurangi false positive)
        faceDetector.detectMultiScale(
            gray, faces, 1.1, 6, 0,
            new Size(MIN_FACE_WIDTH, MIN_FACE_HEIGHT), new Size());
        gray.release();
        return faces;
    }

    // ── VALIDASI REGISTRASI ───────────────────────────────────
    /**
     * Urutan validasi:
     * 1. Brightness gambar total (anti pitch-black)
     * 2. Deteksi wajah — wajib 1 wajah tepat
     * 3. Brightness AREA WAJAH — wajah harus cukup terang
     * 4. Coverage wajah — bagian bawah tidak boleh tertutup
     * 5. Ukuran wajah cukup
     * 6. Posisi wajah di tengah
     * 7. Wajah tidak terpotong
     *
     * Tanpa OpenCV: brightness gambar >= 70 (ketat karena tidak ada deteksi wajah)
     */
    public static FaceDetectionResult validate(BufferedImage image) {
        if (image == null)
            return FaceDetectionResult.gagal("Gagal mengambil gambar dari kamera.");

        // ── Tanpa OpenCV: hanya brightness ───────────────────
        if (!isAvailable()) {
            double ib = hitungBrightnessManual(image);
            System.out.printf("[FaceDetection] Tanpa OpenCV, brightness: %.1f%n", ib);
            if (ib < MIN_IMG_BRIGHTNESS) {
                return FaceDetectionResult.gagal(
                    "Foto terlalu gelap (" + (int)ib + "/255).\n\n" +
                    "Pastikan:\n" +
                    "• Wajah menghadap kamera dengan JELAS\n" +
                    "• Tidak ada yang menutupi wajah\n" +
                    "• Berada di tempat yang cukup terang",
                    0, ib, ib);
            }
            return FaceDetectionResult.sukses(1, null, ib, ib);
        }

        Mat mat = bufferedImageToMat(image);

        // Step 1: Brightness gambar total
        double imgBright = hitungBrightnessRegion(mat, null);
        System.out.printf("[FaceDetection] Brightness gambar: %.1f%n", imgBright);
        if (imgBright < 20.0) {
            mat.release();
            return FaceDetectionResult.gagal(
                "Foto terlalu gelap.\nNyalakan lampu atau pindah ke tempat terang.",
                0, 0, imgBright);
        }

        // Step 2: Deteksi wajah DULU
        Mat gray = new Mat();
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.equalizeHist(gray, gray);
        MatOfRect faces = new MatOfRect();
        faceDetector.detectMultiScale(
            gray, faces, 1.1, 6, 0,
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
                "• Tidak ada yang menutupi wajah\n" +
                "• Pencahayaan cukup terang\n" +
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

        // Step 3: Brightness bagian TENGAH wajah (40%-80% vertikal)
        // Hindari area dahi atas dan baju/dagu bawah yang bisa lebih gelap
        Rect faceCenter = clampRect(
            new Rect(face.x + face.width/6,
                     face.y + face.height*2/5,
                     face.width*2/3,
                     face.height*2/5),
            mat.cols(), mat.rows());
        double faceBright = hitungBrightnessRegion(mat, faceCenter);
        System.out.printf("[FaceDetection] Brightness tengah wajah: %.1f%n", faceBright);
        if (faceBright < MIN_FACE_BRIGHTNESS) {
            mat.release();
            return FaceDetectionResult.gagal(
                "Wajah terlalu gelap.\n\nHadapkan wajah ke sumber cahaya.",
                fc, faceBright, imgBright);
        }

        // Step 4: Cek coverage wajah (bagian bawah tidak tertutup)
        // Bagi wajah jadi 2: atas (mata/dahi) dan bawah (hidung/mulut)
        // Brightness bagian bawah harus minimal 60% dari bagian atas
        String coverErr = cekCoverageWajah(mat, face);
        mat.release();
        if (coverErr != null) {
            return FaceDetectionResult.gagal(coverErr, fc, faceBright, imgBright);
        }

        // Step 5: Ukuran wajah
        if (face.width < MIN_FACE_WIDTH || face.height < MIN_FACE_HEIGHT) {
            return FaceDetectionResult.gagal(
                "Wajah terlalu jauh dari kamera.\nSilakan mendekat.",
                fc, faceBright, imgBright);
        }

        // Step 6: Posisi wajah di tengah
        String posErr = cekPosisi(face, image.getWidth(), image.getHeight());
        if (posErr != null)
            return FaceDetectionResult.gagal(posErr, fc, faceBright, imgBright);

        // Step 7: Tidak terpotong
        if (face.x <= 2 || face.y <= 2
                || (face.x + face.width)  >= image.getWidth()  - 2
                || (face.y + face.height) >= image.getHeight() - 2) {
            return FaceDetectionResult.gagal(
                "Wajah terpotong di tepi frame.\nMundur sedikit.",
                fc, faceBright, imgBright);
        }

        return FaceDetectionResult.sukses(fc, face, faceBright, imgBright);
    }

    // ── VALIDASI ABSENSI ─────────────────────────────────────
    /**
     * Validasi absensi — lebih longgar dari registrasi
     * tapi tetap wajib:
     * 1. Brightness gambar tidak pitch-black
     * 2. Deteksi wajah — wajib 1 wajah
     * 3. Brightness area wajah cukup
     * 4. Coverage wajah (tidak tertutup)
     *
     * Tanpa OpenCV: brightness >= 70
     */
    public static FaceDetectionResult validateAbsensi(BufferedImage image) {
        if (image == null)
            return FaceDetectionResult.gagal("Gagal mengambil gambar.");

        if (!isAvailable()) {
            double ib = hitungBrightnessManual(image);
            System.out.printf("[FaceDetection] Absensi tanpa OpenCV, brightness: %.1f%n", ib);
            if (ib < MIN_IMG_BRIGHTNESS) {
                return FaceDetectionResult.gagal(
                    "Foto terlalu gelap (" + (int)ib + "/255).\n\n" +
                    "Pastikan wajah menghadap kamera dengan jelas\n" +
                    "dan tidak ada yang menutupi wajah.",
                    0, ib, ib);
            }
            return FaceDetectionResult.sukses(1, null, ib, ib);
        }

        Mat mat = bufferedImageToMat(image);

        // Brightness gambar
        double imgBright = hitungBrightnessRegion(mat, null);
        if (imgBright < 20.0) {
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
            gray, faces, 1.1, 6, 0,
            new Size(MIN_FACE_WIDTH, MIN_FACE_HEIGHT), new Size());
        Rect[] arr = faces.toArray();
        int    fc  = arr.length;
        gray.release();
        System.out.printf("[FaceDetection] Absensi wajah: %d%n", fc);

        if (fc == 0) {
            mat.release();
            return FaceDetectionResult.gagal(
                "Wajah tidak terdeteksi.\n\n" +
                "Pastikan wajah menghadap kamera\n" +
                "dan tidak ada yang menutupi wajah.",
                0, 0, imgBright);
        }

        if (fc > 1) {
            mat.release();
            return FaceDetectionResult.gagal(
                "Terdeteksi lebih dari satu wajah.\n" +
                "Pastikan hanya Anda di depan kamera.",
                fc, 0, imgBright);
        }

        Rect face = arr[0];

        // Brightness bagian tengah wajah (40%-80% vertikal)
        Rect faceCenter = clampRect(
            new Rect(face.x + face.width/6,
                     face.y + face.height*2/5,
                     face.width*2/3,
                     face.height*2/5),
            mat.cols(), mat.rows());
        double faceBright = hitungBrightnessRegion(mat, faceCenter);
        System.out.printf("[FaceDetection] Brightness tengah wajah absensi: %.1f%n", faceBright);
        if (faceBright < MIN_FACE_BRIGHTNESS) {
            mat.release();
            return FaceDetectionResult.gagal(
                "Wajah terlalu gelap.\nHadapkan wajah ke sumber cahaya.",
                fc, faceBright, imgBright);
        }

        // Coverage wajah
        String coverErr = cekCoverageWajah(mat, face);
        mat.release();
        if (coverErr != null) {
            return FaceDetectionResult.gagal(coverErr, fc, faceBright, imgBright);
        }

        return FaceDetectionResult.sukses(fc, face, faceBright, imgBright);
    }

    // ── Brightness helpers ────────────────────────────────────

    public static double hitungBrightnessRegion(Mat mat, Rect region) {
        try {
            Mat gray = new Mat();
            if (mat.channels() == 3)
                Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
            else
                gray = mat.clone();
            Mat target;
            if (region != null) {
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
            return 128;
        }
    }

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

    // ── Cek coverage & kualitas wajah ────────────────────────
    /**
     * Validasi kualitas wajah:
     * 1. Coverage — bagian bawah wajah tidak boleh tertutup
     * 2. Pose — wajah tidak boleh terlalu miring (rasio w:h harus wajar)
     * 3. Clarity — brightness keseluruhan wajah harus cukup
     */
    private static String cekCoverageWajah(Mat mat, Rect face) {
        try {
            int faceX = face.x;
            int faceY = face.y;
            int faceW = face.width;
            int faceH = face.height;

            // ── Validasi rasio wajah ──────────────────────────
            // Wajah normal punya rasio lebar:tinggi antara 0.6 - 1.4
            // Kalau terlalu lebar atau terlalu tinggi = bukan wajah / miring ekstrem
            double rasio = (double) faceW / faceH;
            System.out.printf("[FaceDetection] Rasio wajah: %.2f%n", rasio);
            if (rasio < 0.5 || rasio > 1.6) {
                return "Wajah terdeteksi pada posisi yang tidak tepat.\n\n" +
                       "Pastikan:\n" +
                       "• Wajah menghadap langsung ke kamera\n" +
                       "• Kepala tidak terlalu miring ke kiri/kanan\n" +
                       "• Jarak cukup dekat dari kamera";
            }

            // ── Brightness seluruh area wajah ────────────────
            // Hitung brightness keseluruhan rect wajah
            double brightWajah = hitungBrightnessRegion(mat, face);
            System.out.printf("[FaceDetection] Brightness seluruh wajah: %.1f%n", brightWajah);
            if (brightWajah < 35.0) {
                return "Wajah terlalu gelap.\n\n" +
                       "Hadapkan wajah ke sumber cahaya\n" +
                       "(lampu atau jendela di depan Anda).";
            }

            // ── Coverage — bagian bawah tidak tertutup ────────
            // Zona atas (dahi + mata) vs zona bawah (hidung + mulut)
            Rect zonaAtas = clampRect(
                new Rect(faceX, faceY, faceW, faceH / 3),
                mat.cols(), mat.rows());
            Rect zonaBawah = clampRect(
                new Rect(faceX, faceY + (faceH * 2 / 3), faceW, faceH / 3),
                mat.cols(), mat.rows());

            double brightAtas  = hitungBrightnessRegion(mat, zonaAtas);
            double brightBawah = hitungBrightnessRegion(mat, zonaBawah);

            System.out.printf("[FaceDetection] Coverage — Atas: %.1f, Bawah: %.1f, Rasio: %.2f%n",
                brightAtas, brightBawah,
                brightAtas > 0 ? brightBawah / brightAtas : 0);

            // Zona bawah < 25% zona atas → kemungkinan tertutup
            if (brightAtas > 40 && brightBawah < brightAtas * 0.25) {
                return "Wajah terdeteksi tertutup sebagian.\n\n" +
                       "Pastikan seluruh wajah terlihat jelas:\n" +
                       "• Tidak ada tangan yang menutupi\n" +
                       "• Tidak ada baju yang menutupi hidung/mulut\n" +
                       "• Tidak menggunakan masker";
            }

            return null; // OK

        } catch (Exception e) {
            System.err.println("[FaceDetection] cekCoverage error: " + e.getMessage());
            return null;
        }
    }

    // ── Helper ────────────────────────────────────────────────

    private static String cekPosisi(Rect face, int fw, int fh) {
        double cx = face.x + face.width  / 2.0;
        double cy = face.y + face.height / 2.0;
        if (Math.abs(cx - fw / 2.0) > fw * CENTER_TOLERANCE
                || Math.abs(cy - fh / 2.0) > fh * CENTER_TOLERANCE)
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