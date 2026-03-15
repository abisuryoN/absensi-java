package com.absensi.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility untuk hashing password menggunakan SHA-256 + Salt.
 *
 * ── Kenapa SHA-256 + Salt (bukan plain SHA-256)?
 *    Plain SHA-256 rentan terhadap rainbow table attack.
 *    Salt adalah string acak unik per user yang digabung ke password
 *    sebelum di-hash, sehingga hash password yang sama menjadi berbeda.
 *
 * ── Format yang disimpan di database:
 *    "SALT:HASH"  →  contoh: "aBcD1234...:ef12ab34..."
 *    Kedua nilai di-encode Base64 agar aman disimpan di VARCHAR.
 *
 * ── Untuk produksi skala besar, pertimbangkan BCrypt (via library
 *    org.mindrot:jbcrypt). SHA-256+salt cukup untuk aplikasi desktop
 *    skala SME seperti sistem absensi ini.
 */
public class PasswordUtil {

    private static final String ALGORITHM   = "SHA-256";
    private static final int    SALT_LENGTH = 16;  // byte

    // =========================================================
    //  HASH PASSWORD (saat registrasi / set password)
    // =========================================================

    /**
     * Hash password baru dengan salt acak.
     * Panggil saat registrasi atau mengubah password.
     *
     * @param passwordPlain Password asli dari user (misal ID karyawan)
     * @return String format "SALT:HASH" untuk disimpan ke database
     */
    public static String hashPassword(String passwordPlain) {
        if (passwordPlain == null || passwordPlain.isEmpty())
            throw new IllegalArgumentException("Password tidak boleh kosong");

        // 1. Generate salt acak
        byte[] saltBytes = generateSalt();
        String saltBase64 = Base64.getEncoder().encodeToString(saltBytes);

        // 2. Hash: SHA-256(salt + password)
        String hashBase64 = sha256(saltBase64 + passwordPlain);

        // 3. Simpan sebagai "SALT:HASH"
        return saltBase64 + ":" + hashBase64;
    }

    // =========================================================
    //  VERIFY PASSWORD (saat login)
    // =========================================================

    /**
     * Verifikasi password yang dimasukkan user terhadap hash di database.
     *
     * @param passwordPlain      Password yang diketik user saat login
     * @param saltDanHashDiDB    Nilai dari kolom password di database ("SALT:HASH")
     * @return true jika password cocok
     */
    public static boolean verifyPassword(String passwordPlain, String saltDanHashDiDB) {
        if (passwordPlain == null || saltDanHashDiDB == null) return false;

        String[] parts = saltDanHashDiDB.split(":", 2);
        if (parts.length != 2) {
            // Format lama (plain text) — untuk migrasi data lama
            // Hapus baris ini setelah semua password di-migrate
            return passwordPlain.equals(saltDanHashDiDB);
        }

        String saltBase64    = parts[0];
        String hashDiDB      = parts[1];
        String hashInput     = sha256(saltBase64 + passwordPlain);

        // Bandingkan secara constant-time untuk mencegah timing attack
        return MessageDigest.isEqual(hashInput.getBytes(), hashDiDB.getBytes());
    }

    // =========================================================
    //  MIGRASI: Hash password lama (plain text → hashed)
    // =========================================================

    /**
     * Cek apakah nilai dari database sudah di-hash (format SALT:HASH)
     * atau masih plain text (format lama).
     */
    public static boolean sudahDiHash(String nilaiDiDB) {
        return nilaiDiDB != null && nilaiDiDB.contains(":") && nilaiDiDB.split(":").length == 2;
    }

    // =========================================================
    //  PRIVATE HELPERS
    // =========================================================

    private static byte[] generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return salt;
    }

    private static String sha256(String input) {
        try {
            MessageDigest md     = MessageDigest.getInstance(ALGORITHM);
            byte[]        digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 selalu tersedia di JVM standar
            throw new RuntimeException("SHA-256 tidak tersedia", e);
        }
    }
}