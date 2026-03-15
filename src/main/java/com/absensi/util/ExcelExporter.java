package com.absensi.util;

import com.absensi.model.Absensi;
import com.absensi.model.Gaji;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Utility untuk export laporan ke format Excel (.xlsx).
 * Menggunakan Apache POI library.
 */
public class ExcelExporter {

    /**
     * Export laporan absensi ke file Excel.
     *
     * @param absensiList Daftar data absensi
     * @param bulan       Bulan laporan
     * @param tahun       Tahun laporan
     * @param filePath    Path file output
     */
    public static boolean exportLaporanAbsensi(List<Absensi> absensiList,
                                                int bulan, int tahun,
                                                String filePath) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Laporan Absensi");

            // Style
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle   = createDataStyle(workbook);
            CellStyle altStyle    = createAltRowStyle(workbook);

            // Title
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("LAPORAN ABSENSI KARYAWAN - " + getNamaBulan(bulan) + " " + tahun);
            CellStyle titleStyle = workbook.createCellStyle();
            XSSFFont titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));

            // Header baris
            String[] headers = {"No", "Tanggal", "Nama Karyawan", "Jam Masuk",
                                 "Jam Keluar", "Status", "Durasi Telat", "Lembur"};
            Row headerRow = sheet.createRow(2);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            int rowNum = 3;
            int no = 1;
            for (Absensi a : absensiList) {
                Row row = sheet.createRow(rowNum++);
                CellStyle style = (no % 2 == 0) ? altStyle : dataStyle;

                createCell(row, 0, String.valueOf(no++), style);
                createCell(row, 1, a.getTanggal() != null ? a.getTanggal().toString() : "-", style);
                createCell(row, 2, a.getNamaKaryawan() != null ? a.getNamaKaryawan() : "-", style);
                createCell(row, 3, a.getJamMasuk() != null ? a.getJamMasuk().toString() : "-", style);
                createCell(row, 4, a.getJamKeluar() != null ? a.getJamKeluar().toString() : "-", style);
                createCell(row, 5, a.isStatusTelat() ? "Terlambat" : "Tepat Waktu", style);
                createCell(row, 6, a.getDurasiTelatFormatted(), style);
                createCell(row, 7, a.getDurasiLemburFormatted(), style);
            }

            // Auto-size kolom
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            // Simpan file
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
            return true;
        } catch (IOException e) {
            System.err.println("[ExcelExporter] Gagal export: " + e.getMessage());
            return false;
        }
    }

    /**
     * Export laporan gaji ke file Excel.
     */
    public static boolean exportLaporanGaji(List<Gaji> gajiList,
                                             int bulan, int tahun,
                                             String filePath) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Laporan Gaji");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle   = createDataStyle(workbook);
            CellStyle altStyle    = createAltRowStyle(workbook);

            // Title
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("LAPORAN GAJI KARYAWAN - " + getNamaBulan(bulan) + " " + tahun);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));

            // Header
            String[] headers = {"No", "ID Karyawan", "Nama", "Bagian",
                                 "Gaji Pokok", "Lembur", "Bonus", "Total Gaji", "Status"};
            Row headerRow = sheet.createRow(2);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data
            int rowNum = 3, no = 1;
            for (Gaji g : gajiList) {
                Row row = sheet.createRow(rowNum++);
                CellStyle style = (no % 2 == 0) ? altStyle : dataStyle;

                createCell(row, 0, String.valueOf(no++), style);
                createCell(row, 1, g.getIdKaryawan(), style);
                createCell(row, 2, g.getNamaKaryawan(), style);
                createCell(row, 3, g.getBagian(), style);
                createCell(row, 4, CurrencyUtil.formatRupiahBulat(g.getGajiPokok()), style);
                createCell(row, 5, CurrencyUtil.formatRupiahBulat(g.getTotalLembur()), style);
                createCell(row, 6, CurrencyUtil.formatRupiahBulat(g.getBonus()), style);
                createCell(row, 7, CurrencyUtil.formatRupiahBulat(g.getTotalGaji()), style);
                createCell(row, 8, g.getStatusBayar().name(), style);
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
            return true;
        } catch (IOException e) {
            System.err.println("[ExcelExporter] Gagal export gaji: " + e.getMessage());
            return false;
        }
    }

    // ===================== HELPER STYLE METHODS =====================

    private static CellStyle createHeaderStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(new byte[]{0x1B, 0x2A, 0x4A}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private static CellStyle createDataStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static CellStyle createAltRowStyle(XSSFWorkbook wb) {
        CellStyle style = createDataStyle(wb);
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0xF0, (byte)0xF4, (byte)0xF8}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static void createCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "-");
        cell.setCellStyle(style);
    }

    private static String getNamaBulan(int bulan) {
        String[] nama = {"", "Januari", "Februari", "Maret", "April", "Mei", "Juni",
                          "Juli", "Agustus", "September", "Oktober", "November", "Desember"};
        return (bulan >= 1 && bulan <= 12) ? nama[bulan] : "Unknown";
    }
}
