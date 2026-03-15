package com.absensi.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConfig {

    private static final String HOST     = "localhost";
    private static final String PORT     = "3306";
    private static final String DATABASE = "absensi_db"; 
    private static final String USERNAME = "root";
    private static final String PASSWORD = "";

    private static final String URL = String.format(
        "jdbc:mysql://%s:%s/%s?useSSL=false&serverTimezone=Asia/Jakarta&characterEncoding=UTF-8&allowPublicKeyRetrieval=true",
        HOST, PORT, DATABASE
    );

    private static Connection connection = null;

    public static Connection getConnection() throws SQLException {
        try {
            if (connection == null || connection.isClosed()) {
                Class.forName("com.mysql.cj.jdbc.Driver");
                connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
                System.out.println("[DB] Koneksi database berhasil.");
            }
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC Driver tidak ditemukan: " + e.getMessage());
        }
        return connection;
    }

    public static void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
                connection = null;
                System.out.println("[DB] Koneksi database ditutup.");
            } catch (SQLException e) {
                System.err.println("[DB] Gagal menutup koneksi: " + e.getMessage());
            }
        }
    }

    public static boolean testConnection() {
        try {
            Connection conn = getConnection();
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            System.err.println("[DB] Koneksi gagal: " + e.getMessage());
            return false;
        }
    }
}