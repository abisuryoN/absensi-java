package com.absensi.dao;

import com.absensi.config.DatabaseConfig;
import com.absensi.model.Gaji;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO untuk operasi database tabel gaji.
 */
public class GajiDAO {

    /**
     * Simpan atau update data gaji karyawan.
     */
    public boolean saveOrUpdate(Gaji gaji) throws SQLException {
        // Cek apakah record sudah ada
        Gaji existing = findByKaryawanAndPeriode(gaji.getIdKaryawan(), gaji.getBulan(), gaji.getTahun());

        if (existing == null) {
            return insert(gaji);
        } else {
            gaji.setId(existing.getId());
            return update(gaji);
        }
    }

    private boolean insert(Gaji gaji) throws SQLException {
        String sql = "INSERT INTO gaji (id_karyawan, bulan, tahun, gaji_pokok, total_lembur, " +
                     "bonus, potongan, total_gaji, keterangan_bonus, status_bayar) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            setGajiParams(ps, gaji);
            return ps.executeUpdate() > 0;
        }
    }

    private boolean update(Gaji gaji) throws SQLException {
        String sql = "UPDATE gaji SET gaji_pokok = ?, total_lembur = ?, bonus = ?, " +
                     "potongan = ?, total_gaji = ?, keterangan_bonus = ?, status_bayar = ?, " +
                     "tanggal_bayar = ?, dibayar_oleh = ? WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setBigDecimal(1, gaji.getGajiPokok());
            ps.setBigDecimal(2, gaji.getTotalLembur());
            ps.setBigDecimal(3, gaji.getBonus());
            ps.setBigDecimal(4, gaji.getPotongan());
            ps.setBigDecimal(5, gaji.getTotalGaji());
            ps.setString(6, gaji.getKeteranganBonus());
            ps.setString(7, gaji.getStatusBayar().name());
            ps.setDate(8, gaji.getTanggalBayar());
            ps.setString(9, gaji.getDibayarOleh());
            ps.setInt(10, gaji.getId());
            return ps.executeUpdate() > 0;
        }
    }

    private void setGajiParams(PreparedStatement ps, Gaji gaji) throws SQLException {
        ps.setString(1, gaji.getIdKaryawan());
        ps.setInt(2, gaji.getBulan());
        ps.setInt(3, gaji.getTahun());
        ps.setBigDecimal(4, gaji.getGajiPokok());
        ps.setBigDecimal(5, gaji.getTotalLembur());
        ps.setBigDecimal(6, gaji.getBonus());
        ps.setBigDecimal(7, gaji.getPotongan());
        ps.setBigDecimal(8, gaji.getTotalGaji());
        ps.setString(9, gaji.getKeteranganBonus());
        ps.setString(10, gaji.getStatusBayar().name());
    }

    /**
     * Cari gaji karyawan berdasarkan periode.
     */
    public Gaji findByKaryawanAndPeriode(String idKaryawan, int bulan, int tahun) throws SQLException {
        String sql = "SELECT g.*, k.nama AS nama_karyawan, k.bagian FROM gaji g " +
                     "JOIN karyawan k ON g.id_karyawan = k.id_karyawan " +
                     "WHERE g.id_karyawan = ? AND g.bulan = ? AND g.tahun = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, idKaryawan);
            ps.setInt(2, bulan);
            ps.setInt(3, tahun);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapResultSet(rs);
            }
        }
        return null;
    }

    /**
     * Ambil riwayat gaji seorang karyawan.
     */
    public List<Gaji> findByKaryawan(String idKaryawan) throws SQLException {
        List<Gaji> list = new ArrayList<>();
        String sql = "SELECT g.*, k.nama AS nama_karyawan, k.bagian FROM gaji g " +
                     "JOIN karyawan k ON g.id_karyawan = k.id_karyawan " +
                     "WHERE g.id_karyawan = ? ORDER BY g.tahun DESC, g.bulan DESC";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, idKaryawan);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    /**
     * Ambil semua data gaji untuk periode tertentu (laporan HRD).
     */
    public List<Gaji> findAllByPeriode(int bulan, int tahun) throws SQLException {
        List<Gaji> list = new ArrayList<>();
        String sql = "SELECT g.*, k.nama AS nama_karyawan, k.bagian FROM gaji g " +
                     "JOIN karyawan k ON g.id_karyawan = k.id_karyawan " +
                     "WHERE g.bulan = ? AND g.tahun = ? ORDER BY k.nama ASC";

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
     * Generate/rekap gaji otomatis untuk semua karyawan dalam periode tertentu.
     * Mengambil data lembur dari tabel absensi.
     */
    public void generateGajiBulanan(int bulan, int tahun, String idHRD) throws SQLException {
        // Ambil semua karyawan aktif
        String sqlKaryawan = "SELECT id_karyawan, gaji_pokok FROM karyawan WHERE status_verifikasi = 'TERVERIFIKASI'";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement psK = conn.prepareStatement(sqlKaryawan);
             ResultSet rsK = psK.executeQuery()) {

            AbsensiDAO absensiDAO = new AbsensiDAO();

            while (rsK.next()) {
                String idKaryawan = rsK.getString("id_karyawan");
                BigDecimal gajiPokok = rsK.getBigDecimal("gaji_pokok");

                // Hitung total lembur dari absensi
                BigDecimal totalLembur = absensiDAO.hitungTotalLembur(idKaryawan, bulan, tahun);

                // Cek apakah sudah ada record gaji
                Gaji existingGaji = findByKaryawanAndPeriode(idKaryawan, bulan, tahun);

                Gaji gaji = (existingGaji != null) ? existingGaji : new Gaji();
                gaji.setIdKaryawan(idKaryawan);
                gaji.setBulan(bulan);
                gaji.setTahun(tahun);
                gaji.setGajiPokok(gajiPokok);
                gaji.setTotalLembur(totalLembur);

                // Pertahankan bonus jika sudah diset sebelumnya
                if (gaji.getBonus() == null) gaji.setBonus(BigDecimal.ZERO);
                if (gaji.getPotongan() == null) gaji.setPotongan(BigDecimal.ZERO);

                gaji.hitungTotalGaji();
                saveOrUpdate(gaji);
            }
        }
    }

    /**
     * Update bonus karyawan oleh HRD.
     */
    public boolean updateBonus(String idKaryawan, int bulan, int tahun,
                                BigDecimal bonus, String keterangan) throws SQLException {
        // Pastikan record gaji sudah ada
        Gaji gaji = findByKaryawanAndPeriode(idKaryawan, bulan, tahun);
        if (gaji == null) return false;

        gaji.setBonus(bonus);
        gaji.setKeteranganBonus(keterangan);
        gaji.hitungTotalGaji();
        return saveOrUpdate(gaji);
    }

    /**
     * Tandai gaji sebagai sudah dibayar.
     */
    public boolean tandaiBayar(int gajiId, String idHRD) throws SQLException {
        String sql = "UPDATE gaji SET status_bayar = 'DIBAYAR', tanggal_bayar = CURDATE(), " +
                     "dibayar_oleh = ? WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, idHRD);
            ps.setInt(2, gajiId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Helper: mapping ResultSet ke objek Gaji. */
    private Gaji mapResultSet(ResultSet rs) throws SQLException {
        Gaji g = new Gaji();
        g.setId(rs.getInt("id"));
        g.setIdKaryawan(rs.getString("id_karyawan"));
        g.setBulan(rs.getInt("bulan"));
        g.setTahun(rs.getInt("tahun"));
        g.setGajiPokok(rs.getBigDecimal("gaji_pokok"));
        g.setTotalLembur(rs.getBigDecimal("total_lembur"));
        g.setBonus(rs.getBigDecimal("bonus"));
        g.setPotongan(rs.getBigDecimal("potongan"));
        g.setTotalGaji(rs.getBigDecimal("total_gaji"));
        g.setKeteranganBonus(rs.getString("keterangan_bonus"));
        g.setStatusBayar(Gaji.StatusBayar.valueOf(rs.getString("status_bayar")));
        g.setTanggalBayar(rs.getDate("tanggal_bayar"));
        g.setDibayarOleh(rs.getString("dibayar_oleh"));
        g.setCreatedAt(rs.getTimestamp("created_at"));
        g.setUpdatedAt(rs.getTimestamp("updated_at"));

        try { g.setNamaKaryawan(rs.getString("nama_karyawan")); } catch (SQLException ignored) {}
        try { g.setBagian(rs.getString("bagian")); } catch (SQLException ignored) {}

        return g;
    }
}