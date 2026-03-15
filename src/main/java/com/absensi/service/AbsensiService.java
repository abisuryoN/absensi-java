package com.absensi.service;

import com.absensi.dao.AbsensiDAO;
import com.absensi.model.Absensi;
import com.absensi.model.Karyawan;
import com.absensi.util.AbsensiUtil;
import com.absensi.util.FaceDetectionUtil;
import com.absensi.util.FaceRecognitionUtil;
import com.absensi.util.ImageUtil;
import com.absensi.util.ImageValidationUtil;
import com.absensi.util.LocationUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class AbsensiService {

    private final AbsensiDAO absensiDAO;

    public AbsensiService() {
        this.absensiDAO = new AbsensiDAO();
    }

    public AbsensiService(AbsensiDAO absensiDAO) {
        this.absensiDAO = absensiDAO;
    }

    // =========================================================
    //  INNER CLASS: HasilAbsensi
    // =========================================================

    public static class HasilAbsensi {
        private final boolean    sukses;
        private final String     pesan;
        private final Absensi    absensi;
        private final boolean    terlambat;
        private final int        menitTerlambat;
        private final boolean    lembur;
        private final int        menitLembur;
        private final BigDecimal uangLembur;

        public HasilAbsensi(boolean sukses, String pesan, Absensi absensi,
                             boolean terlambat, int menitTerlambat,
                             boolean lembur, int menitLembur,
                             BigDecimal uangLembur) {
            this.sukses         = sukses;
            this.pesan          = pesan;
            this.absensi        = absensi;
            this.terlambat      = terlambat;
            this.menitTerlambat = menitTerlambat;
            this.lembur         = lembur;
            this.menitLembur    = menitLembur;
            this.uangLembur     = uangLembur;
        }

        public static HasilAbsensi gagal(String pesan) {
            return new HasilAbsensi(false, pesan, null,
                false, 0, false, 0, BigDecimal.ZERO);
        }

        public boolean    isSukses()          { return sukses; }
        public String     getPesan()          { return pesan; }
        public Absensi    getAbsensi()        { return absensi; }
        public boolean    isTerlambat()       { return terlambat; }
        public int        getMenitTerlambat() { return menitTerlambat; }
        public boolean    isLembur()          { return lembur; }
        public int        getMenitLembur()    { return menitLembur; }
        public BigDecimal getUangLembur()     { return uangLembur; }

        public String getRingkasan() {
            if (!sukses) return pesan;
            StringBuilder sb = new StringBuilder();
            sb.append(pesan).append("\n");
            if (absensi != null && absensi.getJamMasuk() != null)
                sb.append("Jam Masuk  : ").append(absensi.getJamMasuk()).append("\n");
            if (absensi != null && absensi.getJamKeluar() != null)
                sb.append("Jam Keluar : ").append(absensi.getJamKeluar()).append("\n");
            if (terlambat)
                sb.append("Terlambat  : ")
                  .append(AbsensiUtil.formatDurasi(menitTerlambat)).append("\n");
            if (lembur)
                sb.append("Lembur     : ")
                  .append(AbsensiUtil.formatDurasi(menitLembur))
                  .append(" (")
                  .append(com.absensi.util.CurrencyUtil.formatRupiahBulat(uangLembur))
                  .append(")");
            return sb.toString().trim();
        }
    }

    // =========================================================
    //  ABSENSI MASUK (dengan Face Verification)
    // =========================================================

    /**
     * Proses absensi masuk DENGAN face verification.
     * Urutan validasi:
     *  1. Cek waktu >= jam masuk
     *  2. Cek absensi ganda
     *  3. Cek foto ada
     *  4. Validasi brightness + jumlah wajah
     *  5. Face verification vs foto registrasi
     *  6. Cek lokasi / geofencing
     *  7. Simpan ke database
     */
    public HasilAbsensi absenMasuk(Karyawan karyawan, byte[] fotoBytes,
                                    Double latitude, Double longitude,
                                    boolean lokasiManual) {
        // ── 1. Cek waktu ──────────────────────────────────────
        LocalTime sekarang = LocalTime.now();
        if (!AbsensiUtil.isBolehAbsen()) {
            return HasilAbsensi.gagal(
                "Belum waktunya absen masuk!\n\n" +
                "Absensi masuk dibuka mulai pukul " +
                AbsensiUtil.getJamMasuk() + " WIB.\n" +
                "Waktu sekarang: " +
                sekarang.format(DateTimeFormatter.ofPattern("HH:mm")) + " WIB."
            );
        }

        // ── 2. Cek absensi ganda ──────────────────────────────
        try {
            Absensi sudahAbsen = absensiDAO.findToday(karyawan.getIdKaryawan());
            if (sudahAbsen != null && sudahAbsen.sudahAbsenMasuk()) {
                return HasilAbsensi.gagal(
                    "Anda sudah melakukan absensi masuk hari ini.\n" +
                    "Jam masuk tercatat: " + sudahAbsen.getJamMasuk() + "\n\n" +
                    "Jika ada masalah, hubungi HRD."
                );
            }
        } catch (SQLException e) {
            return HasilAbsensi.gagal(
                "Gagal memeriksa status absensi: " + e.getMessage());
        }

        // ── 3. Cek foto ───────────────────────────────────────
        if (fotoBytes == null || fotoBytes.length == 0)
            return HasilAbsensi.gagal(
                "Foto selfie tidak ditemukan. Silakan ambil ulang.");

        // ── 4. Validasi wajah (brightness + jumlah wajah) ────
        BufferedImage image = decodeFoto(fotoBytes);
        if (image == null)
            return HasilAbsensi.gagal(
                "Gagal membaca gambar dari kamera. Coba ambil foto ulang.");

        ImageValidationUtil.ValidationResult imgValid =
            ImageValidationUtil.validateAbsensi(image);
        if (!imgValid.valid)
            return HasilAbsensi.gagal(imgValid.message);

        // ── 5. Face verification vs foto registrasi ───────────
        String pathFotoRegistrasi = karyawan.getFotoProfil();
        if (pathFotoRegistrasi != null
                && !pathFotoRegistrasi.isBlank()
                && FaceDetectionUtil.isAvailable()) {

            FaceRecognitionUtil.VerificationResult verif =
                FaceRecognitionUtil.verify(pathFotoRegistrasi, image);

            if (!verif.match) {
                return HasilAbsensi.gagal(
                    "Wajah tidak sesuai dengan data registrasi.\n" +
                    "Absensi ditolak.\n\n" +
                    String.format("Kemiripan wajah: %d%%",
                        verif.getSimilarityPercent())
                );
            }
            System.out.printf("[AbsensiService] Face match OK — " +
                "confidence=%.1f, similarity=%d%%%n",
                verif.confidence, verif.getSimilarityPercent());
        }

        // ── 6. Lokasi / geofencing ────────────────────────────
        String lokasiStr;
        if (!lokasiManual) {
            if (latitude == null || longitude == null)
                return HasilAbsensi.gagal(
                    "Lokasi tidak dapat dideteksi.\n" +
                    "Pastikan koneksi internet aktif dan coba lagi."
                );

            if (!LocationUtil.isInKantor(latitude, longitude)) {
                double jarak = LocationUtil.hitungJarak(
                    latitude, longitude,
                    LocationUtil.getKantorLatitude(),
                    LocationUtil.getKantorLongitude()
                );
                return HasilAbsensi.gagal(String.format(
                    "Anda berada di luar area kantor!\n\n" +
                    "Jarak dari kantor : %.0f meter\n" +
                    "Radius diizinkan  : %.0f meter\n\n" +
                    "Absensi hanya dapat dilakukan di area kantor.",
                    jarak, LocationUtil.getRadiusMeter()
                ));
            }
            lokasiStr = LocationUtil.formatKoordinat(latitude, longitude);
        } else {
            lokasiStr = "Konfirmasi Manual";
        }

        // ── 7. Kalkulasi keterlambatan ────────────────────────
        boolean terlambat      = AbsensiUtil.isTerlambat(sekarang);
        int     menitTerlambat = AbsensiUtil.hitungKeterlambatan(sekarang);

        // ── 8. Simpan foto absensi ke disk ────────────────────
        String pathFotoMasuk = ImageUtil.simpanFotoAbsensi(
            fotoBytes, karyawan.getIdKaryawan(), "masuk");

        // ── 9. Simpan ke database ─────────────────────────────
        try {
            Absensi absensi = new Absensi();
            absensi.setIdKaryawan(karyawan.getIdKaryawan());
            absensi.setTanggal(AbsensiUtil.getCurrentDateSql());
            absensi.setJamMasuk(AbsensiUtil.getCurrentTimeSql());
            absensi.setPathFotoMasuk(pathFotoMasuk);
            absensi.setLokasiMasuk(lokasiStr);
            absensi.setStatusTelat(terlambat);
            absensi.setDurasiTelat(menitTerlambat);
            absensi.setKeterangan(terlambat
                ? "Terlambat " + AbsensiUtil.formatDurasi(menitTerlambat)
                : "Tepat waktu");

            boolean saved = absensiDAO.absenMasuk(absensi);
            if (!saved)
                return HasilAbsensi.gagal(
                    "Gagal menyimpan absensi. Hubungi administrator.");

            String pesan = terlambat
                ? "Absensi masuk dicatat.\nAnda terlambat " +
                  AbsensiUtil.formatDurasi(menitTerlambat) + "."
                : "Absensi masuk berhasil. Selamat bekerja!";

            return new HasilAbsensi(true, pesan, absensi,
                terlambat, menitTerlambat, false, 0, BigDecimal.ZERO);

        } catch (SQLException e) {
            return HasilAbsensi.gagal(
                "Kesalahan database saat menyimpan absensi: " + e.getMessage());
        }
    }

    // =========================================================
    //  ABSENSI KELUAR (dengan Face Verification)
    // =========================================================

    /**
     * Proses absensi keluar DENGAN face verification.
     * Urutan validasi:
     *  1. Harus sudah absen masuk
     *  2. Belum absen keluar
     *  3. Foto ada
     *  4. Validasi brightness + jumlah wajah
     *  5. Face verification vs foto registrasi
     *  6. Lokasi / geofencing
     *  7. Hitung lembur + simpan
     */
    public HasilAbsensi absenKeluar(Karyawan karyawan, byte[] fotoBytes,
                                     Double latitude, Double longitude,
                                     boolean lokasiManual) {
        // ── 1. Harus sudah absen masuk ────────────────────────
        Absensi absensiHariIni;
        try {
            absensiHariIni = absensiDAO.findToday(karyawan.getIdKaryawan());
        } catch (SQLException e) {
            return HasilAbsensi.gagal(
                "Gagal memeriksa status absensi: " + e.getMessage());
        }

        if (absensiHariIni == null || !absensiHariIni.sudahAbsenMasuk())
            return HasilAbsensi.gagal(
                "Anda belum melakukan absensi masuk hari ini.\n" +
                "Lakukan absensi masuk terlebih dahulu."
            );

        // ── 2. Belum absen keluar ─────────────────────────────
        if (absensiHariIni.sudahAbsenKeluar())
            return HasilAbsensi.gagal(
                "Anda sudah melakukan absensi keluar hari ini.\n" +
                "Jam keluar tercatat: " + absensiHariIni.getJamKeluar()
            );

        // ── 3. Cek foto ───────────────────────────────────────
        if (fotoBytes == null || fotoBytes.length == 0)
            return HasilAbsensi.gagal(
                "Foto selfie tidak ditemukan. Silakan ambil ulang.");

        // ── 4. Validasi wajah ─────────────────────────────────
        BufferedImage image = decodeFoto(fotoBytes);
        if (image == null)
            return HasilAbsensi.gagal(
                "Gagal membaca gambar dari kamera. Coba ambil foto ulang.");

        ImageValidationUtil.ValidationResult imgValid =
            ImageValidationUtil.validateAbsensi(image);
        if (!imgValid.valid)
            return HasilAbsensi.gagal(imgValid.message);

        // ── 5. Face verification ──────────────────────────────
        String pathFotoRegistrasi = karyawan.getFotoProfil();
        if (pathFotoRegistrasi != null
                && !pathFotoRegistrasi.isBlank()
                && FaceDetectionUtil.isAvailable()) {

            FaceRecognitionUtil.VerificationResult verif =
                FaceRecognitionUtil.verify(pathFotoRegistrasi, image);

            if (!verif.match) {
                return HasilAbsensi.gagal(
                    "Wajah tidak sesuai dengan data registrasi.\n" +
                    "Absensi keluar ditolak.\n\n" +
                    String.format("Kemiripan wajah: %d%%",
                        verif.getSimilarityPercent())
                );
            }
        }

        // ── 6. Lokasi ─────────────────────────────────────────
        String lokasiStr;
        if (!lokasiManual) {
            if (latitude == null || longitude == null)
                return HasilAbsensi.gagal(
                    "Lokasi tidak dapat dideteksi. " +
                    "Pastikan koneksi internet aktif.");

            if (!LocationUtil.isInKantor(latitude, longitude)) {
                double jarak = LocationUtil.hitungJarak(
                    latitude, longitude,
                    LocationUtil.getKantorLatitude(),
                    LocationUtil.getKantorLongitude()
                );
                return HasilAbsensi.gagal(String.format(
                    "Anda berada di luar area kantor!\n\n" +
                    "Jarak dari kantor: %.0f meter (maks %.0f meter).\n" +
                    "Absensi keluar harus dilakukan di area kantor.",
                    jarak, LocationUtil.getRadiusMeter()
                ));
            }
            lokasiStr = LocationUtil.formatKoordinat(latitude, longitude);
        } else {
            lokasiStr = "Konfirmasi Manual";
        }

        // ── 7. Kalkulasi lembur ───────────────────────────────
        LocalTime sekarang    = LocalTime.now();
        int       menitLembur = AbsensiUtil.hitungLembur(sekarang);
        BigDecimal uangLembur = AbsensiUtil.hitungUangLembur(menitLembur);
        boolean   adaLembur   = menitLembur > 0;

        // ── 8. Simpan foto absensi keluar ─────────────────────
        String pathFotoKeluar = ImageUtil.simpanFotoAbsensi(
            fotoBytes, karyawan.getIdKaryawan(), "keluar");

        // ── 9. Simpan ke database ─────────────────────────────
        try {
            absensiHariIni.setJamKeluar(AbsensiUtil.getCurrentTimeSql());
            absensiHariIni.setPathFotoKeluar(pathFotoKeluar);
            absensiHariIni.setLokasiKeluar(lokasiStr);
            absensiHariIni.setDurasiLembur(menitLembur);
            absensiHariIni.setUangLembur(uangLembur);

            boolean saved = absensiDAO.absenKeluar(absensiHariIni);
            if (!saved)
                return HasilAbsensi.gagal(
                    "Gagal menyimpan absensi keluar. Hubungi administrator.");

            String pesan = adaLembur
                ? "Absensi keluar berhasil.\nAnda lembur " +
                  AbsensiUtil.formatDurasi(menitLembur) + "."
                : "Absensi keluar berhasil. Selamat beristirahat!";

            return new HasilAbsensi(true, pesan, absensiHariIni,
                absensiHariIni.isStatusTelat(),
                absensiHariIni.getDurasiTelat(),
                adaLembur, menitLembur, uangLembur);

        } catch (SQLException e) {
            return HasilAbsensi.gagal(
                "Kesalahan database saat absen keluar: " + e.getMessage());
        }
    }

    // =========================================================
    //  STATUS & LAPORAN
    // =========================================================

    public Absensi getStatusHariIni(String idKaryawan) throws SQLException {
        return absensiDAO.findToday(idKaryawan);
    }

    public List<Absensi> getRiwayatKaryawan(String idKaryawan,
            int bulan, int tahun) throws SQLException {
        return absensiDAO.findByKaryawanAndPeriode(idKaryawan, bulan, tahun);
    }

    public List<Absensi> getSemuaAbsensiPeriode(int bulan, int tahun)
            throws SQLException {
        return absensiDAO.findAllByPeriode(bulan, tahun);
    }

    public List<Absensi> getLaporanKeterlambatan(int bulan, int tahun)
            throws SQLException {
        return absensiDAO.findLateByPeriode(bulan, tahun);
    }

    public List<Absensi> getLaporanLembur(int bulan, int tahun)
            throws SQLException {
        return absensiDAO.findOvertimeByPeriode(bulan, tahun);
    }

    public int[] getStatistikBulanan(String idKaryawan,
            int bulan, int tahun) throws SQLException {
        return absensiDAO.hitungStatistik(idKaryawan, bulan, tahun);
    }

    public BigDecimal getTotalLemburPeriode(String idKaryawan,
            int bulan, int tahun) throws SQLException {
        return absensiDAO.hitungTotalLembur(idKaryawan, bulan, tahun);
    }

    // =========================================================
    //  HELPER
    // =========================================================

    private BufferedImage decodeFoto(byte[] bytes) {
        if (bytes == null) return null;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            return ImageIO.read(bais);
        } catch (IOException e) {
            return null;
        }
    }
}