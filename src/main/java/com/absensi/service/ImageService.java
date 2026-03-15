package com.absensi.service;

import com.absensi.util.FaceDetectionUtil;
import com.absensi.util.ImageUtil;
import com.absensi.util.ImageValidationUtil;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;

/**
 * Service untuk pemrosesan dan penyimpanan foto karyawan.
 *
 * Alur registrasi:
 *   Foto webcam → validasi wajah → resize → kompres → simpan ke disk → return path
 *
 * Alur absensi:
 *   Foto webcam → validasi cepat → simpan sementara → face verification → hapus jika gagal
 */
public class ImageService {

    private static final int    TARGET_SIZE  = 480;
    private static final float  JPEG_QUALITY = 0.90f;
    private static final String PHOTOS_DIR   = "photos";

    // ── Hasil penyimpanan foto ────────────────────────────────

    public static class SaveResult {
        public final boolean valid;
        public final String  message;
        public final String  path;

        public SaveResult(boolean valid, String message, String path) {
            this.valid   = valid;
            this.message = message;
            this.path    = path;
        }
    }

    // ── Simpan foto profil (registrasi) ──────────────────────

    /**
     * Validasi + simpan foto profil karyawan.
     * Melakukan face detection sebelum menyimpan.
     *
     * @param fotoBytes   Raw bytes dari webcam
     * @param idKaryawan  ID karyawan (dipakai sebagai nama file)
     * @return SaveResult dengan path atau pesan error
     */
    public SaveResult simpanFotoProfil(byte[] fotoBytes, String idKaryawan) {
        // ── 1. Decode bytes → BufferedImage ───────────────
        BufferedImage image = decodeBytes(fotoBytes);
        if (image == null) {
            return new SaveResult(false,
                "Gagal membaca gambar dari kamera.\nCoba ambil foto ulang.", null);
        }

        // ── 2. Validasi wajah (face detection) ────────────
        ImageValidationUtil.ValidationResult validasi =
            ImageValidationUtil.validateRegistrasi(image);

        if (!validasi.valid) {
            return new SaveResult(false, validasi.message, null);
        }

        // ── 3. Resize + kompres + simpan ──────────────────
        try {
            Files.createDirectories(Paths.get(PHOTOS_DIR));
            String namaFile = idKaryawan + ".jpg";
            Path   filePath = Paths.get(PHOTOS_DIR, namaFile);

            BufferedImage resized = resizeCropCenter(image, TARGET_SIZE, TARGET_SIZE);
            simpanJpeg(resized, filePath.toFile(), JPEG_QUALITY);

            long kb = Files.size(filePath) / 1024;
            System.out.printf("[ImageService] Profil disimpan: %s (%d KB)%n", filePath, kb);

            return new SaveResult(true, "Foto berhasil disimpan.",
                filePath.toString().replace("\\", "/"));

        } catch (IOException e) {
            return new SaveResult(false,
                "Gagal menyimpan foto: " + e.getMessage(), null);
        }
    }

    /**
     * Simpan foto absensi (masuk/keluar).
     * Validasi lebih ringan dari registrasi.
     *
     * @param fotoBytes    Raw bytes dari webcam
     * @param idKaryawan   ID karyawan
     * @param jenisAbsensi "masuk" atau "keluar"
     * @return SaveResult dengan path
     */
    public SaveResult simpanFotoAbsensi(byte[] fotoBytes, String idKaryawan,
                                         String jenisAbsensi) {
        BufferedImage image = decodeBytes(fotoBytes);
        if (image == null) {
            return new SaveResult(false,
                "Gagal membaca gambar dari kamera.", null);
        }

        // Validasi absensi (brightness + jumlah wajah)
        ImageValidationUtil.ValidationResult validasi =
            ImageValidationUtil.validateAbsensi(image);

        if (!validasi.valid) {
            return new SaveResult(false, validasi.message, null);
        }

        try {
            String tanggal  = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String subDir   = PHOTOS_DIR + "/absensi/" +
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
            Files.createDirectories(Paths.get(subDir));

            String namaFile = idKaryawan + "_" + jenisAbsensi + "_" + tanggal + ".jpg";
            Path   filePath = Paths.get(subDir, namaFile);

            BufferedImage resized = resizeCropCenter(image, TARGET_SIZE, TARGET_SIZE);
            simpanJpeg(resized, filePath.toFile(), JPEG_QUALITY);

            return new SaveResult(true, "OK",
                filePath.toString().replace("\\", "/"));

        } catch (IOException e) {
            return new SaveResult(false,
                "Gagal menyimpan foto absensi: " + e.getMessage(), null);
        }
    }

    // ── Helper ───────────────────────────────────────────────

    private BufferedImage decodeBytes(byte[] bytes) {
        if (bytes == null) return null;
        try (java.io.ByteArrayInputStream bais =
                 new java.io.ByteArrayInputStream(bytes)) {
            return ImageIO.read(bais);
        } catch (IOException e) {
            return null;
        }
    }

    private BufferedImage resizeCropCenter(BufferedImage src, int targetW, int targetH) {
        int srcW = src.getWidth(), srcH = src.getHeight();
        double scale = Math.max((double) targetW / srcW, (double) targetH / srcH);
        int scaledW  = (int) Math.ceil(srcW * scale);
        int scaledH  = (int) Math.ceil(srcH * scale);

        BufferedImage scaled = new BufferedImage(scaledW, scaledH,
            BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, scaledW, scaledH, null);
        g.dispose();

        int cropX = (scaledW - targetW) / 2;
        int cropY = (scaledH - targetH) / 2;
        return scaled.getSubimage(cropX, cropY, targetW, targetH);
    }

    private void simpanJpeg(BufferedImage img, File file, float quality)
            throws IOException {
        // Pastikan mode RGB
        BufferedImage rgb = new BufferedImage(
            img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.drawImage(img, 0, 0, null);
        g.dispose();

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IOException("Tidak ada JPEG writer");
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);

        try (FileImageOutputStream out = new FileImageOutputStream(file)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}