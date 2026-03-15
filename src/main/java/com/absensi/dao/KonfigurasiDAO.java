package com.absensi.dao;

import com.absensi.config.DatabaseConfig;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * DAO untuk membaca dan mengupdate konfigurasi sistem dari database.
 * HRD dapat mengubah konfigurasi seperti koordinat kantor, jam kerja, dll.
 */
public class KonfigurasiDAO {

    /**
     * Ambil semua konfigurasi sebagai Map key-value.
     */
    public Map<String, String> findAll() throws SQLException {
        Map<String, String> config = new HashMap<>();
        String sql = "SELECT kunci, nilai FROM konfigurasi";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                config.put(rs.getString("kunci"), rs.getString("nilai"));
            }
        }
        return config;
    }

    /**
     * Ambil satu nilai konfigurasi berdasarkan kunci.
     */
    public String findByKey(String kunci) throws SQLException {
        String sql = "SELECT nilai FROM konfigurasi WHERE kunci = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, kunci);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("nilai");
            }
        }
        return null;
    }

    /**
     * Update nilai konfigurasi.
     */
    public boolean update(String kunci, String nilai) throws SQLException {
        String sql = "UPDATE konfigurasi SET nilai = ? WHERE kunci = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, nilai);
            ps.setString(2, kunci);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Update semua konfigurasi sekaligus (untuk panel setting HRD).
     */
    public boolean updateBatch(Map<String, String> configMap) throws SQLException {
        Connection conn = DatabaseConfig.getConnection();
        try {
            conn.setAutoCommit(false);
            String sql = "UPDATE konfigurasi SET nilai = ? WHERE kunci = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Map.Entry<String, String> entry : configMap.entrySet()) {
                    ps.setString(1, entry.getValue());
                    ps.setString(2, entry.getKey());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }
}