package com.absensi.model;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;

/**
 * Model untuk data Absensi harian karyawan.
 */
public class Absensi {

    private int id;
    private String idKaryawan;
    private String namaKaryawan;    // JOIN dari tabel karyawan
    private Date tanggal;
    private Time jamMasuk;
    private Time jamKeluar;
    private byte[] fotoMasuk;
    private byte[] fotoKeluar;
    
    private String pathFotoMasuk;
    private String pathFotoKeluar;
    private String lokasiMasuk;
    private String lokasiKeluar;
    private boolean statusTelat;
    private int durasiTelat;        // dalam menit
    private int durasiLembur;       // dalam menit
    private BigDecimal uangLembur;
    private String keterangan;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Absensi() {}

    // ===================== GETTERS & SETTERS =====================

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getIdKaryawan() { return idKaryawan; }
    public void setIdKaryawan(String idKaryawan) { this.idKaryawan = idKaryawan; }

    public String getNamaKaryawan() { return namaKaryawan; }
    public void setNamaKaryawan(String namaKaryawan) { this.namaKaryawan = namaKaryawan; }

    public Date getTanggal() { return tanggal; }
    public void setTanggal(Date tanggal) { this.tanggal = tanggal; }

    public Time getJamMasuk() { return jamMasuk; }
    public void setJamMasuk(Time jamMasuk) { this.jamMasuk = jamMasuk; }

    public Time getJamKeluar() { return jamKeluar; }
    public void setJamKeluar(Time jamKeluar) { this.jamKeluar = jamKeluar; }

    public byte[] getFotoMasuk() { return fotoMasuk; }
    public void setFotoMasuk(byte[] fotoMasuk) { this.fotoMasuk = fotoMasuk; }

    public String getPathFotoMasuk() {
    return pathFotoMasuk;
    }

    public void setPathFotoMasuk(String pathFotoMasuk) {
    this.pathFotoMasuk = pathFotoMasuk;
    }

    public String getPathFotoKeluar() {
    return pathFotoKeluar;
    }

    public void setPathFotoKeluar(String pathFotoKeluar) {
    this.pathFotoKeluar = pathFotoKeluar;
    }

    public String getLokasiMasuk() { return lokasiMasuk; }
    public void setLokasiMasuk(String lokasiMasuk) { this.lokasiMasuk = lokasiMasuk; }

    public String getLokasiKeluar() { return lokasiKeluar; }
    public void setLokasiKeluar(String lokasiKeluar) { this.lokasiKeluar = lokasiKeluar; }

    public boolean isStatusTelat() { return statusTelat; }
    public void setStatusTelat(boolean statusTelat) { this.statusTelat = statusTelat; }

    public int getDurasiTelat() { return durasiTelat; }
    public void setDurasiTelat(int durasiTelat) { this.durasiTelat = durasiTelat; }

    public int getDurasiLembur() { return durasiLembur; }
    public void setDurasiLembur(int durasiLembur) { this.durasiLembur = durasiLembur; }

    public BigDecimal getUangLembur() { return uangLembur; }
    public void setUangLembur(BigDecimal uangLembur) { this.uangLembur = uangLembur; }

    public String getKeterangan() { return keterangan; }
    public void setKeterangan(String keterangan) { this.keterangan = keterangan; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    /** Format durasi telat ke string yang mudah dibaca */
    public String getDurasiTelatFormatted() {
        if (durasiTelat <= 0) return "-";
        int jam = durasiTelat / 60;
        int menit = durasiTelat % 60;
        if (jam > 0) return jam + " jam " + menit + " menit";
        return menit + " menit";
    }

    /** Format durasi lembur ke string yang mudah dibaca */
    public String getDurasiLemburFormatted() {
        if (durasiLembur <= 0) return "-";
        int jam = durasiLembur / 60;
        int menit = durasiLembur % 60;
        if (jam > 0) return jam + " jam " + menit + " menit";
        return menit + " menit";
    }

    /** Cek apakah absensi masuk sudah dilakukan */
    public boolean sudahAbsenMasuk() {
        return jamMasuk != null;
    }

    /** Cek apakah absensi keluar sudah dilakukan */
    public boolean sudahAbsenKeluar() {
        return jamKeluar != null;
    }
}