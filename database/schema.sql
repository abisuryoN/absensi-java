-- =============================================
-- SISTEM ABSENSI KARYAWAN
-- Database Schema MySQL
-- =============================================

CREATE DATABASE IF NOT EXISTS absensi_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE absensi_db;

-- =============================================
-- TABEL DIVISI / BAGIAN KERJA
-- =============================================
CREATE TABLE IF NOT EXISTS divisi (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    kode_divisi     VARCHAR(20) NOT NULL UNIQUE,
    nama_divisi     VARCHAR(100) NOT NULL,
    deskripsi       TEXT,
    kepala          VARCHAR(20),
    aktif           BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- =============================================
-- DATA AWAL DIVISI
-- =============================================
INSERT INTO divisi (kode_divisi, nama_divisi, deskripsi) VALUES
('HRD','Human Resources','Mengelola SDM dan absensi'),
('IT','Information Technology','Mengelola sistem IT'),
('KEUANGAN','Keuangan','Mengelola keuangan perusahaan'),
('MARKETING','Marketing','Mengelola pemasaran'),
('OPERASIONAL','Operasional','Operasional perusahaan'),
('PRODUKSI','Produksi','Produksi dan QC'),
('LEGAL','Legal & Compliance','Aspek hukum'),
('LOGISTIK','Logistik','Pengiriman dan gudang');

-- =============================================
-- TABEL KARYAWAN
-- =============================================
CREATE TABLE IF NOT EXISTS karyawan (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    id_karyawan     VARCHAR(20) NOT NULL UNIQUE,
    nama            VARCHAR(100) NOT NULL,
    bagian          VARCHAR(50) NOT NULL,

    -- FOTO PROFIL (PATH FILE)
    foto_profil     VARCHAR(255) NULL COMMENT 'photos/profil_EMP001.jpg',

    -- PASSWORD HASH
    password_hash   VARCHAR(255) NULL COMMENT 'SALT:HASH SHA256',

    role            ENUM('KARYAWAN','HRD') DEFAULT 'KARYAWAN',
    status_verifikasi ENUM('PENDING','TERVERIFIKASI','DITOLAK') DEFAULT 'PENDING',

    gaji_pokok      DECIMAL(15,2) DEFAULT 0.00,

    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- =============================================
-- TABEL ABSENSI
-- =============================================
CREATE TABLE IF NOT EXISTS absensi (
    id                INT AUTO_INCREMENT PRIMARY KEY,
    id_karyawan       VARCHAR(20) NOT NULL,
    tanggal           DATE NOT NULL,

    jam_masuk         TIME,
    jam_keluar        TIME,

    -- FOTO ABSENSI (PATH FILE)
    path_foto_masuk   VARCHAR(255) NULL COMMENT 'photos/EMP001_masuk_20260315.jpg',
    path_foto_keluar  VARCHAR(255) NULL COMMENT 'photos/EMP001_keluar_20260315.jpg',

    lokasi_masuk      VARCHAR(100),
    lokasi_keluar     VARCHAR(100),

    status_telat      BOOLEAN DEFAULT FALSE,
    durasi_telat      INT DEFAULT 0,
    durasi_lembur     INT DEFAULT 0,
    uang_lembur       DECIMAL(15,2) DEFAULT 0.00,

    keterangan        VARCHAR(255),

    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (id_karyawan) REFERENCES karyawan(id_karyawan) ON UPDATE CASCADE,
    UNIQUE KEY unique_absensi (id_karyawan, tanggal)
);

-- =============================================
-- TABEL GAJI
-- =============================================
CREATE TABLE IF NOT EXISTS gaji (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    id_karyawan     VARCHAR(20) NOT NULL,

    bulan           INT NOT NULL,
    tahun           INT NOT NULL,

    gaji_pokok      DECIMAL(15,2) DEFAULT 0.00,
    total_lembur    DECIMAL(15,2) DEFAULT 0.00,
    bonus           DECIMAL(15,2) DEFAULT 0.00,
    potongan        DECIMAL(15,2) DEFAULT 0.00,

    total_gaji      DECIMAL(15,2) DEFAULT 0.00,

    keterangan_bonus VARCHAR(255),

    status_bayar    ENUM('PENDING','DIBAYAR') DEFAULT 'PENDING',
    tanggal_bayar   DATE,
    dibayar_oleh    VARCHAR(20),

    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (id_karyawan) REFERENCES karyawan(id_karyawan) ON UPDATE CASCADE,
    UNIQUE KEY unique_gaji (id_karyawan, bulan, tahun)
);

-- =============================================
-- TABEL KONFIGURASI
-- =============================================
CREATE TABLE IF NOT EXISTS konfigurasi (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    kunci           VARCHAR(100) NOT NULL UNIQUE,
    nilai           VARCHAR(500) NOT NULL,
    keterangan      VARCHAR(255),
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- =============================================
-- DATA AWAL KONFIGURASI
-- =============================================
INSERT INTO konfigurasi (kunci, nilai, keterangan) VALUES
('KANTOR_LATITUDE','-6.200000','Latitude kantor'),
('KANTOR_LONGITUDE','106.816666','Longitude kantor'),
('RADIUS_KANTOR','100','Radius kantor meter'),
('JAM_MASUK','08:00','Jam mulai kerja'),
('JAM_PULANG','17:00','Jam pulang'),
('TARIF_LEMBUR','70000','Tarif lembur per jam');

-- =============================================
-- AKUN HRD DEFAULT
-- =============================================
INSERT INTO karyawan (id_karyawan,nama,bagian,role,status_verifikasi,gaji_pokok)
VALUES ('HRD001','Admin HRD','HRD','HRD','TERVERIFIKASI',8000000);

-- =============================================
-- INDEX PERFORMA
-- =============================================
CREATE INDEX idx_absensi_tanggal    ON absensi (tanggal);
CREATE INDEX idx_absensi_karyawan   ON absensi (id_karyawan);
CREATE INDEX idx_gaji_periode       ON gaji (bulan, tahun);
CREATE INDEX idx_karyawan_bagian    ON karyawan (bagian);
CREATE INDEX idx_karyawan_foto      ON karyawan (foto_profil);
CREATE INDEX idx_divisi_kode        ON divisi (kode_divisi);
CREATE INDEX idx_divisi_aktif       ON divisi (aktif);