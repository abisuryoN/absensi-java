package com.absensi.dao;

import com.absensi.config.DatabaseConfig;
import com.absensi.model.Karyawan;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO untuk tabel karyawan.
 * Kolom foto_profil = VARCHAR path (bukan BLOB).
 */
public class KaryawanDAO {

    public boolean registrasi(Karyawan karyawan) throws SQLException {
        String sql = "INSERT INTO karyawan " +
                     "(id_karyawan, nama, bagian, foto_profil, role, status_verifikasi, gaji_pokok) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, karyawan.getIdKaryawan());
            ps.setString(2, karyawan.getNama());
            ps.setString(3, karyawan.getBagian());
            ps.setString(4, karyawan.getFotoProfil());   // String path
            ps.setString(5, karyawan.getRole().name());
            ps.setString(6, karyawan.getStatusVerifikasi().name());
            ps.setBigDecimal(7, karyawan.getGajiPokok());
            return ps.executeUpdate() > 0;
        }
    }

    public Karyawan login(String nama, String idKaryawan) throws SQLException {
        String sql = "SELECT * FROM karyawan WHERE nama = ? AND id_karyawan = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nama);
            ps.setString(2, idKaryawan);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapResultSet(rs);
            }
        }
        return null;
    }

    public Karyawan findById(String idKaryawan) throws SQLException {
        String sql = "SELECT * FROM karyawan WHERE id_karyawan = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, idKaryawan);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapResultSet(rs);
            }
        }
        return null;
    }

    public Karyawan findByNama(String nama) throws SQLException {
        String sql = "SELECT * FROM karyawan WHERE nama = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nama);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapResultSet(rs);
            }
        }
        return null;
    }

    public List<Karyawan> findAll() throws SQLException {
        List<Karyawan> list = new ArrayList<>();
        String sql = "SELECT * FROM karyawan ORDER BY nama ASC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapResultSet(rs));
        }
        return list;
    }

    public List<Karyawan> findByStatus(Karyawan.StatusVerifikasi status) throws SQLException {
        List<Karyawan> list = new ArrayList<>();
        String sql = "SELECT * FROM karyawan WHERE status_verifikasi = ? ORDER BY created_at DESC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    public boolean updateBagian(String idKaryawan, String bagianBaru) throws SQLException {
        String sql = "UPDATE karyawan SET bagian = ?, role = ? WHERE id_karyawan = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String role = "HRD".equalsIgnoreCase(bagianBaru) ? "HRD" : "KARYAWAN";
            ps.setString(1, bagianBaru);
            ps.setString(2, role);
            ps.setString(3, idKaryawan);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updateStatusVerifikasi(String idKaryawan,
                                           Karyawan.StatusVerifikasi status) throws SQLException {
        String sql = "UPDATE karyawan SET status_verifikasi = ? WHERE id_karyawan = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, idKaryawan);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updateGajiPokok(String idKaryawan, java.math.BigDecimal gp) throws SQLException {
        String sql = "UPDATE karyawan SET gaji_pokok = ? WHERE id_karyawan = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, gp);
            ps.setString(2, idKaryawan);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updateFotoProfil(String idKaryawan, String path) throws SQLException {
        String sql = "UPDATE karyawan SET foto_profil = ? WHERE id_karyawan = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, path);
            ps.setString(2, idKaryawan);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updatePasswordHash(String idKaryawan, String hash) throws SQLException {
        String sql = "UPDATE karyawan SET password_hash = ? WHERE id_karyawan = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hash);
            ps.setString(2, idKaryawan);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updateProfil(Karyawan k) throws SQLException {
        String sql = "UPDATE karyawan SET nama = ?, bagian = ?, foto_profil = ? WHERE id_karyawan = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, k.getNama());
            ps.setString(2, k.getBagian());
            ps.setString(3, k.getFotoProfil());
            ps.setString(4, k.getIdKaryawan());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean isIdKaryawanExist(String idKaryawan) throws SQLException {
        String sql = "SELECT COUNT(*) FROM karyawan WHERE id_karyawan = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, idKaryawan);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        }
        return false;
    }

    public boolean isNamaExist(String nama) throws SQLException {
        String sql = "SELECT COUNT(*) FROM karyawan WHERE nama = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nama);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        }
        return false;
    }

    public boolean delete(String idKaryawan) throws SQLException {
        String sql = "DELETE FROM karyawan WHERE id_karyawan = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, idKaryawan);
            return ps.executeUpdate() > 0;
        }
    }

    // ── Mapping ResultSet → Karyawan ─────────────────────────
    // Membaca foto_profil sebagai String path.
    // try-catch per kolom agar tidak crash di database lama
    // yang mungkin belum punya kolom foto_profil / password_hash.

    private Karyawan mapResultSet(ResultSet rs) throws SQLException {
        Karyawan k = new Karyawan();
        k.setId(rs.getInt("id"));
        k.setIdKaryawan(rs.getString("id_karyawan"));
        k.setNama(rs.getString("nama"));
        k.setBagian(rs.getString("bagian"));
        k.setRole(Karyawan.Role.valueOf(rs.getString("role")));
        k.setStatusVerifikasi(Karyawan.StatusVerifikasi.valueOf(rs.getString("status_verifikasi")));
        k.setGajiPokok(rs.getBigDecimal("gaji_pokok"));
        k.setCreatedAt(rs.getTimestamp("created_at"));
        k.setUpdatedAt(rs.getTimestamp("updated_at"));

        try { k.setFotoProfil(rs.getString("foto_profil")); }
        catch (SQLException ignored) {}

        try { k.setPasswordHash(rs.getString("password_hash")); }
        catch (SQLException ignored) {}

        return k;
    }
}
