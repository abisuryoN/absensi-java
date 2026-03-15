package com.absensi.dao;

import com.absensi.config.DatabaseConfig;
import com.absensi.model.Absensi;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO untuk operasi database tabel absensi.
 */
public class AbsensiDAO {

    /**
     * Simpan absensi masuk.
     */
    public boolean absenMasuk(Absensi absensi) throws SQLException {
        String sql = "INSERT INTO absensi (id_karyawan, tanggal, jam_masuk, path_foto_masuk, " +
             "lokasi_masuk, status_telat, durasi_telat, keterangan) " +
             "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
             "ON DUPLICATE KEY UPDATE " +
             "jam_masuk = VALUES(jam_masuk), path_foto_masuk = VALUES(path_foto_masuk), " +
             "lokasi_masuk = VALUES(lokasi_masuk), status_telat = VALUES(status_telat), " +
             "durasi_telat = VALUES(durasi_telat), keterangan = VALUES(keterangan)";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, absensi.getIdKaryawan());
            ps.setDate(2, absensi.getTanggal());
            ps.setTime(3, absensi.getJamMasuk());
            ps.setString(4, absensi.getPathFotoMasuk());
            ps.setString(5, absensi.getLokasiMasuk());
            ps.setBoolean(6, absensi.isStatusTelat());
            ps.setInt(7, absensi.getDurasiTelat());
            ps.setString(8, absensi.getKeterangan());

            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Update absensi keluar.
     */
    public boolean absenKeluar(Absensi absensi) throws SQLException {
        String sql = "UPDATE absensi SET jam_keluar = ?, path_foto_keluar = ?, " +
             "lokasi_keluar = ?, durasi_lembur = ?, uang_lembur = ? " +
             "WHERE id_karyawan = ? AND tanggal = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTime(1, absensi.getJamKeluar());
            ps.setString(2, absensi.getPathFotoKeluar());
            ps.setString(3, absensi.getLokasiKeluar());
            ps.setInt(4, absensi.getDurasiLembur());
            ps.setBigDecimal(5, absensi.getUangLembur());
            ps.setString(6, absensi.getIdKaryawan());
            ps.setDate(7, absensi.getTanggal());

            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Cari absensi karyawan hari ini.
     */
    public Absensi findToday(String idKaryawan) throws SQLException {
        String sql = "SELECT a.*, k.nama AS nama_karyawan FROM absensi a " +
                     "JOIN karyawan k ON a.id_karyawan = k.id_karyawan " +
                     "WHERE a.id_karyawan = ? AND a.tanggal = CURDATE()";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, idKaryawan);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapResultSet(rs);
            }
        }
        return null;
    }

    /**
     * Ambil riwayat absensi karyawan berdasarkan bulan dan tahun.
     */
    public List<Absensi> findByKaryawanAndPeriode(String idKaryawan,
                                                   int bulan, int tahun) throws SQLException {
        List<Absensi> list = new ArrayList<>();
        String sql = "SELECT a.*, k.nama AS nama_karyawan FROM absensi a " +
                     "JOIN karyawan k ON a.id_karyawan = k.id_karyawan " +
                     "WHERE a.id_karyawan = ? AND MONTH(a.tanggal) = ? AND YEAR(a.tanggal) = ? " +
                     "ORDER BY a.tanggal DESC";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, idKaryawan);
            ps.setInt(2, bulan);
            ps.setInt(3, tahun);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    /**
     * Ambil semua absensi untuk periode tertentu (laporan HRD).
     */
    public List<Absensi> findAllByPeriode(int bulan, int tahun) throws SQLException {
        List<Absensi> list = new ArrayList<>();
        String sql = "SELECT a.*, k.nama AS nama_karyawan FROM absensi a " +
                     "JOIN karyawan k ON a.id_karyawan = k.id_karyawan " +
                     "WHERE MONTH(a.tanggal) = ? AND YEAR(a.tanggal) = ? " +
                     "ORDER BY a.tanggal DESC, k.nama ASC";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, bulan);
            ps.setInt(2, tahun);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    /**
     * Laporan keterlambatan: ambil absensi yang telat pada periode tertentu.
     */
    public List<Absensi> findLateByPeriode(int bulan, int tahun) throws SQLException {
        List<Absensi> list = new ArrayList<>();
        String sql = "SELECT a.*, k.nama AS nama_karyawan FROM absensi a " +
                     "JOIN karyawan k ON a.id_karyawan = k.id_karyawan " +
                     "WHERE a.status_telat = TRUE AND MONTH(a.tanggal) = ? AND YEAR(a.tanggal) = ? " +
                     "ORDER BY a.durasi_telat DESC";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, bulan);
            ps.setInt(2, tahun);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    /**
     * Laporan lembur: ambil absensi yang ada lemburnya pada periode tertentu.
     */
    public List<Absensi> findOvertimeByPeriode(int bulan, int tahun) throws SQLException {
        List<Absensi> list = new ArrayList<>();
        String sql = "SELECT a.*, k.nama AS nama_karyawan FROM absensi a " +
                     "JOIN karyawan k ON a.id_karyawan = k.id_karyawan " +
                     "WHERE a.durasi_lembur > 0 AND MONTH(a.tanggal) = ? AND YEAR(a.tanggal) = ? " +
                     "ORDER BY a.durasi_lembur DESC";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, bulan);
            ps.setInt(2, tahun);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    /**
     * Hitung total lembur seorang karyawan dalam periode tertentu (untuk kalkulasi gaji).
     */
    public java.math.BigDecimal hitungTotalLembur(String idKaryawan,
                                                   int bulan, int tahun) throws SQLException {
        String sql = "SELECT COALESCE(SUM(uang_lembur), 0) AS total " +
                     "FROM absensi WHERE id_karyawan = ? " +
                     "AND MONTH(tanggal) = ? AND YEAR(tanggal) = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, idKaryawan);
            ps.setInt(2, bulan);
            ps.setInt(3, tahun);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBigDecimal("total");
            }
        }
        return java.math.BigDecimal.ZERO;
    }

    /**
     * Hitung statistik absensi karyawan dalam periode tertentu.
     */
    public int[] hitungStatistik(String idKaryawan, int bulan, int tahun) throws SQLException {
        // [0]=total_hadir, [1]=total_telat, [2]=total_lembur_hari
        int[] stat = {0, 0, 0};
        String sql = "SELECT " +
                     "COUNT(*) AS total_hadir, " +
                     "SUM(CASE WHEN status_telat = TRUE THEN 1 ELSE 0 END) AS total_telat, " +
                     "SUM(CASE WHEN durasi_lembur > 0 THEN 1 ELSE 0 END) AS total_lembur " +
                     "FROM absensi WHERE id_karyawan = ? " +
                     "AND MONTH(tanggal) = ? AND YEAR(tanggal) = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, idKaryawan);
            ps.setInt(2, bulan);
            ps.setInt(3, tahun);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    stat[0] = rs.getInt("total_hadir");
                    stat[1] = rs.getInt("total_telat");
                    stat[2] = rs.getInt("total_lembur");
                }
            }
        }
        return stat;
    }

    /** Helper: mapping ResultSet ke objek Absensi. */
    private Absensi mapResultSet(ResultSet rs) throws SQLException {
        Absensi a = new Absensi();
        a.setId(rs.getInt("id"));
        a.setIdKaryawan(rs.getString("id_karyawan"));
        a.setTanggal(rs.getDate("tanggal"));
        a.setJamMasuk(rs.getTime("jam_masuk"));
        a.setJamKeluar(rs.getTime("jam_keluar"));
        a.setPathFotoMasuk(rs.getString("path_foto_masuk"));
        a.setPathFotoKeluar(rs.getString("path_foto_keluar"));
        a.setLokasiMasuk(rs.getString("lokasi_masuk"));
        a.setLokasiKeluar(rs.getString("lokasi_keluar"));
        a.setStatusTelat(rs.getBoolean("status_telat"));
        a.setDurasiTelat(rs.getInt("durasi_telat"));
        a.setDurasiLembur(rs.getInt("durasi_lembur"));
        a.setUangLembur(rs.getBigDecimal("uang_lembur"));
        a.setKeterangan(rs.getString("keterangan"));
        a.setCreatedAt(rs.getTimestamp("created_at"));
        a.setUpdatedAt(rs.getTimestamp("updated_at"));

        // Kolom JOIN (bisa null jika query tanpa JOIN)
        try { a.setNamaKaryawan(rs.getString("nama_karyawan")); } catch (SQLException ignored) {}

        return a;
    }
}