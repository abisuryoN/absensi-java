package com.absensi.model;

import java.sql.Timestamp;

/**
 * Model untuk data Divisi/Bagian kerja karyawan.
 *
 * Divisi dikelola secara dinamis oleh HRD melalui database,
 * sehingga tidak perlu hardcode daftar bagian di kode program.
 *
 * Relasi: satu Divisi memiliki banyak Karyawan (One-to-Many).
 */
public class Divisi {

    private int id;
    private String kodeDivisi;      // Contoh: "IT", "HRD", "FIN"
    private String namaDivisi;      // Contoh: "Information Technology", "Human Resources"
    private String deskripsi;       // Deskripsi singkat tugas divisi
    private String kepala;          // ID Karyawan yang menjadi kepala divisi (nullable)
    private boolean aktif;          // Apakah divisi masih aktif
    private int jumlahKaryawan;     // Field transient untuk COUNT JOIN (tidak disimpan di DB)
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Divisi() {
        this.aktif = true;
    }

    public Divisi(String kodeDivisi, String namaDivisi) {
        this.kodeDivisi  = kodeDivisi;
        this.namaDivisi  = namaDivisi;
        this.aktif       = true;
    }

    public Divisi(String kodeDivisi, String namaDivisi, String deskripsi) {
        this.kodeDivisi  = kodeDivisi;
        this.namaDivisi  = namaDivisi;
        this.deskripsi   = deskripsi;
        this.aktif       = true;
    }

    // ===================== GETTERS & SETTERS =====================

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getKodeDivisi() { return kodeDivisi; }
    public void setKodeDivisi(String kodeDivisi) {
        this.kodeDivisi = kodeDivisi != null ? kodeDivisi.toUpperCase().trim() : null;
    }

    public String getNamaDivisi() { return namaDivisi; }
    public void setNamaDivisi(String namaDivisi) { this.namaDivisi = namaDivisi; }

    public String getDeskripsi() { return deskripsi; }
    public void setDeskripsi(String deskripsi) { this.deskripsi = deskripsi; }

    public String getKepala() { return kepala; }
    public void setKepala(String kepala) { this.kepala = kepala; }

    public boolean isAktif() { return aktif; }
    public void setAktif(boolean aktif) { this.aktif = aktif; }

    public int getJumlahKaryawan() { return jumlahKaryawan; }
    public void setJumlahKaryawan(int jumlahKaryawan) { this.jumlahKaryawan = jumlahKaryawan; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    // ===================== UTILITY METHODS =====================

    /**
     * Cek apakah divisi ini adalah divisi HRD.
     * Divisi HRD memiliki hak akses khusus di sistem.
     */
    public boolean isHRD() {
        return "HRD".equalsIgnoreCase(this.kodeDivisi) ||
               "HRD".equalsIgnoreCase(this.namaDivisi);
    }

    /**
     * Mendapatkan label tampilan divisi (Kode - Nama).
     * Contoh: "IT - Information Technology"
     */
    public String getLabelLengkap() {
        if (namaDivisi != null && !namaDivisi.isEmpty()) {
            return kodeDivisi + " - " + namaDivisi;
        }
        return kodeDivisi;
    }

    /**
     * Mendapatkan status aktif sebagai teks.
     */
    public String getStatusLabel() {
        return aktif ? "Aktif" : "Nonaktif";
    }

    @Override
    public String toString() {
        // Digunakan JComboBox untuk menampilkan nama divisi
        return namaDivisi != null ? namaDivisi : kodeDivisi;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Divisi)) return false;
        Divisi other = (Divisi) obj;
        return this.id == other.id ||
               (this.kodeDivisi != null && this.kodeDivisi.equalsIgnoreCase(other.kodeDivisi));
    }

    @Override
    public int hashCode() {
        return kodeDivisi != null ? kodeDivisi.toUpperCase().hashCode() : id;
    }
}