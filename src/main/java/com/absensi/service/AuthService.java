package com.absensi.service;

import com.absensi.dao.KaryawanDAO;
import com.absensi.model.Karyawan;
import com.absensi.util.ImageUtil;

import java.sql.SQLException;

/**
 * Service layer untuk semua proses autentikasi dan manajemen akun karyawan.
 *
 * Tanggung jawab AuthService:
 *  - Validasi input registrasi (format, keunikan, kelengkapan)
 *  - Proses login dan pemeriksaan status akun
 *  - Aturan penetapan role (mencegah HRD palsu)
 *  - Verifikasi dan penolakan akun oleh HRD
 *  - Koreksi bagian/divisi karyawan oleh HRD
 *
 * AuthService TIDAK boleh:
 *  - Menyentuh UI (JOptionPane, Swing) secara langsung
 *  - Berisi query SQL (itu tugas DAO)
 *
 * UI memanggil Service → Service memanggil DAO → DAO ke MySQL
 */
public class AuthService {

    private final KaryawanDAO karyawanDAO;

    public AuthService() {
        this.karyawanDAO = new KaryawanDAO();
    }

    /** Konstruktor dengan injeksi DAO (memudahkan unit testing). */
    public AuthService(KaryawanDAO karyawanDAO) {
        this.karyawanDAO = karyawanDAO;
    }

    // =========================================================
    //  INNER CLASS: ServiceResult
    //  Membungkus hasil operasi service agar UI bisa membaca
    //  apakah sukses/gagal dan pesan apa yang perlu ditampilkan.
    // =========================================================

    public static class ServiceResult {
        private final boolean sukses;
        private final String  pesan;
        private final Object  data;     // payload opsional (mis. objek Karyawan)

        private ServiceResult(boolean sukses, String pesan, Object data) {
            this.sukses = sukses;
            this.pesan  = pesan;
            this.data   = data;
        }

        public static ServiceResult sukses(String pesan)              { return new ServiceResult(true,  pesan, null); }
        public static ServiceResult sukses(String pesan, Object data) { return new ServiceResult(true,  pesan, data); }
        public static ServiceResult gagal(String pesan)               { return new ServiceResult(false, pesan, null); }

        public boolean isSukses()    { return sukses; }
        public String  getPesan()    { return pesan;  }
        @SuppressWarnings("unchecked")
        public <T> T   getData()     { return (T) data; }
    }

    // =========================================================
    //  REGISTRASI
    // =========================================================

    /**
     * Proses registrasi akun karyawan baru.
     *
     * Aturan bisnis yang divalidasi:
     * 1. Nama dan ID karyawan tidak boleh kosong
     * 2. ID karyawan harus unik
     * 3. Nama harus unik (dipakai sebagai username login)
     * 4. Foto selfie wajib ada
     * 5. Bagian tidak boleh kosong
     * 6. Semua akun baru selalu diberi role KARYAWAN + status PENDING,
     *    terlepas dari bagian yang dipilih — HRD yang verifikasi nanti
     *
     * @param idKaryawan  ID unik karyawan
     * @param nama        Nama lengkap (sekaligus username login)
     * @param bagian      Nama divisi/bagian yang dipilih
     * @param fotoBytes   Byte array foto selfie (wajib)
     * @return ServiceResult sukses atau gagal dengan pesan penjelasan
     */
    public ServiceResult registrasi(String idKaryawan, String nama,
                                    String bagian,     byte[] fotoBytes) {
        // --- Validasi format input ---
        if (idKaryawan == null || idKaryawan.trim().isEmpty())
            return ServiceResult.gagal("ID Karyawan tidak boleh kosong.");

        if (nama == null || nama.trim().isEmpty())
            return ServiceResult.gagal("Nama tidak boleh kosong.");

        if (bagian == null || bagian.trim().isEmpty())
            return ServiceResult.gagal("Bagian/divisi harus dipilih.");

        if (fotoBytes == null || fotoBytes.length == 0)
            return ServiceResult.gagal("Foto selfie wajib diambil untuk registrasi.");

        idKaryawan = idKaryawan.trim().toUpperCase();
        nama       = nama.trim();

        if (idKaryawan.length() > 20)
            return ServiceResult.gagal("ID Karyawan maksimal 20 karakter.");

        if (nama.length() > 100)
            return ServiceResult.gagal("Nama maksimal 100 karakter.");

        // --- Validasi keunikan ke database ---
        try {
            if (karyawanDAO.isIdKaryawanExist(idKaryawan))
                return ServiceResult.gagal(
                    "ID Karyawan '" + idKaryawan + "' sudah terdaftar.\n" +
                    "Gunakan ID yang berbeda atau hubungi HRD."
                );

            if (karyawanDAO.isNamaExist(nama))
                return ServiceResult.gagal(
                    "Nama '" + nama + "' sudah terdaftar.\n" +
                    "Gunakan nama lengkap yang berbeda (mis. tambahkan inisial)."
                );

            // --- Buat objek Karyawan baru ---
            Karyawan karyawan = new Karyawan(idKaryawan, nama, bagian);
            String pathFoto = ImageUtil.simpanFotoProfil(fotoBytes, idKaryawan);
            karyawan.setFotoProfil(pathFoto);

            // ATURAN BISNIS KRITIS:
            // Semua akun baru SELALU role KARYAWAN dan status PENDING.
            // Meskipun memilih bagian "HRD", role tidak langsung HRD.
            // HRD yang bertugas memverifikasi dan koreksi jika ada pemalsuan.
            karyawan.setRole(Karyawan.Role.KARYAWAN);
            karyawan.setStatusVerifikasi(Karyawan.StatusVerifikasi.PENDING);
            karyawan.setGajiPokok(java.math.BigDecimal.ZERO);

            boolean berhasil = karyawanDAO.registrasi(karyawan);
            if (!berhasil)
                return ServiceResult.gagal("Registrasi gagal karena kesalahan sistem. Coba lagi.");

            return ServiceResult.sukses(
                "Registrasi berhasil!\n\n" +
                "Akun Anda sedang menunggu verifikasi HRD.\n" +
                "Setelah diverifikasi, login menggunakan:\n" +
                "  • Username : " + nama + "\n" +
                "  • Password : " + idKaryawan
            );

        } catch (SQLException e) {
            return ServiceResult.gagal("Kesalahan database saat registrasi: " + e.getMessage());
        }
    }

    // =========================================================
    //  LOGIN
    // =========================================================

    /**
     * Proses autentikasi login karyawan.
     *
     * Aturan bisnis:
     * 1. Username = nama lengkap, Password = ID karyawan
     * 2. Jika credential salah → gagal dengan pesan umum (tidak bocorkan detail)
     * 3. Jika status PENDING → gagal dengan instruksi hubungi HRD
     * 4. Jika status DITOLAK → gagal dengan pesan khusus
     * 5. Jika TERVERIFIKASI → sukses, kembalikan objek Karyawan sebagai data
     *
     * @param nama        Username (nama lengkap)
     * @param idKaryawan  Password (ID karyawan)
     * @return ServiceResult dengan data Karyawan jika sukses
     */
    public ServiceResult login(String nama, String idKaryawan) {
        if (nama == null || nama.trim().isEmpty())
            return ServiceResult.gagal("Nama (username) tidak boleh kosong.");

        if (idKaryawan == null || idKaryawan.trim().isEmpty())
            return ServiceResult.gagal("ID Karyawan (password) tidak boleh kosong.");

        try {
            Karyawan karyawan = karyawanDAO.login(nama.trim(), idKaryawan.trim());

            if (karyawan == null)
                return ServiceResult.gagal(
                    "Nama atau ID Karyawan salah.\n" +
                    "Periksa kembali data Anda atau hubungi HRD."
                );

            // Cek status verifikasi
            switch (karyawan.getStatusVerifikasi()) {
                case PENDING:
                    return ServiceResult.gagal(
                        "Akun Anda belum diverifikasi oleh HRD.\n\n" +
                        "Silakan hubungi divisi HRD untuk aktivasi akun.\n" +
                        "ID Karyawan Anda: " + karyawan.getIdKaryawan()
                    );

                case DITOLAK:
                    return ServiceResult.gagal(
                        "Akun Anda ditolak oleh HRD.\n\n" +
                        "Kemungkinan penyebab: data tidak valid atau bagian kerja tidak sesuai.\n" +
                        "Hubungi HRD untuk informasi lebih lanjut."
                    );

                case TERVERIFIKASI:
                    return ServiceResult.sukses(
                        "Login berhasil. Selamat datang, " + karyawan.getNama() + "!",
                        karyawan
                    );

                default:
                    return ServiceResult.gagal("Status akun tidak dikenali. Hubungi administrator.");
            }

        } catch (SQLException e) {
            return ServiceResult.gagal("Kesalahan database saat login: " + e.getMessage());
        }
    }

    // =========================================================
    //  VERIFIKASI OLEH HRD
    // =========================================================

    /**
     * HRD memverifikasi (mengaktifkan) akun karyawan yang masih PENDING.
     *
     * Aturan bisnis:
     * - Hanya bisa memverifikasi akun yang berstatus PENDING
     * - Setelah diverifikasi, karyawan bisa login
     * - Jika bagian karyawan adalah "HRD" dan HRD yakin itu benar,
     *   otomatis upgrade role ke HRD
     *
     * @param idKaryawanTarget  ID karyawan yang diverifikasi
     * @param idHRD             ID HRD yang melakukan verifikasi (untuk audit)
     */
    public ServiceResult verifikasiAkun(String idKaryawanTarget, String idHRD) {
        try {
            Karyawan target = karyawanDAO.findById(idKaryawanTarget);
            if (target == null)
                return ServiceResult.gagal("Karyawan dengan ID '" + idKaryawanTarget + "' tidak ditemukan.");

            if (target.getStatusVerifikasi() == Karyawan.StatusVerifikasi.TERVERIFIKASI)
                return ServiceResult.gagal("Akun " + target.getNama() + " sudah terverifikasi sebelumnya.");

            // Verifikasi akun
            karyawanDAO.updateStatusVerifikasi(idKaryawanTarget, Karyawan.StatusVerifikasi.TERVERIFIKASI);

            // Jika bagian adalah HRD, upgrade role otomatis
            if ("HRD".equalsIgnoreCase(target.getBagian()) ||
                "Human Resources".equalsIgnoreCase(target.getBagian())) {
                karyawanDAO.updateBagian(idKaryawanTarget, target.getBagian()); // updateBagian juga set role HRD
            }

            return ServiceResult.sukses(
                "Akun " + target.getNama() + " (" + target.getIdKaryawan() + ") berhasil diverifikasi.\n" +
                "Karyawan sekarang bisa login ke sistem."
            );

        } catch (SQLException e) {
            return ServiceResult.gagal("Gagal verifikasi akun: " + e.getMessage());
        }
    }

    /**
     * HRD menolak akun karyawan yang dianggap tidak valid.
     *
     * @param idKaryawanTarget  ID karyawan yang ditolak
     * @param idHRD             ID HRD yang melakukan penolakan
     */
    public ServiceResult tolakAkun(String idKaryawanTarget, String idHRD) {
        try {
            Karyawan target = karyawanDAO.findById(idKaryawanTarget);
            if (target == null)
                return ServiceResult.gagal("Karyawan tidak ditemukan.");

            karyawanDAO.updateStatusVerifikasi(idKaryawanTarget, Karyawan.StatusVerifikasi.DITOLAK);

            return ServiceResult.sukses(
                "Akun " + target.getNama() + " ditolak.\n" +
                "Karyawan tidak dapat login sampai diverifikasi ulang."
            );

        } catch (SQLException e) {
            return ServiceResult.gagal("Gagal menolak akun: " + e.getMessage());
        }
    }

    // =========================================================
    //  KOREKSI BAGIAN OLEH HRD (Pencegahan Role Palsu)
    // =========================================================

    /**
     * HRD mengoreksi bagian kerja karyawan yang tidak sesuai.
     *
     * Ini adalah mekanisme utama untuk mencegah karyawan mengklaim
     * bagian HRD secara tidak sah. HRD bisa mengubah bagian ke
     * nilai yang benar, dan role akan menyesuaikan otomatis.
     *
     * Aturan bisnis:
     * - Hanya HRD yang bisa mengubah bagian karyawan lain
     * - HRD tidak bisa mengubah bagian dirinya sendiri
     *   (mencegah HRD tunggal mengubah data dirinya)
     * - Jika bagian baru = "HRD", role otomatis jadi HRD
     * - Jika bagian baru bukan HRD, role diturunkan ke KARYAWAN
     *
     * @param idKaryawanTarget  ID karyawan yang dikoreksi
     * @param bagianBaru        Nama bagian yang benar
     * @param idHRD             ID HRD yang melakukan koreksi
     */
    public ServiceResult koreksiDivisi(String idKaryawanTarget, String bagianBaru, String idHRD) {
        if (bagianBaru == null || bagianBaru.trim().isEmpty())
            return ServiceResult.gagal("Nama bagian baru tidak boleh kosong.");

        // HRD tidak boleh mengubah bagian dirinya sendiri lewat menu ini
        if (idKaryawanTarget.equalsIgnoreCase(idHRD))
            return ServiceResult.gagal(
                "Anda tidak dapat mengubah bagian akun Anda sendiri melalui menu ini.\n" +
                "Hubungi HRD lain atau administrator sistem."
            );

        try {
            Karyawan target = karyawanDAO.findById(idKaryawanTarget);
            if (target == null)
                return ServiceResult.gagal("Karyawan dengan ID '" + idKaryawanTarget + "' tidak ditemukan.");

            String bagianLama = target.getBagian();
            karyawanDAO.updateBagian(idKaryawanTarget, bagianBaru.trim());

            // Tentukan role baru untuk pesan konfirmasi
            boolean jadiHRD = "HRD".equalsIgnoreCase(bagianBaru.trim()) ||
                               "Human Resources".equalsIgnoreCase(bagianBaru.trim());

            return ServiceResult.sukses(
                "Bagian " + target.getNama() + " berhasil diubah:\n" +
                "  Sebelum : " + bagianLama + "\n" +
                "  Sesudah : " + bagianBaru.trim() + "\n" +
                "  Role    : " + (jadiHRD ? "HRD" : "KARYAWAN")
            );

        } catch (SQLException e) {
            return ServiceResult.gagal("Gagal mengubah bagian karyawan: " + e.getMessage());
        }
    }

    // =========================================================
    //  PENGELOLAAN GAJI POKOK OLEH HRD
    // =========================================================

    /**
     * HRD menetapkan atau mengubah gaji pokok karyawan.
     *
     * @param idKaryawan  ID karyawan yang diubah gajinya
     * @param gajiPokok   Nominal gaji pokok baru (harus > 0)
     * @param idHRD       ID HRD yang melakukan perubahan
     */
    public ServiceResult setGajiPokok(String idKaryawan, java.math.BigDecimal gajiPokok, String idHRD) {
        if (gajiPokok == null || gajiPokok.compareTo(java.math.BigDecimal.ZERO) < 0)
            return ServiceResult.gagal("Gaji pokok tidak boleh negatif.");

        if (gajiPokok.compareTo(new java.math.BigDecimal("100000000")) > 0)
            return ServiceResult.gagal("Gaji pokok melebihi batas maksimum sistem (Rp 100.000.000).");

        try {
            Karyawan target = karyawanDAO.findById(idKaryawan);
            if (target == null)
                return ServiceResult.gagal("Karyawan tidak ditemukan.");

            karyawanDAO.updateGajiPokok(idKaryawan, gajiPokok);

            return ServiceResult.sukses(
                "Gaji pokok " + target.getNama() + " berhasil diatur ke " +
                com.absensi.util.CurrencyUtil.formatRupiahBulat(gajiPokok) + "."
            );

        } catch (SQLException e) {
            return ServiceResult.gagal("Gagal mengatur gaji pokok: " + e.getMessage());
        }
    }

    // =========================================================
    //  QUERY HELPER (digunakan UI tanpa perlu akses DAO langsung)
    // =========================================================

    /**
     * Ambil semua karyawan (untuk tabel manajemen HRD).
     */
    public java.util.List<Karyawan> getAllKaryawan() throws SQLException {
        return karyawanDAO.findAll();
    }

    /**
     * Ambil karyawan berdasarkan status verifikasi.
     */
    public java.util.List<Karyawan> getKaryawanByStatus(Karyawan.StatusVerifikasi status) throws SQLException {
        return karyawanDAO.findByStatus(status);
    }

    /**
     * Ambil data karyawan berdasarkan ID.
     */
    public Karyawan getKaryawanById(String idKaryawan) throws SQLException {
        return karyawanDAO.findById(idKaryawan);
    }
}