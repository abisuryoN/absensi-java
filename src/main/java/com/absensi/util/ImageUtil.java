package com.absensi.util;

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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;

public class ImageUtil {

    //  Konfigurasi 
    private static final int    TARGET_WIDTH   = 480;
    private static final int    TARGET_HEIGHT  = 480;
    private static final float  JPEG_QUALITY   = 0.90f;   
    private static final String PHOTOS_DIR     = "photos"; 

    
    //  SIMPAN FOTO ABSENSI

    public static String simpanFotoAbsensi(byte[] fotoBytes, String idKaryawan, String jenisAbsensi) {
        if (fotoBytes == null || fotoBytes.length == 0) return null;

        try {
            // 1. Decode bytes → BufferedImage
            BufferedImage original = decodeBytes(fotoBytes);
            if (original == null) return null;

            // 2. Resize ke 300×300 (crop tengah agar tidak distorsi)
            BufferedImage resized = resizeCropCenter(original, TARGET_WIDTH, TARGET_HEIGHT);

            // 3. Pastikan folder /photos/ ada
            Path photosPath = Paths.get(PHOTOS_DIR);
            Files.createDirectories(photosPath);

            // 4. Buat nama file: EMP001_masuk_20260315.jpg
            String timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

            String namaFile = idKaryawan + "_" + jenisAbsensi + "_" + timestamp + ".jpg";
            Path   filePath  = photosPath.resolve(namaFile);

            // 5. Kompres dan simpan sebagai JPEG
            simpanJpeg(resized, filePath.toFile(), JPEG_QUALITY);

            long ukuranKB = Files.size(filePath) / 1024;
            System.out.printf("[ImageUtil] Foto disimpan: %s (%d KB)%n", filePath, ukuranKB);

            // Kembalikan path relatif (disimpan ke DB)
            return filePath.toString().replace("\\", "/");

        } catch (IOException e) {
            System.err.println("[ImageUtil] Gagal simpan foto: " + e.getMessage());
            return null;
        }
    }

    /**
     * Simpan foto profil karyawan saat registrasi.
     *
     * @param fotoBytes   Raw bytes foto selfie
     * @param idKaryawan  ID karyawan
     * @return Path relatif file (contoh: "photos/profil_EMP001.jpg")
     */
    public static String simpanFotoProfil(byte[] fotoBytes, String idKaryawan) {
        if (fotoBytes == null || fotoBytes.length == 0) return null;

        try {
            BufferedImage original = decodeBytes(fotoBytes);
            if (original == null) return null;

            BufferedImage resized = resizeCropCenter(original, TARGET_WIDTH, TARGET_HEIGHT);

            LocalDate now = LocalDate.now();

            Path photosPath = Paths.get(
            PHOTOS_DIR,
            String.valueOf(now.getYear()),
            String.format("%02d", now.getMonthValue())
            );

            Files.createDirectories(photosPath);

            String namaFile = "profil_" + idKaryawan + ".jpg";
            Path   filePath = photosPath.resolve(namaFile);

            simpanJpeg(resized, filePath.toFile(), JPEG_QUALITY);

            long ukuranKB = Files.size(filePath) / 1024;
            System.out.printf("[ImageUtil] Foto profil: %s (%d KB)%n", filePath, ukuranKB);

            return filePath.toString().replace("\\", "/");

        } catch (IOException e) {
            System.err.println("[ImageUtil] Gagal simpan foto profil: " + e.getMessage());
            return null;
        }
    }

    // =========================================================
    //  LOAD FOTO DARI PATH
    // =========================================================

    /**
     * Muat foto dari path yang disimpan di database.
     *
     * @param pathFoto Path relatif (contoh: "photos/EMP001_masuk_20260315.jpg")
     * @return BufferedImage atau null jika file tidak ada
     */
    public static BufferedImage loadFoto(String pathFoto) {
        if (pathFoto == null || pathFoto.isBlank()) return null;

        File file = new File(pathFoto);
        if (!file.exists()) {
            System.err.println("[ImageUtil] File tidak ditemukan: " + pathFoto);
            return null;
        }

        try {
            return ImageIO.read(file);
        } catch (IOException e) {
            System.err.println("[ImageUtil] Gagal load foto: " + e.getMessage());
            return null;
        }
    }

    /**
     * Muat foto sebagai ImageIcon siap pakai di Swing (dengan ukuran tampil tertentu).
     *
     * @param pathFoto   Path dari database
     * @param lebarTampil Lebar display (misal 80 untuk thumbnail sidebar)
     * @param tinggiTampil Tinggi display
     * @return ImageIcon atau null
     */
    public static javax.swing.ImageIcon loadSebagaiIcon(String pathFoto, int lebarTampil, int tinggiTampil) {
        BufferedImage img = loadFoto(pathFoto);
        if (img == null) return null;

        Image scaled = img.getScaledInstance(lebarTampil, tinggiTampil, Image.SCALE_SMOOTH);
        return new javax.swing.ImageIcon(scaled);
    }

    // =========================================================
    //  METODE PRIVAT: RESIZE & KOMPRES
    // =========================================================

    /**
     * Resize gambar dengan crop tengah (center crop) agar proporsional.
     * Tidak distorsi — mirip dengan "object-fit: cover" di CSS.
     *
     * Langkah:
     * 1. Tentukan sisi mana yang lebih besar (lebar atau tinggi)
     * 2. Scale gambar sehingga sisi terkecil tepat = target
     * 3. Crop bagian tengah
     */
    private static BufferedImage resizeCropCenter(BufferedImage src, int targetW, int targetH) {
        int srcW = src.getWidth();
        int srcH = src.getHeight();

        // Hitung scale agar sisi terpendek = target
        double scaleW = (double) targetW / srcW;
        double scaleH = (double) targetH / srcH;
        double scale  = Math.max(scaleW, scaleH);

        int scaledW = (int) Math.ceil(srcW * scale);
        int scaledH = (int) Math.ceil(srcH * scale);

        // Langkah 1: Scale gambar
        BufferedImage scaled = new BufferedImage(scaledW, scaledH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, scaledW, scaledH, null);
        g.dispose();

        // Langkah 2: Crop bagian tengah
        int cropX = (scaledW - targetW) / 2;
        int cropY = (scaledH - targetH) / 2;
        return scaled.getSubimage(cropX, cropY, targetW, targetH);
    }

    /**
     * Simpan BufferedImage sebagai JPEG dengan kualitas tertentu.
     * Menggunakan ImageWriter untuk kontrol kualitas (bukan ImageIO.write biasa).
     *
     * @param img     Gambar yang akan disimpan
     * @param file    File tujuan
     * @param quality 0.0 (sangat kecil, jelek) sampai 1.0 (terbesar, bagus)
     */
    private static void simpanJpeg(BufferedImage img, File file, float quality) throws IOException {
        // Pastikan gambar dalam mode RGB (JPEG tidak support alpha/ARGB)
        BufferedImage rgbImg = img;
        if (img.getType() != BufferedImage.TYPE_INT_RGB) {
            rgbImg = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgbImg.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.drawImage(img, 0, 0, null);
            g.dispose();
        }

        // Cari JPEG ImageWriter
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IOException("Tidak ada JPEG ImageWriter");

        ImageWriter     writer = writers.next();
        ImageWriteParam param  = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);

        try (FileImageOutputStream output = new FileImageOutputStream(file)) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(rgbImg, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    /**
     * Decode byte[] menjadi BufferedImage.
     */
    private static BufferedImage decodeBytes(byte[] bytes) throws IOException {
        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(bytes)) {
            return ImageIO.read(bais);
        }
    }

    // =========================================================
    //  UTILITAS TAMBAHAN
    // =========================================================

    /**
     * Hapus foto lama jika ada (misal saat update foto profil).
     * @param pathFoto
     */
    public static void hapusFoto(String pathFoto) {
        if (pathFoto == null || pathFoto.isBlank()) return;
        File file = new File(pathFoto);
        if (file.exists()) {
            boolean deleted = file.delete();
            System.out.printf("[ImageUtil] Hapus %s: %s%n", pathFoto, deleted ? "OK" : "GAGAL");
        }
    }

    /**
     * Cek ukuran file foto dalam KB.
     * @param pathFoto
     * @return 
     */
    public static long getUkuranKB(String pathFoto) {
        if (pathFoto == null) return 0;
        File file = new File(pathFoto);
        return file.exists() ? file.length() / 1024 : 0;
    }
}