package com.absensi.model;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;

/**
 * Model untuk data Gaji karyawan per bulan.
 */
public class Gaji {

    public enum StatusBayar { PENDING, DIBAYAR }

    private int id;
    private String idKaryawan;
    private String namaKaryawan;    // JOIN dari tabel karyawan
    private String bagian;          // JOIN dari tabel karyawan
    private int bulan;
    private int tahun;
    private BigDecimal gajiPokok;
    private BigDecimal totalLembur;
    private BigDecimal bonus;
    private BigDecimal potongan;
    private BigDecimal totalGaji;
    private String keteranganBonus;
    private StatusBayar statusBayar;
    private Date tanggalBayar;
    private String dibayarOleh;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Gaji() {
        this.gajiPokok   = BigDecimal.ZERO;
        this.totalLembur = BigDecimal.ZERO;
        this.bonus       = BigDecimal.ZERO;
        this.potongan    = BigDecimal.ZERO;
        this.totalGaji   = BigDecimal.ZERO;
        this.statusBayar = StatusBayar.PENDING;
    }

    // ===================== GETTERS & SETTERS =====================

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getIdKaryawan() { return idKaryawan; }
    public void setIdKaryawan(String idKaryawan) { this.idKaryawan = idKaryawan; }

    public String getNamaKaryawan() { return namaKaryawan; }
    public void setNamaKaryawan(String namaKaryawan) { this.namaKaryawan = namaKaryawan; }

    public String getBagian() { return bagian; }
    public void setBagian(String bagian) { this.bagian = bagian; }

    public int getBulan() { return bulan; }
    public void setBulan(int bulan) { this.bulan = bulan; }

    public int getTahun() { return tahun; }
    public void setTahun(int tahun) { this.tahun = tahun; }

    public BigDecimal getGajiPokok() { return gajiPokok; }
    public void setGajiPokok(BigDecimal gajiPokok) { this.gajiPokok = gajiPokok; }

    public BigDecimal getTotalLembur() { return totalLembur; }
    public void setTotalLembur(BigDecimal totalLembur) { this.totalLembur = totalLembur; }

    public BigDecimal getBonus() { return bonus; }
    public void setBonus(BigDecimal bonus) { this.bonus = bonus; }

    public BigDecimal getPotongan() { return potongan; }
    public void setPotongan(BigDecimal potongan) { this.potongan = potongan; }

    public BigDecimal getTotalGaji() { return totalGaji; }
    public void setTotalGaji(BigDecimal totalGaji) { this.totalGaji = totalGaji; }

    public String getKeteranganBonus() { return keteranganBonus; }
    public void setKeteranganBonus(String keteranganBonus) { this.keteranganBonus = keteranganBonus; }

    public StatusBayar getStatusBayar() { return statusBayar; }
    public void setStatusBayar(StatusBayar statusBayar) { this.statusBayar = statusBayar; }

    public Date getTanggalBayar() { return tanggalBayar; }
    public void setTanggalBayar(Date tanggalBayar) { this.tanggalBayar = tanggalBayar; }

    public String getDibayarOleh() { return dibayarOleh; }
    public void setDibayarOleh(String dibayarOleh) { this.dibayarOleh = dibayarOleh; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    /** Hitung total gaji secara otomatis */
    public void hitungTotalGaji() {
        this.totalGaji = gajiPokok
            .add(totalLembur != null ? totalLembur : BigDecimal.ZERO)
            .add(bonus != null ? bonus : BigDecimal.ZERO)
            .subtract(potongan != null ? potongan : BigDecimal.ZERO);
    }

    /** Mendapatkan nama bulan dalam Bahasa Indonesia */
    public String getNamaBulan() {
        String[] namaBulan = {
            "", "Januari", "Februari", "Maret", "April", "Mei", "Juni",
            "Juli", "Agustus", "September", "Oktober", "November", "Desember"
        };
        if (bulan >= 1 && bulan <= 12) return namaBulan[bulan];
        return "Unknown";
    }

    /** Mendapatkan label periode gaji */
    public String getPeriodeLabel() {
        return getNamaBulan() + " " + tahun;
    }
}