package com.absensi.model;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * Model data Karyawan.
 *
 * PERUBAHAN:
 *  - foto (byte[])  → DIHAPUS, tidak lagi disimpan di database
 *  - fotoProfil (String) → PATH file di disk, contoh: photos/2026/03/profil_EMP001.jpg
 *  - passwordHash (String) → SHA-256+salt untuk keamanan
 */
public class Karyawan {

    public enum Role { KARYAWAN, HRD }
    public enum StatusVerifikasi { PENDING, TERVERIFIKASI, DITOLAK }

    private int    id;
    private String idKaryawan;
    private String nama;
    private String bagian;
    private String fotoProfil;    // PATH ke file gambar di disk (VARCHAR di DB)
    private String passwordHash;  // SHA-256+salt, format "SALT:HASH"
    private Role   role;
    private StatusVerifikasi statusVerifikasi;
    private BigDecimal gajiPokok;
    private Timestamp  createdAt;
    private Timestamp  updatedAt;

    public Karyawan() {}

    public Karyawan(String idKaryawan, String nama, String bagian) {
        this.idKaryawan       = idKaryawan;
        this.nama             = nama;
        this.bagian           = bagian;
        this.role             = Role.KARYAWAN;
        this.statusVerifikasi = StatusVerifikasi.PENDING;
        this.gajiPokok        = BigDecimal.ZERO;
    }

    // ── Getters & Setters ─────────────────────────────────────

    public int    getId()            { return id; }
    public void   setId(int id)      { this.id = id; }

    public String getIdKaryawan()                    { return idKaryawan; }
    public void   setIdKaryawan(String idKaryawan)   { this.idKaryawan = idKaryawan; }

    public String getNama()                { return nama; }
    public void   setNama(String nama)     { this.nama = nama; }

    public String getBagian()              { return bagian; }
    public void   setBagian(String bagian) { this.bagian = bagian; }

    /**
     * PATH foto profil yang disimpan di disk.
     * Contoh: "photos/2026/03/profil_EMP001.jpg"
     * Return null jika karyawan belum punya foto.
     */
    public String getFotoProfil()                      { return fotoProfil; }
    public void   setFotoProfil(String fotoProfil)     { this.fotoProfil = fotoProfil; }

    /**
     * Alias getFotoProfil() — untuk kompatibilitas dengan kode
     * yang sebelumnya memanggil getFoto().
     * Bukan lagi byte[], tapi String path.
     */
    public String getFoto()              { return fotoProfil; }
    public void   setFoto(String path)   { this.fotoProfil = path; }

    public String getPasswordHash()                    { return passwordHash; }
    public void   setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public Role getRole()              { return role; }
    public void setRole(Role role)     { this.role = role; }

    public StatusVerifikasi getStatusVerifikasi()                        { return statusVerifikasi; }
    public void             setStatusVerifikasi(StatusVerifikasi sv)     { this.statusVerifikasi = sv; }

    public BigDecimal getGajiPokok()                   { return gajiPokok; }
    public void       setGajiPokok(BigDecimal gp)      { this.gajiPokok = gp; }

    public Timestamp  getCreatedAt()                   { return createdAt; }
    public void       setCreatedAt(Timestamp t)        { this.createdAt = t; }

    public Timestamp  getUpdatedAt()                   { return updatedAt; }
    public void       setUpdatedAt(Timestamp t)        { this.updatedAt = t; }

    // ── Helper methods ────────────────────────────────────────

    public boolean isHRD()          { return Role.HRD.equals(this.role); }
    public boolean isTerverifikasi(){ return StatusVerifikasi.TERVERIFIKASI.equals(this.statusVerifikasi); }
    public boolean hasFoto()        { return fotoProfil != null && !fotoProfil.isBlank(); }

    @Override
    public String toString() {
        return String.format("Karyawan{id='%s', nama='%s', bagian='%s', role=%s}",
            idKaryawan, nama, bagian, role);
    }
}
