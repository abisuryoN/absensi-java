package com.absensi.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Utility untuk format mata uang Rupiah Indonesia.
 */
public class CurrencyUtil {

    private static final Locale LOCALE_ID = new Locale("id", "ID");
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(LOCALE_ID);

    /**
     * Format BigDecimal ke format Rupiah.
     * Contoh: 5000000.00 -> "Rp5.000.000,00"
     */
    public static String formatRupiah(BigDecimal amount) {
        if (amount == null) return "Rp0";
        return CURRENCY_FORMAT.format(amount);
    }

    /**
     * Format long ke format Rupiah tanpa desimal.
     * Contoh: 5000000 -> "Rp5.000.000"
     */
    public static String formatRupiahBulat(BigDecimal amount) {
        if (amount == null) return "Rp0";
        NumberFormat format = NumberFormat.getIntegerInstance(LOCALE_ID);
        return "Rp" + format.format(amount.longValue());
    }

    /**
     * Parse string Rupiah kembali ke BigDecimal.
     */
    public static BigDecimal parseRupiah(String rupiahStr) {
        if (rupiahStr == null || rupiahStr.trim().isEmpty()) return BigDecimal.ZERO;
        // Hapus semua karakter non-digit kecuali titik desimal
        String cleaned = rupiahStr.replaceAll("[^0-9,]", "").replace(",", ".");
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}