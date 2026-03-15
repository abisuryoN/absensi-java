package com.absensi.util;

import java.awt.*;

/**
 * Konstanta tema visual untuk tampilan aplikasi.
 * Tema: Modern Corporate - Biru gelap dengan aksen hijau mint.
 */
public class UITheme {

    // ===================== WARNA UTAMA =====================
    public static final Color PRIMARY        = new Color(0x1B2A4A);  // Navy Blue gelap
    public static final Color PRIMARY_LIGHT  = new Color(0x2E4272);  // Navy Blue lebih terang
    public static final Color ACCENT         = new Color(0x00C9A7);  // Hijau mint (aksen)
    public static final Color ACCENT_HOVER   = new Color(0x00A88C);  // Aksen hover

    // ===================== WARNA STATUS =====================
    public static final Color SUCCESS        = new Color(0x28A745);  // Hijau sukses
    public static final Color WARNING        = new Color(0xFFC107);  // Kuning peringatan
    public static final Color DANGER         = new Color(0xDC3545);  // Merah error/bahaya
    public static final Color INFO           = new Color(0x17A2B8);  // Biru info

    // ===================== WARNA LATAR =====================
    public static final Color BG_MAIN        = new Color(0xF0F4F8);  // Abu-abu sangat terang
    public static final Color BG_CARD        = Color.WHITE;
    public static final Color BG_SIDEBAR     = new Color(0x1B2A4A);  // Sama dengan PRIMARY
    public static final Color BG_HEADER      = new Color(0x1B2A4A);

    // ===================== WARNA TEKS =====================
    public static final Color TEXT_PRIMARY   = new Color(0x1A1A2E);  // Teks utama gelap
    public static final Color TEXT_SECONDARY = new Color(0x6C757D);  // Teks sekunder abu-abu
    public static final Color TEXT_LIGHT     = new Color(0xF8F9FA);  // Teks di latar gelap
    public static final Color TEXT_MUTED     = new Color(0xADB5BD);  // Teks disabilkan

    // ===================== WARNA BORDER =====================
    public static final Color BORDER         = new Color(0xDEE2E6);
    public static final Color BORDER_FOCUS   = ACCENT;

    // ===================== WARNA TABEL =====================
    public static final Color TABLE_HEADER   = new Color(0x1B2A4A);
    public static final Color TABLE_ROW_ODD  = Color.WHITE;
    public static final Color TABLE_ROW_EVEN = new Color(0xF8F9FA);
    public static final Color TABLE_SELECT   = new Color(0xCCF0EB);  // Hijau mint muda

    // ===================== FONT =====================
    public static final Font FONT_TITLE      = new Font("Segoe UI", Font.BOLD, 24);
    public static final Font FONT_SUBTITLE   = new Font("Segoe UI", Font.BOLD, 16);
    public static final Font FONT_BODY       = new Font("Segoe UI", Font.PLAIN, 14);
    public static final Font FONT_SMALL      = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font FONT_BOLD       = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font FONT_HEADER     = new Font("Segoe UI", Font.BOLD, 18);

    // ===================== DIMENSI =====================
    public static final int SIDEBAR_WIDTH    = 290;
    public static final int HEADER_HEIGHT    = 65;
    public static final int PADDING          = 20;
    public static final int CARD_RADIUS      = 12;
    public static final int BTN_HEIGHT       = 38;

    // ===================== HELPER METHODS =====================

    /**
     * Buat warna dengan transparansi.
     */
    public static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    /**
     * Warna untuk badge status absensi.
     */
    public static Color getStatusColor(boolean isTelat) {
        return isTelat ? DANGER : SUCCESS;
    }

    /**
     * Warna untuk badge role karyawan.
     */
    public static Color getRoleColor(String role) {
        return "HRD".equals(role) ? new Color(0x6F42C1) : INFO;
    }
}
