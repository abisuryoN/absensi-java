package com.absensi.util;

import com.absensi.config.DatabaseConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * Utility untuk kalkulasi waktu absensi, keterlambatan, dan lembur.
 */
public class AbsensiUtil {

    // Default konfigurasi waktu (akan di-override dari database)
    private static LocalTime JAM_MASUK  = LocalTime.of(8, 0);   // 08:00
    private static LocalTime JAM_PULANG = LocalTime.of(17, 0);  // 17:00
    private static BigDecimal TARIF_LEMBUR_PER_JAM = new BigDecimal("70000");

    static {
        loadKonfigurasiWaktu();
    }

    private static void loadKonfigurasiWaktu() {
        try (Connection conn = DatabaseConfig.getConnection()) {
            String sql = "SELECT kunci, nilai FROM konfigurasi WHERE kunci IN " +
                         "('JAM_MASUK', 'JAM_PULANG', 'TARIF_LEMBUR')";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    String kunci = rs.getString("kunci");
                    String nilai = rs.getString("nilai");
                    switch (kunci) {
                        case "JAM_MASUK":    JAM_MASUK  = LocalTime.parse(nilai); break;
                        case "JAM_PULANG":   JAM_PULANG = LocalTime.parse(nilai); break;
                        case "TARIF_LEMBUR": TARIF_LEMBUR_PER_JAM = new BigDecimal(nilai); break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[AbsensiUtil] Menggunakan konfigurasi waktu default: " + e.getMessage());
        }
    }

    /**
     * Cek apakah waktu saat ini sudah boleh absen masuk.
     * Absen ditolak jika sebelum jam 08:00.
     *
     * @return true jika sudah boleh absen
     */
    public static boolean isBolehAbsen() {
        LocalTime sekarang = LocalTime.now();
        return !sekarang.isBefore(JAM_MASUK);
    }

    /**
     * Hitung keterlambatan dalam menit.
     * Jika tepat waktu atau lebih awal, return 0.
     *
     * @param jamMasuk Waktu absen masuk
     * @return Durasi keterlambatan dalam menit
     */
    public static int hitungKeterlambatan(LocalTime jamMasuk) {
        if (jamMasuk.isAfter(JAM_MASUK)) {
            return (int) ChronoUnit.MINUTES.between(JAM_MASUK, jamMasuk);
        }
        return 0;
    }

    /**
     * Cek apakah karyawan terlambat.
     */
    public static boolean isTerlambat(LocalTime jamMasuk) {
        return jamMasuk.isAfter(JAM_MASUK);
    }

    /**
     * Hitung durasi lembur dalam menit.
     * Lembur dihitung jika pulang setelah jam 17:00.
     *
     * @param jamKeluar Waktu absen keluar
     * @return Durasi lembur dalam menit
     */
    public static int hitungLembur(LocalTime jamKeluar) {
        if (jamKeluar.isAfter(JAM_PULANG)) {
            return (int) ChronoUnit.MINUTES.between(JAM_PULANG, jamKeluar);
        }
        return 0;
    }

    /**
     * Hitung uang lembur berdasarkan durasi lembur.
     * Tarif: Rp70.000 per jam (dihitung per menit, bukan bulatkan ke jam).
     *
     * @param durasiLemburMenit Durasi lembur dalam menit
     * @return Nominal uang lembur dalam Rupiah
     */
    public static BigDecimal hitungUangLembur(int durasiLemburMenit) {
        if (durasiLemburMenit <= 0) return BigDecimal.ZERO;

        // Hitung per menit: tarif_per_jam / 60 * durasi_menit
        BigDecimal tarifPerMenit = TARIF_LEMBUR_PER_JAM.divide(new BigDecimal("60"), 4, RoundingMode.HALF_UP);
        return tarifPerMenit.multiply(new BigDecimal(durasiLemburMenit)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Mendapatkan waktu saat ini dalam format Time SQL.
     */
    public static Time getCurrentTimeSql() {
        LocalTime now = LocalTime.now();
        return Time.valueOf(now);
    }

    /**
     * Mendapatkan tanggal hari ini dalam format Date SQL.
     */
    public static java.sql.Date getCurrentDateSql() {
        return java.sql.Date.valueOf(java.time.LocalDate.now());
    }

    /**
     * Format durasi dalam menit ke string yang mudah dibaca.
     */
    public static String formatDurasi(int totalMenit) {
        if (totalMenit <= 0) return "-";
        int jam   = totalMenit / 60;
        int menit = totalMenit % 60;
        if (jam > 0) return jam + " jam " + menit + " menit";
        return menit + " menit";
    }

    /**
     * Getter untuk konfigurasi waktu.
     */
    public static LocalTime getJamMasuk()  { return JAM_MASUK; }
    public static LocalTime getJamPulang() { return JAM_PULANG; }
    public static BigDecimal getTarifLembur() { return TARIF_LEMBUR_PER_JAM; }
}