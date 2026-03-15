package com.absensi.dao;

import com.absensi.config.DatabaseConfig;
import com.absensi.model.Divisi;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO untuk operasi CRUD tabel divisi.
 * HRD dapat menambah, mengubah, menonaktifkan divisi melalui DAO ini.
 */
public class DivisiDAO {

    /**
     * Tambah divisi baru.
     */
    public boolean insert(Divisi divisi) throws SQLException {
        String sql = "INSERT INTO divisi (kode_divisi, nama_divisi, deskripsi, kepala, aktif) " +
                     "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, divisi.getKodeDivisi());
            ps.setString(2, divisi.getNamaDivisi());
            ps.setString(3, divisi.getDeskripsi());
            ps.setString(4, divisi.getKepala());
            ps.setBoolean(5, divisi.isAktif());

            int rows = ps.executeUpdate();
            if (rows > 0) {
                try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                    if (generatedKeys.next()) divisi.setId(generatedKeys.getInt(1));
                }
                return true;
            }
            return false;
        }
    }

    /**
     * Update data divisi.
     */
    public boolean update(Divisi divisi) throws SQLException {
        String sql = "UPDATE divisi SET kode_divisi = ?, nama_divisi = ?, " +
                     "deskripsi = ?, kepala = ?, aktif = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, divisi.getKodeDivisi());
            ps.setString(2, divisi.getNamaDivisi());
            ps.setString(3, divisi.getDeskripsi());
            ps.setString(4, divisi.getKepala());
            ps.setBoolean(5, divisi.isAktif());
            ps.setInt(6, divisi.getId());

            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Nonaktifkan divisi (soft delete - tidak benar-benar hapus dari DB).
     * Aman dilakukan meski sudah ada karyawan yang terhubung ke divisi ini.
     */
    public boolean nonaktifkan(int id) throws SQLException {
        String sql = "UPDATE divisi SET aktif = FALSE WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Hapus permanen divisi (hanya jika tidak ada karyawan yang menggunakannya).
     */
    public boolean delete(int id) throws SQLException {
        // Cek dulu apakah ada karyawan yang masih di divisi ini
        if (countKaryawanByDivisiId(id) > 0) {
            throw new SQLException(
                "Tidak dapat menghapus divisi: masih ada karyawan yang terdaftar di divisi ini. " +
                "Gunakan nonaktifkan() sebagai gantinya."
            );
        }
        String sql = "DELETE FROM divisi WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Ambil semua divisi (aktif dan nonaktif) beserta jumlah karyawan.
     */
    public List<Divisi> findAll() throws SQLException {
        List<Divisi> list = new ArrayList<>();
        String sql = "SELECT d.*, " +
                     "COUNT(k.id) AS jumlah_karyawan " +
                     "FROM divisi d " +
                     "LEFT JOIN karyawan k ON k.bagian = d.kode_divisi " +
                     "GROUP BY d.id " +
                     "ORDER BY d.aktif DESC, d.nama_divisi ASC";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) list.add(mapResultSet(rs));
        }
        return list;
    }

    /**
     * Ambil hanya divisi yang aktif (untuk dropdown registrasi dan form karyawan).
     */
    public List<Divisi> findAllAktif() throws SQLException {
        List<Divisi> list = new ArrayList<>();
        String sql = "SELECT d.*, COUNT(k.id) AS jumlah_karyawan " +
                     "FROM divisi d " +
                     "LEFT JOIN karyawan k ON k.bagian = d.kode_divisi " +
                     "WHERE d.aktif = TRUE " +
                     "GROUP BY d.id " +
                     "ORDER BY d.nama_divisi ASC";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) list.add(mapResultSet(rs));
        }
        return list;
    }

    /**
     * Ambil hanya nama/kode divisi aktif sebagai array String.
     * Digunakan untuk mengisi JComboBox.
     */
    public String[] findNamaDivisiAktif() throws SQLException {
        List<Divisi> list = findAllAktif();
        return list.stream()
                   .map(Divisi::getNamaDivisi)
                   .toArray(String[]::new);
    }

    /**
     * Cari divisi berdasarkan ID.
     */
    public Divisi findById(int id) throws SQLException {
        String sql = "SELECT d.*, COUNT(k.id) AS jumlah_karyawan " +
                     "FROM divisi d " +
                     "LEFT JOIN karyawan k ON k.bagian = d.kode_divisi " +
                     "WHERE d.id = ? GROUP BY d.id";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapResultSet(rs);
            }
        }
        return null;
    }

    /**
     * Cari divisi berdasarkan kode divisi.
     */
    public Divisi findByKode(String kodeDivisi) throws SQLException {
        String sql = "SELECT d.*, COUNT(k.id) AS jumlah_karyawan " +
                     "FROM divisi d " +
                     "LEFT JOIN karyawan k ON k.bagian = d.kode_divisi " +
                     "WHERE d.kode_divisi = ? GROUP BY d.id";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, kodeDivisi);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapResultSet(rs);
            }
        }
        return null;
    }

    /**
     * Cek apakah kode divisi sudah digunakan.
     */
    public boolean isKodeExist(String kodeDivisi) throws SQLException {
        String sql = "SELECT COUNT(*) FROM divisi WHERE kode_divisi = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, kodeDivisi.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Cek apakah kode divisi sudah digunakan, kecuali record dengan ID tertentu.
     * Digunakan saat update untuk menghindari conflict dengan dirinya sendiri.
     */
    public boolean isKodeExistExclude(String kodeDivisi, int excludeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM divisi WHERE kode_divisi = ? AND id != ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, kodeDivisi.toUpperCase());
            ps.setInt(2, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Hitung jumlah karyawan di suatu divisi berdasarkan ID divisi.
     */
    public int countKaryawanByDivisiId(int divisiId) throws SQLException {
        // Ambil kode divisi dulu
        Divisi divisi = findById(divisiId);
        if (divisi == null) return 0;
        return countKaryawanByKode(divisi.getKodeDivisi());
    }

    /**
     * Hitung jumlah karyawan berdasarkan kode divisi.
     */
    public int countKaryawanByKode(String kodeDivisi) throws SQLException {
        String sql = "SELECT COUNT(*) FROM karyawan WHERE bagian = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, kodeDivisi);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Set kepala divisi (ID Karyawan yang menjabat sebagai kepala).
     */
    public boolean setKepala(int divisiId, String idKaryawan) throws SQLException {
        String sql = "UPDATE divisi SET kepala = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, idKaryawan);
            ps.setInt(2, divisiId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Helper: mapping ResultSet ke objek Divisi.
     */
    private Divisi mapResultSet(ResultSet rs) throws SQLException {
        Divisi d = new Divisi();
        d.setId(rs.getInt("id"));
        d.setKodeDivisi(rs.getString("kode_divisi"));
        d.setNamaDivisi(rs.getString("nama_divisi"));
        d.setDeskripsi(rs.getString("deskripsi"));
        d.setKepala(rs.getString("kepala"));
        d.setAktif(rs.getBoolean("aktif"));
        d.setCreatedAt(rs.getTimestamp("created_at"));
        d.setUpdatedAt(rs.getTimestamp("updated_at"));

        // Kolom COUNT dari JOIN (bisa null jika query tanpa GROUP BY)
        try { d.setJumlahKaryawan(rs.getInt("jumlah_karyawan")); }
        catch (SQLException ignored) {}

        return d;
    }
}