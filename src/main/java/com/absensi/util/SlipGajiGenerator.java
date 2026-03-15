package com.absensi.util;

import com.absensi.model.Gaji;
import com.absensi.model.Karyawan;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Generator slip gaji dalam format PDF menggunakan iText library.
 * Menghasilkan slip gaji yang profesional dan dapat dicetak.
 */
public class SlipGajiGenerator {

    // Warna tema slip gaji
    private static final BaseColor COLOR_PRIMARY  = new BaseColor(0x1B, 0x2A, 0x4A);
    private static final BaseColor COLOR_ACCENT   = new BaseColor(0x00, 0xC9, 0xA7);
    private static final BaseColor COLOR_LIGHT    = new BaseColor(0xF0, 0xF4, 0xF8);
    private static final BaseColor COLOR_WHITE    = BaseColor.WHITE;

    /**
     * Generate slip gaji PDF untuk seorang karyawan.
     *
     * @param karyawan Data karyawan
     * @param gaji     Data gaji periode tersebut
     * @param filePath Path file output PDF
     * @return true jika berhasil
     */
    public static boolean generate(Karyawan karyawan, Gaji gaji, String filePath) {
        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        try {
            PdfWriter.getInstance(document, new FileOutputStream(filePath));
            document.open();

            addHeader(document, karyawan, gaji);
            addEmployeeInfo(document, karyawan, gaji);
            addSalaryTable(document, gaji);
            addFooter(document, gaji);

            document.close();
            return true;
        } catch (DocumentException | IOException e) {
            System.err.println("[SlipGaji] Gagal generate PDF: " + e.getMessage());
            return false;
        }
    }

    // ===================== HEADER PERUSAHAAN =====================

    private static void addHeader(Document doc, Karyawan karyawan, Gaji gaji)
            throws DocumentException {
        // Background header
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{2f, 1f});

        // Nama perusahaan
        Font companyFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, COLOR_WHITE);
        Font subFont     = FontFactory.getFont(FontFactory.HELVETICA, 10, COLOR_WHITE);

        PdfPCell cellLeft = new PdfPCell();
        cellLeft.setBackgroundColor(COLOR_PRIMARY);
        cellLeft.setBorder(Rectangle.NO_BORDER);
        cellLeft.setPadding(20);
        cellLeft.addElement(new Paragraph("PT. NAMA PERUSAHAAN", companyFont));
        cellLeft.addElement(new Paragraph("Jl. Contoh No. 123, Jakarta | Telp: (021) 1234-5678", subFont));
        headerTable.addCell(cellLeft);

        // Label slip gaji
        Font slipFont    = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, COLOR_WHITE);
        Font periodeFont = FontFactory.getFont(FontFactory.HELVETICA, 11, COLOR_ACCENT);

        PdfPCell cellRight = new PdfPCell();
        cellRight.setBackgroundColor(COLOR_PRIMARY);
        cellRight.setBorder(Rectangle.NO_BORDER);
        cellRight.setPadding(20);
        cellRight.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph pSlip = new Paragraph("SLIP GAJI\n", slipFont);
        pSlip.setAlignment(Element.ALIGN_RIGHT);
        Paragraph pPeriode = new Paragraph(gaji.getNamaBulan() + " " + gaji.getTahun(), periodeFont);
        pPeriode.setAlignment(Element.ALIGN_RIGHT);
        cellRight.addElement(pSlip);
        cellRight.addElement(pPeriode);
        headerTable.addCell(cellRight);

        doc.add(headerTable);
        doc.add(Chunk.NEWLINE);
    }

    // ===================== INFO KARYAWAN =====================

    private static void addEmployeeInfo(Document doc, Karyawan karyawan, Gaji gaji)
            throws DocumentException {
        Font titleFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, COLOR_PRIMARY);
        Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10, BaseColor.DARK_GRAY);

        PdfPTable infoTable = new PdfPTable(4);
        infoTable.setWidthPercentage(100);
        infoTable.setWidths(new float[]{1.2f, 2f, 1.2f, 2f});
        infoTable.setSpacingBefore(10f);

        // Row 1
        addInfoCell(infoTable, "Nama Karyawan", titleFont);
        addInfoCell(infoTable, karyawan.getNama(), normalFont);
        addInfoCell(infoTable, "ID Karyawan", titleFont);
        addInfoCell(infoTable, karyawan.getIdKaryawan(), normalFont);

        // Row 2
        addInfoCell(infoTable, "Bagian", titleFont);
        addInfoCell(infoTable, karyawan.getBagian(), normalFont);
        addInfoCell(infoTable, "Periode", titleFont);
        addInfoCell(infoTable, gaji.getPeriodeLabel(), normalFont);

        // Row 3
        addInfoCell(infoTable, "Tanggal Cetak", titleFont);
        addInfoCell(infoTable, LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy",
            new java.util.Locale("id", "ID"))), normalFont);
        addInfoCell(infoTable, "Status", titleFont);
        addInfoCell(infoTable, gaji.getStatusBayar().name(), normalFont);

        // Style seluruh tabel
        for (int i = 0; i < infoTable.size(); i++) {
            // Styling diterapkan per cell saat addInfoCell
        }

        doc.add(infoTable);
        doc.add(Chunk.NEWLINE);
    }

    private static void addInfoCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(new BaseColor(0xDE, 0xE2, 0xE6));
        cell.setPadding(8);
        cell.setBackgroundColor(COLOR_LIGHT);
        table.addCell(cell);
    }

    // ===================== TABEL RINCIAN GAJI =====================

    private static void addSalaryTable(Document doc, Gaji gaji)
            throws DocumentException {
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, COLOR_WHITE);
        Font labelFont  = FontFactory.getFont(FontFactory.HELVETICA, 10, BaseColor.DARK_GRAY);
        Font valueFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, COLOR_PRIMARY);
        Font totalFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, COLOR_WHITE);

        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{0.5f, 2.5f, 1.5f});

        // Header tabel
        addTableHeaderCell(table, "No.", headerFont);
        addTableHeaderCell(table, "Komponen Gaji", headerFont);
        addTableHeaderCell(table, "Jumlah (Rp)", headerFont);

        // Baris data
        int no = 1;
        addSalaryRow(table, no++, "Gaji Pokok", gaji.getGajiPokok(), labelFont, valueFont, false);
        addSalaryRow(table, no++, "Uang Lembur", gaji.getTotalLembur(), labelFont, valueFont, false);
        addSalaryRow(table, no++, "Bonus" + (gaji.getKeteranganBonus() != null && !gaji.getKeteranganBonus().isEmpty()
            ? " (" + gaji.getKeteranganBonus() + ")" : ""), gaji.getBonus(), labelFont, valueFont, false);
        addSalaryRow(table, no,   "Potongan", gaji.getPotongan(), labelFont,
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BaseColor.RED), false);

        // Baris Total
        PdfPCell cellNoTotal = new PdfPCell(new Phrase("", totalFont));
        cellNoTotal.setBackgroundColor(COLOR_PRIMARY);
        cellNoTotal.setBorder(Rectangle.NO_BORDER);
        cellNoTotal.setPadding(10);
        table.addCell(cellNoTotal);

        PdfPCell cellLabelTotal = new PdfPCell(new Phrase("TOTAL GAJI BERSIH", totalFont));
        cellLabelTotal.setBackgroundColor(COLOR_PRIMARY);
        cellLabelTotal.setBorder(Rectangle.NO_BORDER);
        cellLabelTotal.setPadding(10);
        table.addCell(cellLabelTotal);

        PdfPCell cellValueTotal = new PdfPCell(new Phrase(
            CurrencyUtil.formatRupiahBulat(gaji.getTotalGaji()), totalFont));
        cellValueTotal.setBackgroundColor(COLOR_PRIMARY);
        cellValueTotal.setBorder(Rectangle.NO_BORDER);
        cellValueTotal.setPadding(10);
        cellValueTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(cellValueTotal);

        doc.add(table);
    }

    private static void addTableHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(COLOR_PRIMARY);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(10);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private static void addSalaryRow(PdfPTable table, int no, String label,
                                      java.math.BigDecimal value, Font labelFont, Font valueFont,
                                      boolean isAlt) {
        BaseColor rowBg = isAlt ? COLOR_LIGHT : COLOR_WHITE;

        PdfPCell cellNo    = new PdfPCell(new Phrase(String.valueOf(no), labelFont));
        PdfPCell cellLabel = new PdfPCell(new Phrase(label, labelFont));
        PdfPCell cellValue = new PdfPCell(new Phrase(
            value != null ? CurrencyUtil.formatRupiahBulat(value) : "Rp0", valueFont));

        for (PdfPCell cell : new PdfPCell[]{cellNo, cellLabel, cellValue}) {
            cell.setBackgroundColor(rowBg);
            cell.setBorderColor(new BaseColor(0xDE, 0xE2, 0xE6));
            cell.setPadding(9);
        }
        cellValue.setHorizontalAlignment(Element.ALIGN_RIGHT);

        table.addCell(cellNo);
        table.addCell(cellLabel);
        table.addCell(cellValue);
    }

    // ===================== FOOTER & TANDA TANGAN =====================

    private static void addFooter(Document doc, Gaji gaji) throws DocumentException {
        Font noteFont = FontFactory.getFont(FontFactory.HELVETICA, 9, BaseColor.GRAY);
        Font signFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, COLOR_PRIMARY);

        doc.add(Chunk.NEWLINE);
        doc.add(new Paragraph(
            "* Slip gaji ini diterbitkan secara otomatis oleh Sistem Absensi Karyawan", noteFont));
        doc.add(new Paragraph(
            "* Jika ada pertanyaan mengenai komponen gaji, silakan hubungi HRD", noteFont));
        doc.add(Chunk.NEWLINE);

        // Area tanda tangan
        PdfPTable signTable = new PdfPTable(3);
        signTable.setWidthPercentage(100);
        signTable.setSpacingBefore(20f);

        String[] roles = {"Karyawan", "HRD", "Direktur"};
        for (String role : roles) {
            PdfPCell cell = new PdfPCell();
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setPadding(10);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);

            Paragraph p = new Paragraph(role + "\n\n\n\n\n__________________", signFont);
            p.setAlignment(Element.ALIGN_CENTER);
            cell.addElement(p);
            signTable.addCell(cell);
        }
        doc.add(signTable);

        // Nomor slip & timestamp
        doc.add(Chunk.NEWLINE);
        Font idFont = FontFactory.getFont(FontFactory.HELVETICA, 8, BaseColor.LIGHT_GRAY);
        Paragraph pId = new Paragraph(
            "Digenerate: " + LocalDate.now() + " | Sistem Absensi Karyawan v1.0", idFont);
        pId.setAlignment(Element.ALIGN_CENTER);
        doc.add(pId);
    }
}