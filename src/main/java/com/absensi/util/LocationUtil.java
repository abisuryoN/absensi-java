package com.absensi.util;

import com.absensi.config.DatabaseConfig;
import java.sql.*;
import java.net.*;
import java.io.*;

public class LocationUtil {

    private static double  KANTOR_LATITUDE  = -6.474124187264267;
    private static double  KANTOR_LONGITUDE = 106.74882293073968;
    private static double  RADIUS_METER     = 100.0;
    private static boolean BYPASS_LOKASI    = false;

    static {
        loadKonfigurasiKantor();
    }

    private static void loadKonfigurasiKantor() {
        try (Connection conn = DatabaseConfig.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                "SELECT kunci, nilai FROM konfigurasi");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String kunci = rs.getString("kunci");
                String nilai = rs.getString("nilai");
                switch (kunci) {
                    case "KANTOR_LATITUDE":
                        KANTOR_LATITUDE = Double.parseDouble(nilai); break;
                    case "KANTOR_LONGITUDE":
                        KANTOR_LONGITUDE = Double.parseDouble(nilai); break;
                    case "RADIUS_KANTOR":
                        RADIUS_METER = Double.parseDouble(nilai); break;
                    case "BYPASS_LOKASI":
                        BYPASS_LOKASI = "true".equalsIgnoreCase(nilai)
                            || "1".equals(nilai); break;
                }
            }
            System.out.printf("[LocationUtil] Kantor: %.6f, %.6f | " +
                "Radius: %.0fm | Bypass: %s%n",
                KANTOR_LATITUDE, KANTOR_LONGITUDE, RADIUS_METER, BYPASS_LOKASI);
        } catch (Exception e) {
            System.out.println("[LocationUtil] Gagal load konfigurasi, gunakan default.");
        }
    }

    // ── Haversine ─────────────────────────────────────────────

    public static double hitungJarak(double lat1, double lon1,
                                      double lat2, double lon2) {
        final int R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2)*Math.sin(dLat/2)
            + Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))
            *Math.sin(dLon/2)*Math.sin(dLon/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    }

    public static boolean isInKantor(double lat, double lon) {
        if (BYPASS_LOKASI) return true;
        return hitungJarak(lat, lon, KANTOR_LATITUDE, KANTOR_LONGITUDE) <= RADIUS_METER;
    }

    // ── Ambil lokasi ──────────────────────────────────────────

    public static double[] getCurrentLocation() {
        String[] apis = {
            "http://ip-api.com/json",
            "https://ipinfo.io/json",
            "https://geolocation-db.com/json/"
        };
        for (String api : apis) {
            try {
                URL url = new URL(api);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);

                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                String json = sb.toString();

                // ── DEBUG: print response mentah ──────────────
                System.out.println("[LocationUtil] API: " + api);
                System.out.println("[LocationUtil] Response: " + json);

                Double lat = parseLat(json);
                Double lon = parseLon(json);

                System.out.printf("[LocationUtil] Parsed lat=%s lon=%s%n", lat, lon);

                if (lat != null && lon != null) {
                    double jarak = hitungJarak(lat, lon,
                        KANTOR_LATITUDE, KANTOR_LONGITUDE);
                    System.out.printf("[LocationUtil] Jarak ke kantor: %.1f m " +
                        "(radius: %.0f m)%n", jarak, RADIUS_METER);
                    return new double[]{lat, lon};
                }

            } catch (Exception e) {
                System.out.println("[LocationUtil] API gagal: " + api +
                    " — " + e.getMessage());
            }
        }
        return null;
    }

    // ── evaluasiLokasi ────────────────────────────────────────

    public static LokasiResult evaluasiLokasi() {
        if (BYPASS_LOKASI) {
            System.out.println("[LocationUtil] Bypass aktif.");
            return new LokasiResult(StatusLokasi.VALID_BYPASS, 0, null);
        }

        double[] koordinat = getCurrentLocation();

        if (koordinat == null) {
            System.out.println("[LocationUtil] Semua API gagal.");
            return new LokasiResult(StatusLokasi.API_GAGAL, -1, null);
        }

        double jarak = hitungJarak(
            koordinat[0], koordinat[1],
            KANTOR_LATITUDE, KANTOR_LONGITUDE);

        System.out.printf("[LocationUtil] Evaluasi: jarak=%.1f m, " +
            "radius=%.0f m, valid=%b%n",
            jarak, RADIUS_METER, jarak <= RADIUS_METER);

        if (jarak <= RADIUS_METER) {
            return new LokasiResult(StatusLokasi.VALID, jarak, koordinat);
        }

        if (jarak > 500_000) {
            // > 500km — jelas beda kota/pulau
            return new LokasiResult(StatusLokasi.DI_LUAR_AREA, jarak, koordinat);
        }

        // Antara radius dan 500km → IP tidak akurat, minta konfirmasi
        return new LokasiResult(StatusLokasi.PERLU_KONFIRMASI, jarak, koordinat);
    }

    // ── Inner classes ─────────────────────────────────────────

    public enum StatusLokasi {
        VALID, VALID_BYPASS, PERLU_KONFIRMASI, API_GAGAL, DI_LUAR_AREA
    }

    public static class LokasiResult {
        public final StatusLokasi status;
        public final double       jarak;
        public final double[]     koordinat;

        public LokasiResult(StatusLokasi status, double jarak, double[] koordinat) {
            this.status    = status;
            this.jarak     = jarak;
            this.koordinat = koordinat;
        }

        public boolean isValid() {
            return status == StatusLokasi.VALID || status == StatusLokasi.VALID_BYPASS;
        }
    }

    // ── Parser ───────────────────────────────────────────────
    // Diperbaiki: lebih robust untuk berbagai format JSON

    private static Double parseLat(String json) {
        try {
            // Format ip-api.com: "lat":angka
            Double v = parseNumber(json, "\"lat\"");
            if (v != null) return v;

            // Format ipinfo.io: "loc":"lat,lon"
            v = parseLocLat(json);
            if (v != null) return v;

            // Format geolocation-db: "latitude":angka
            v = parseNumber(json, "\"latitude\"");
            return v;

        } catch (Exception e) {
            System.out.println("[LocationUtil] parseLat error: " + e.getMessage());
            return null;
        }
    }

    private static Double parseLon(String json) {
        try {
            // Format ip-api.com: "lon":angka
            Double v = parseNumber(json, "\"lon\"");
            if (v != null) return v;

            // Format ipinfo.io: "loc":"lat,lon"
            v = parseLocLon(json);
            if (v != null) return v;

            // Format geolocation-db: "longitude":angka
            v = parseNumber(json, "\"longitude\"");
            return v;

        } catch (Exception e) {
            System.out.println("[LocationUtil] parseLon error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Parse angka dari JSON: cari key lalu ambil nilai numeriknya.
     * Contoh: json="..., \"lat\": -6.474, ..."  key="\"lat\""
     */
    private static Double parseNumber(String json, String key) {
        int idx = json.indexOf(key);
        if (idx < 0) return null;

        // Lewati key dan ':'
        int start = json.indexOf(":", idx + key.length());
        if (start < 0) return null;
        start++; // skip ':'

        // Skip whitespace
        while (start < json.length() &&
               (json.charAt(start) == ' ' || json.charAt(start) == '\t')) {
            start++;
        }

        // Cari akhir angka: koma, }, whitespace, atau akhir string
        int end = start;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == ',' || c == '}' || c == ' ' || c == '\n' || c == '\r') break;
            end++;
        }

        String numStr = json.substring(start, end).trim();
        if (numStr.isEmpty()) return null;

        return Double.parseDouble(numStr);
    }

    /**
     * Parse lat dari format ipinfo.io: "loc":"lat,lon"
     */
    private static Double parseLocLat(String json) {
        int idx = json.indexOf("\"loc\"");
        if (idx < 0) return null;
        int start = json.indexOf("\"", idx + 6);
        if (start < 0) return null;
        start++;
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        String[] parts = json.substring(start, end).split(",");
        if (parts.length < 2) return null;
        return Double.parseDouble(parts[0].trim());
    }

    /**
     * Parse lon dari format ipinfo.io: "loc":"lat,lon"
     */
    private static Double parseLocLon(String json) {
        int idx = json.indexOf("\"loc\"");
        if (idx < 0) return null;
        int start = json.indexOf("\"", idx + 6);
        if (start < 0) return null;
        start++;
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        String[] parts = json.substring(start, end).split(",");
        if (parts.length < 2) return null;
        return Double.parseDouble(parts[1].trim());
    }

    // ── Getter ───────────────────────────────────────────────

    public static double  getRadiusMeter()     { return RADIUS_METER; }
    public static double  getKantorLatitude()  { return KANTOR_LATITUDE; }
    public static double  getKantorLongitude() { return KANTOR_LONGITUDE; }
    public static boolean isBypassAktif()      { return BYPASS_LOKASI; }

    public static String formatKoordinat(double lat, double lon) {
        return String.format("%.6f, %.6f", lat, lon);
    }
}