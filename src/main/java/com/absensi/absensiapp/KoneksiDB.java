package com.absensi.absensiapp;

import java.sql.Connection;
import java.sql.DriverManager;

public class KoneksiDB {

    public static Connection connect() {

        try {

            String url = "jdbc:mysql://localhost:3306/absensi_db";
            String user = "root";
            String pass = "";

            Connection conn = DriverManager.getConnection(url, user, pass);

            System.out.println("Database berhasil terhubung");

            return conn;

        } catch (Exception e) {

            System.out.println("Koneksi gagal: " + e.getMessage());
            return null;

        }

    }

}