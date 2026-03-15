package com.absensi.util;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Embedded HTTP server untuk menerima data GPS dari HP karyawan.
 *
 * Cara kerja:
 *  1. Server start saat aplikasi dibuka
 *  2. Karyawan buka gps.html di HP (HP & PC harus 1 WiFi)
 *  3. Tekan "Ambil Lokasi" → GPS dari HP browser
 *  4. Tekan "Kirim" → POST JSON ke server ini
 *  5. AbsensiMasukPanel ambil koordinat via getLatitude()/getLongitude()
 */
public class LocationServer {

    private static final int    PORT            = 8080;
    private static final long   GPS_TIMEOUT_MS  = 5 * 60 * 1000; // 5 menit

    private static HttpServer   server;
    private static boolean      running = false;

    // Data GPS terbaru — thread-safe dengan AtomicReference
    private static volatile double  latitude        = 0;
    private static volatile double  longitude       = 0;
    private static volatile double  accuracy        = 999;
    private static volatile long    lastUpdateTime  = 0;
    private static volatile boolean dataReceived    = false;

    // ── Start / Stop ──────────────────────────────────────────

    /**
     * Start server. Panggil sekali saat aplikasi launch.
     * Non-blocking — server jalan di thread terpisah.
     */
    public static void start() {
        if (running) return;

        try {
            server = HttpServer.create(
                new InetSocketAddress("0.0.0.0", PORT), 0);

            // Endpoint GPS
            server.createContext("/location", new LocationHandler());

            // Endpoint health check
            server.createContext("/ping", exchange -> {
                String resp = "{\"status\":\"ok\",\"app\":\"AbsensiSystem\"}";
                sendResponse(exchange, 200, resp);
            });

            // Serve gps.html langsung dari server
            server.createContext("/gps", new GpsPageHandler());

            server.setExecutor(Executors.newFixedThreadPool(4));
            server.start();
            running = true;

            System.out.println("[LocationServer] Server GPS berjalan di port " + PORT);
            System.out.println("[LocationServer] Buka dari HP: http://" +
                getLocalIpAddress() + ":" + PORT + "/gps");

        } catch (Exception e) {
            System.err.println("[LocationServer] Gagal start server: " + e.getMessage());
        }
    }

    public static void stop() {
        if (server != null) {
            server.stop(0);
            running = false;
            System.out.println("[LocationServer] Server berhenti.");
        }
    }

    // ── Handler: /location ───────────────────────────────────

    private static class LocationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {

            // CORS headers agar browser HP bisa akses
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods",
                "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers",
                "Content-Type");

            // Handle preflight OPTIONS
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405,
                    "{\"error\":\"Method not allowed\"}");
                return;
            }

            // Validasi hanya dari jaringan lokal
            String clientIp = exchange.getRemoteAddress().getAddress()
                .getHostAddress();
            if (!isLocalNetwork(clientIp)) {
                System.out.println("[LocationServer] Ditolak dari IP: " + clientIp);
                sendResponse(exchange, 403,
                    "{\"error\":\"Hanya jaringan lokal\"}");
                return;
            }

            // Baca body JSON
            String body;
            try (InputStream is = exchange.getRequestBody()) {
                body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            // Parse JSON sederhana (tanpa library eksternal)
            Double lat = parseJsonDouble(body, "latitude");
            Double lon = parseJsonDouble(body, "longitude");
            Double acc = parseJsonDouble(body, "accuracy");

            if (lat == null || lon == null) {
                sendResponse(exchange, 400,
                    "{\"error\":\"Format JSON tidak valid. " +
                    "Butuh: latitude, longitude\"}");
                return;
            }

            // Simpan data GPS
            latitude       = lat;
            longitude      = lon;
            accuracy       = acc != null ? acc : 999;
            lastUpdateTime = System.currentTimeMillis();
            dataReceived   = true;

            // Hitung jarak ke kantor untuk response
            double jarak = LocationUtil.hitungJarak(
                lat, lon,
                LocationUtil.getKantorLatitude(),
                LocationUtil.getKantorLongitude());

            boolean diKantor = jarak <= LocationUtil.getRadiusMeter();

            System.out.printf("[LocationServer] GPS diterima dari %s: " +
                "lat=%.6f, lon=%.6f, akurasi=%.0fm, " +
                "jarak_kantor=%.0fm, status=%s%n",
                clientIp, lat, lon, accuracy, jarak,
                diKantor ? "DALAM AREA" : "LUAR AREA");

            // Response ke HP
            String response = String.format(
                "{\"success\":true," +
                "\"jarak_meter\":%.0f," +
                "\"radius_kantor\":%.0f," +
                "\"status\":\"%s\"," +
                "\"message\":\"%s\"}",
                jarak,
                LocationUtil.getRadiusMeter(),
                diKantor ? "Dalam area kantor" : "Di luar area kantor",
                diKantor
                    ? "Lokasi valid. Silakan lakukan absensi."
                    : String.format("Jarak %.0f m dari kantor. " +
                      "Radius: %.0f m.", jarak,
                      LocationUtil.getRadiusMeter())
            );

            sendResponse(exchange, 200, response);
        }
    }

    // ── Handler: /gps (serve gps.html) ──────────────────────

    private static class GpsPageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Content-Type",
                "text/html; charset=UTF-8");

            // Coba load dari file system dulu
            File htmlFile = new File("gps.html");
            byte[] html;

            if (htmlFile.exists()) {
                html = java.nio.file.Files.readAllBytes(htmlFile.toPath());
            } else {
                // Fallback: halaman sederhana langsung dari kode
                String page = buildFallbackPage();
                html = page.getBytes(StandardCharsets.UTF_8);
            }

            exchange.sendResponseHeaders(200, html.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(html);
            }
        }
    }

    // ── API publik untuk AbsensiService ─────────────────────

    /** Ambil latitude GPS terbaru. Return 0 jika belum ada data. */
    public static double getLatitude()  { return latitude; }

    /** Ambil longitude GPS terbaru. Return 0 jika belum ada data. */
    public static double getLongitude() { return longitude; }

    /** Ambil akurasi GPS dalam meter. */
    public static double getAccuracy()  { return accuracy; }

    /** Cek apakah sudah ada data GPS yang diterima. */
    public static boolean hasData()     { return dataReceived; }

    /** Cek apakah data GPS masih fresh (belum lebih dari 5 menit). */
    public static boolean isFresh() {
        if (!dataReceived) return false;
        return (System.currentTimeMillis() - lastUpdateTime) < GPS_TIMEOUT_MS;
    }

    /** Menit sejak data GPS terakhir diterima. */
    public static long getMenitSejak() {
        if (!dataReceived) return -1;
        return (System.currentTimeMillis() - lastUpdateTime) / 60000;
    }

    /** Reset data GPS (setelah absensi selesai). */
    public static void reset() {
        dataReceived   = false;
        latitude       = 0;
        longitude      = 0;
        accuracy       = 999;
        lastUpdateTime = 0;
    }

    public static boolean isRunning()   { return running; }
    public static int     getPort()     { return PORT; }

    /** Ambil URL yang harus dibuka di HP karyawan. */
    public static String getGpsUrl() {
        return "http://" + getLocalIpAddress() + ":" + PORT + "/gps";
    }

    // ── Helper ───────────────────────────────────────────────

    private static void sendResponse(HttpExchange ex, int code, String body)
            throws IOException {
        ex.getResponseHeaders().add("Content-Type",
            "application/json; charset=UTF-8");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** Parse double dari JSON string tanpa library. */
    private static Double parseJsonDouble(String json, String key) {
        try {
            String search = "\"" + key + "\"";
            int idx = json.indexOf(search);
            if (idx < 0) return null;

            int colon = json.indexOf(":", idx + search.length());
            if (colon < 0) return null;

            int start = colon + 1;
            while (start < json.length() &&
                   (json.charAt(start) == ' ' || json.charAt(start) == '\t'))
                start++;

            int end = start;
            while (end < json.length()) {
                char c = json.charAt(end);
                if (c == ',' || c == '}' || c == ' ' || c == '\n') break;
                end++;
            }

            return Double.parseDouble(json.substring(start, end).trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** Cek apakah IP dari jaringan lokal. */
    private static boolean isLocalNetwork(String ip) {
        return ip.startsWith("127.")
            || ip.startsWith("192.168.")
            || ip.startsWith("10.")
            || ip.equals("0:0:0:0:0:0:0:1")   // IPv6 localhost
            || ip.startsWith("172.16.")
            || ip.startsWith("172.17.")
            || ip.startsWith("172.18.")
            || ip.startsWith("172.19.")
            || ip.startsWith("172.2")
            || ip.startsWith("172.30.")
            || ip.startsWith("172.31.");
    }

    /** Ambil IP address lokal (bukan 127.0.0.1). */
    public static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces =
                NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;

                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    String ip = addr.getHostAddress();
                    // Ambil IPv4 yang bukan loopback
                    if (!addr.isLoopbackAddress()
                            && !ip.contains(":")
                            && ip.startsWith("192.168.")) {
                        return ip;
                    }
                }
            }
            // Fallback: cari semua IPv4 lokal
            interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    String ip = addr.getHostAddress();
                    if (!addr.isLoopbackAddress() && !ip.contains(":")) {
                        return ip;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[LocationServer] Gagal ambil IP lokal: " +
                e.getMessage());
        }
        return "localhost";
    }

    private static String buildFallbackPage() {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'>" +
            "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
            "<title>GPS Absensi</title></head><body style='font-family:sans-serif;" +
            "background:#0f172a;color:#e2e8f0;padding:20px;text-align:center'>" +
            "<h2>File gps.html tidak ditemukan</h2>" +
            "<p>Letakkan file gps.html di folder root project.</p>" +
            "</body></html>";
    }
}