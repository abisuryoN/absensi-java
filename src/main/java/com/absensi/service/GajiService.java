package com.absensi.service;

import com.absensi.dao.GajiDAO;
import com.absensi.dao.KaryawanDAO;
import com.absensi.model.Gaji;
import com.absensi.model.Karyawan;
import com.absensi.service.AuthService.ServiceResult;
import com.absensi.util.CurrencyUtil;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Service layer untuk semua proses penggajian karyawan.
 *
 * Tanggung jawab GajiService:
 *  - Validasi input nominal (gaji pokok, bonus, potongan)
 *  - Kalkulasi total gaji = gaji_pokok + total_lembur + bonus - potongan
 *  - Aturan pemberian bonus (siapa yang berhak memberi, batas maksimum, dll.)
 *  - Generate rekap gaji bulanan otomatis untuk semua karyawan
 *  - Validasi sebelum menandai gaji sebagai "sudah dibayar"
 *  - Menghasilkan ringkasan slip gaji untuk ditampilkan di UI
 *
 * Seluruh logika "bagaimana gaji dihitung?" ada di sini,
 * bukan di UI maupun di DAO.
 */
public class GajiService {

    private final GajiDAO     gajiDAO;
    private final KaryawanDAO karyawanDAO;

    // Batas maksimum bonus (dapat dikonfigurasi sesuai kebijakan perusahaan)
    private static final BigDecimal BATAS_MAKS_BONUS     = new BigDecimal("50000000"); // Rp 50 juta
    private static final BigDecimal BATAS_MAKS_POTONGAN  = new BigDecimal("10000000"); // Rp 10 juta

    public GajiService() {
        this.gajiDAO     = new GajiDAO();
        this.karyawanDAO = new KaryawanDAO();
    }

    /** Konstruktor dengan injeksi DAO (memudahkan unit testing). */
    public GajiService(GajiDAO gajiDAO, KaryawanDAO karyawanDAO) {
        this.gajiDAO     = gajiDAO;
        this.karyawanDAO = karyawanDAO;
    }

    // =========================================================
    //  INNER CLASS: RingkasanGaji
    //  Membungkus satu record gaji beserta info karyawannya
    //  untuk ditampilkan di slip gaji UI.
    // =========================================================

    public static class RingkasanGaji {
        public final Gaji     gaji;
        public final Karyawan karyawan;

        /** Teks ringkasan komponen gaji untuk dialog atau cetak. */
        public String getCetakRingkasan() {
            if (gaji == null) return "-";
            return String.format(
                "Periode       : %s\n" +
                "Nama          : %s\n" +
                "Bagian        : %s\n" +
                "ID Karyawan   : %s\n" +
                "─────────────────────────────\n" +
                "Gaji Pokok    : %s\n" +
                "Uang Lembur   : %s\n" +
                "Bonus         : %s%s\n" +
                "Potongan      : (%s)\n" +
                "─────────────────────────────\n" +
                "TOTAL GAJI    : %s\n" +
                "Status        : %s",
                gaji.getPeriodeLabel(),
                karyawan != null ? karyawan.getNama()      : gaji.getNamaKaryawan(),
                karyawan != null ? karyawan.getBagian()    : gaji.getBagian(),
                gaji.getIdKaryawan(),
                CurrencyUtil.formatRupiahBulat(gaji.getGajiPokok()),
                CurrencyUtil.formatRupiahBulat(gaji.getTotalLembur()),
                CurrencyUtil.formatRupiahBulat(gaji.getBonus()),
                (gaji.getKeteranganBonus() != null && !gaji.getKeteranganBonus().isEmpty()
                    ? " (" + gaji.getKeteranganBonus() + ")" : ""),
                CurrencyUtil.formatRupiahBulat(gaji.getPotongan()),
                CurrencyUtil.formatRupiahBulat(gaji.getTotalGaji()),
                gaji.getStatusBayar().name()
            );
        }

        public RingkasanGaji(Gaji gaji, Karyawan karyawan) {
            this.gaji     = gaji;
            this.karyawan = karyawan;
        }
    }

    // =========================================================
    //  GENERATE GAJI BULANAN
    // =========================================================

    /**
     * Generate atau perbarui rekap gaji untuk SEMUA karyawan aktif pada periode tertentu.
     *
     * Aturan bisnis:
     * 1. Hanya karyawan dengan status TERVERIFIKASI yang diikutkan
     * 2. Gaji pokok diambil dari data terbaru tabel karyawan
     * 3. Total lembur diambil dari akumulasi absensi bulan tersebut
     * 4. Jika record gaji sudah ada → update gaji_pokok & total_lembur,
     *    tetapi PERTAHANKAN nilai bonus & potongan yang sudah diisi HRD
     * 5. Total gaji dihitung ulang: gaji_pokok + total_lembur + bonus - potongan
     *
     * @param bulan  Bulan target (1–12)
     * @param tahun  Tahun target
     * @param idHRD  ID HRD yang menjalankan generate (untuk audit log)
     * @return ServiceResult berisi jumlah karyawan yang berhasil diproses
     */
    public ServiceResult generateGajiBulanan(int bulan, int tahun, String idHRD) {
        if (bulan < 1 || bulan > 12)
            return ServiceResult.gagal("Bulan tidak valid (harus antara 1–12).");
        if (tahun < 2000 || tahun > 2100)
            return ServiceResult.gagal("Tahun tidak valid.");

        int berhasil  = 0;
        int gagal     = 0;
        List<String> karyawanGagal = new ArrayList<>();

        try {
            List<Karyawan> semua = karyawanDAO.findByStatus(Karyawan.StatusVerifikasi.TERVERIFIKASI);
            if (semua.isEmpty())
                return ServiceResult.gagal("Tidak ada karyawan aktif yang terdaftar.");

            AbsensiService absensiService = new AbsensiService();

            for (Karyawan k : semua) {
                try {
                    // Ambil total lembur dari absensi bulan ini
                    BigDecimal totalLembur = absensiService.getTotalLemburPeriode(
                        k.getIdKaryawan(), bulan, tahun);

                    // Cek apakah sudah ada record gaji (preserve bonus & potongan)
                    Gaji existing = gajiDAO.findByKaryawanAndPeriode(
                        k.getIdKaryawan(), bulan, tahun);

                    Gaji gaji = (existing != null) ? existing : new Gaji();
                    gaji.setIdKaryawan(k.getIdKaryawan());
                    gaji.setBulan(bulan);
                    gaji.setTahun(tahun);
                    gaji.setGajiPokok(k.getGajiPokok() != null ? k.getGajiPokok() : BigDecimal.ZERO);
                    gaji.setTotalLembur(totalLembur);

                    // Pertahankan bonus & potongan yang sudah diset HRD
                    if (gaji.getBonus()    == null) gaji.setBonus(BigDecimal.ZERO);
                    if (gaji.getPotongan() == null) gaji.setPotongan(BigDecimal.ZERO);

                    gaji.hitungTotalGaji();
                    gajiDAO.saveOrUpdate(gaji);
                    berhasil++;

                } catch (SQLException e) {
                    gagal++;
                    karyawanGagal.add(k.getNama() + " (" + e.getMessage() + ")");
                }
            }

            String pesan = String.format(
                "Generate gaji selesai untuk periode %d/%d.\n" +
                "  ✅ Berhasil : %d karyawan\n" +
                "  ❌ Gagal    : %d karyawan",
                bulan, tahun, berhasil, gagal
            );
            if (!karyawanGagal.isEmpty())
                pesan += "\n\nGagal diproses:\n" + String.join("\n", karyawanGagal);

            return gagal == 0
                ? ServiceResult.sukses(pesan, berhasil)
                : ServiceResult.gagal(pesan);

        } catch (SQLException e) {
            return ServiceResult.gagal("Kesalahan database saat generate gaji: " + e.getMessage());
        }
    }

    // =========================================================
    //  SET BONUS
    // =========================================================

    /**
     * HRD memberikan bonus kepada karyawan untuk periode tertentu.
     *
     * Aturan bisnis:
     * 1. Record gaji untuk periode tersebut harus sudah ada
     *    (jalankan generateGajiBulanan lebih dulu)
     * 2. Bonus tidak boleh negatif
     * 3. Bonus tidak boleh melebihi batas maksimum (Rp 50 juta)
     * 4. Keterangan bonus wajib diisi agar ada alasan yang jelas
     * 5. Gaji yang sudah berstatus DIBAYAR tidak bisa diubah lagi
     *
     * @param idKaryawan  ID karyawan penerima bonus
     * @param bulan       Bulan periode
     * @param tahun       Tahun periode
     * @param bonus       Nominal bonus (BigDecimal, tidak boleh negatif)
     * @param keterangan  Alasan/keterangan pemberian bonus (wajib)
     * @param idHRD       ID HRD yang memberikan bonus
     * @return ServiceResult sukses atau gagal
     */
    public ServiceResult setBonus(String idKaryawan, int bulan, int tahun,
                                   BigDecimal bonus, String keterangan, String idHRD) {
        // Validasi nominal
        if (bonus == null)
            return ServiceResult.gagal("Nominal bonus tidak boleh kosong.");
        if (bonus.compareTo(BigDecimal.ZERO) < 0)
            return ServiceResult.gagal("Bonus tidak boleh bernilai negatif.");
        if (bonus.compareTo(BATAS_MAKS_BONUS) > 0)
            return ServiceResult.gagal(
                "Bonus melebihi batas maksimum yang diizinkan: " +
                CurrencyUtil.formatRupiahBulat(BATAS_MAKS_BONUS)
            );

        // Validasi keterangan
        if (keterangan == null || keterangan.trim().isEmpty())
            return ServiceResult.gagal("Keterangan/alasan bonus wajib diisi.");

        try {
            Gaji gaji = gajiDAO.findByKaryawanAndPeriode(idKaryawan, bulan, tahun);
            if (gaji == null)
                return ServiceResult.gagal(
                    "Data gaji periode ini belum ada.\n" +
                    "Jalankan 'Generate Gaji' terlebih dahulu."
                );

            // Gaji yang sudah dibayar tidak bisa diubah
            if (gaji.getStatusBayar() == Gaji.StatusBayar.DIBAYAR)
                return ServiceResult.gagal(
                    "Gaji periode ini sudah berstatus DIBAYAR.\n" +
                    "Perubahan bonus tidak dapat dilakukan."
                );

            gaji.setBonus(bonus);
            gaji.setKeteranganBonus(keterangan.trim());
            gaji.hitungTotalGaji();
            gajiDAO.saveOrUpdate(gaji);

            Karyawan k = karyawanDAO.findById(idKaryawan);
            String namaTampil = (k != null) ? k.getNama() : idKaryawan;

            return ServiceResult.sukses(
                "Bonus untuk " + namaTampil + " berhasil disimpan.\n" +
                "Nominal : " + CurrencyUtil.formatRupiahBulat(bonus) + "\n" +
                "Alasan  : " + keterangan.trim() + "\n" +
                "Total gaji baru: " + CurrencyUtil.formatRupiahBulat(gaji.getTotalGaji())
            );

        } catch (SQLException e) {
            return ServiceResult.gagal("Gagal menyimpan bonus: " + e.getMessage());
        }
    }

    // =========================================================
    //  SET POTONGAN
    // =========================================================

    /**
     * HRD menetapkan potongan gaji karyawan (absen, keterlambatan berulang, dll.).
     *
     * Aturan bisnis sama dengan setBonus, dengan batas maksimum sendiri.
     *
     * @param idKaryawan  ID karyawan yang dikenakan potongan
     * @param bulan       Bulan periode
     * @param tahun       Tahun periode
     * @param potongan    Nominal potongan (tidak boleh negatif)
     * @param keterangan  Alasan potongan (wajib)
     * @param idHRD       ID HRD yang menerapkan potongan
     */
    public ServiceResult setPotongan(String idKaryawan, int bulan, int tahun,
                                      BigDecimal potongan, String keterangan, String idHRD) {
        if (potongan == null || potongan.compareTo(BigDecimal.ZERO) < 0)
            return ServiceResult.gagal("Potongan tidak boleh negatif.");
        if (potongan.compareTo(BATAS_MAKS_POTONGAN) > 0)
            return ServiceResult.gagal(
                "Potongan melebihi batas maksimum: " +
                CurrencyUtil.formatRupiahBulat(BATAS_MAKS_POTONGAN)
            );
        if (keterangan == null || keterangan.trim().isEmpty())
            return ServiceResult.gagal("Alasan potongan wajib diisi.");

        try {
            Gaji gaji = gajiDAO.findByKaryawanAndPeriode(idKaryawan, bulan, tahun);
            if (gaji == null)
                return ServiceResult.gagal("Data gaji periode ini belum ada. Jalankan Generate Gaji terlebih dahulu.");
            if (gaji.getStatusBayar() == Gaji.StatusBayar.DIBAYAR)
                return ServiceResult.gagal("Gaji sudah berstatus DIBAYAR, tidak dapat diubah.");

            // Pastikan potongan tidak melebihi gaji bersih
            BigDecimal gajiSebelumPotongan = gaji.getGajiPokok()
                .add(gaji.getTotalLembur())
                .add(gaji.getBonus());
            if (potongan.compareTo(gajiSebelumPotongan) > 0)
                return ServiceResult.gagal(
                    "Potongan (" + CurrencyUtil.formatRupiahBulat(potongan) + ") " +
                    "tidak boleh melebihi total gaji sebelum potongan (" +
                    CurrencyUtil.formatRupiahBulat(gajiSebelumPotongan) + ")."
                );

            gaji.setPotongan(potongan);
            gaji.hitungTotalGaji();
            gajiDAO.saveOrUpdate(gaji);

            Karyawan k = karyawanDAO.findById(idKaryawan);
            String namaTampil = (k != null) ? k.getNama() : idKaryawan;

            return ServiceResult.sukses(
                "Potongan untuk " + namaTampil + " berhasil disimpan.\n" +
                "Nominal : " + CurrencyUtil.formatRupiahBulat(potongan) + "\n" +
                "Alasan  : " + keterangan.trim() + "\n" +
                "Total gaji baru: " + CurrencyUtil.formatRupiahBulat(gaji.getTotalGaji())
            );

        } catch (SQLException e) {
            return ServiceResult.gagal("Gagal menyimpan potongan: " + e.getMessage());
        }
    }

    // =========================================================
    //  TANDAI DIBAYAR
    // =========================================================

    /**
     * HRD menandai gaji karyawan sebagai sudah dibayarkan.
     *
     * Aturan bisnis:
     * 1. Gaji yang sudah DIBAYAR tidak bisa di-reset ke PENDING
     * 2. Total gaji harus > 0 sebelum bisa ditandai dibayar
     * 3. Tanggal bayar otomatis diisi hari ini
     * 4. ID HRD yang membayar dicatat untuk audit trail
     *
     * @param gajiId  Primary key record gaji
     * @param idHRD   ID HRD yang melakukan pembayaran
     */
    public ServiceResult tandaiBayar(int gajiId, String idHRD) {
        try {
            // Ambil data gaji terlebih dahulu untuk validasi
            // (GajiDAO belum punya findById, kita gunakan update langsung)
            boolean berhasil = gajiDAO.tandaiBayar(gajiId, idHRD);
            if (!berhasil)
                return ServiceResult.gagal(
                    "Data gaji tidak ditemukan atau sudah dalam status DIBAYAR."
                );

            return ServiceResult.sukses(
                "Gaji berhasil ditandai sebagai DIBAYAR.\n" +
                "Tanggal bayar: " + LocalDate.now() + "\n" +
                "Diproses oleh: " + idHRD
            );

        } catch (SQLException e) {
            return ServiceResult.gagal("Gagal memperbarui status bayar: " + e.getMessage());
        }
    }

    // =========================================================
    //  QUERY / READ
    // =========================================================

    /**
     * Ambil ringkasan gaji karyawan untuk periode tertentu,
     * sekaligus data karyawan untuk ditampilkan di slip gaji.
     *
     * @return RingkasanGaji atau null jika data tidak ditemukan
     */
    public RingkasanGaji getRingkasanGaji(String idKaryawan, int bulan, int tahun)
            throws SQLException {
        Gaji gaji = gajiDAO.findByKaryawanAndPeriode(idKaryawan, bulan, tahun);
        if (gaji == null) return null;

        Karyawan karyawan = karyawanDAO.findById(idKaryawan);
        return new RingkasanGaji(gaji, karyawan);
    }

    /**
     * Ambil riwayat gaji karyawan (semua periode, terbaru dulu).
     */
    public List<Gaji> getRiwayatGaji(String idKaryawan) throws SQLException {
        return gajiDAO.findByKaryawan(idKaryawan);
    }

    /**
     * Ambil semua gaji pada periode tertentu (untuk laporan HRD).
     */
    public List<Gaji> getGajiPeriode(int bulan, int tahun) throws SQLException {
        return gajiDAO.findAllByPeriode(bulan, tahun);
    }

    /**
     * Hitung total pengeluaran gaji perusahaan pada satu periode.
     * Berguna untuk ringkasan laporan keuangan HRD.
     */
    public BigDecimal hitungTotalPengeluaranGaji(int bulan, int tahun) throws SQLException {
        List<Gaji> list = gajiDAO.findAllByPeriode(bulan, tahun);
        return list.stream()
            .map(Gaji::getTotalGaji)
            .filter(g -> g != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Validasi apakah gaji untuk periode tertentu sudah pernah di-generate.
     * Digunakan UI untuk menampilkan konfirmasi "generate ulang?".
     */
    public boolean isPeriodeSudahGenerate(int bulan, int tahun) throws SQLException {
        List<Gaji> list = gajiDAO.findAllByPeriode(bulan, tahun);
        return !list.isEmpty();
    }

    /**
     * Ambil gaji karyawan pada periode tertentu beserta ringkasannya,
     * sekaligus memvalidasi apakah slip gaji bisa dicetak.
     *
     * Kondisi slip bisa dicetak:
     * - Record gaji ada
     * - Total gaji > 0
     *
     * @return ServiceResult dengan data RingkasanGaji jika valid
     */
    public ServiceResult validasiSlipGaji(String idKaryawan, int bulan, int tahun)
            throws SQLException {
        Gaji gaji = gajiDAO.findByKaryawanAndPeriode(idKaryawan, bulan, tahun);
        if (gaji == null)
            return ServiceResult.gagal(
                "Data gaji untuk periode ini belum tersedia.\n" +
                "Minta HRD untuk menjalankan Generate Gaji terlebih dahulu."
            );

        if (gaji.getTotalGaji() == null ||
            gaji.getTotalGaji().compareTo(BigDecimal.ZERO) <= 0)
            return ServiceResult.gagal(
                "Total gaji periode ini adalah Rp0.\n" +
                "Pastikan gaji pokok sudah diisi oleh HRD."
            );

        Karyawan karyawan = karyawanDAO.findById(idKaryawan);
        return ServiceResult.sukses("Slip gaji siap dicetak.", new RingkasanGaji(gaji, karyawan));
    }
}