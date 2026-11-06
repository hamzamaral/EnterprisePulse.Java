package dev.enterprisepulse.resource;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.ColumnText;
import dev.enterprisepulse.meta.Meta.FieldMeta;
import dev.enterprisepulse.meta.Meta.ResourceMeta;
import dev.enterprisepulse.meta.MetadataStore;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.librepdf.openpdf.fonts.Liberation;
import org.springframework.stereotype.Service;

@Service
public class ExportService {
    public record ExportFile(byte[] content, String contentType, String fileName) {}

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final Set<String> SKIPPED = Set.of("id", "companyId", "deletedAt", "updatedAt");

    private final MetadataStore meta;

    public ExportService(MetadataStore meta) {
        this.meta = meta;
    }

    public String display(FieldMeta f, Object value, Map<String, Map<String, String>> labels, String locale) {
        if (value == null) return "";
        if (f.ref != null && labels.containsKey(f.name)) {
            String label = labels.get(f.name).get(value.toString());
            if (label != null) return label;
        }
        if (value instanceof LocalDate d) return DATE.format(d);
        if (value instanceof Instant i) return DATE_TIME.format(i.atZone(ZONE));
        if (value instanceof BigDecimal m) return money(m, f.scale != null && f.scale > 2 ? f.scale : 2);
        if (value instanceof Boolean b) return b ? ("en".equals(locale) ? "Yes" : "Evet") : ("en".equals(locale) ? "No" : "Hayır");
        if (value instanceof Enum<?> e && f.enumName != null) {
            var byLocale = meta.document().enums.get(f.enumName);
            if (byLocale != null) {
                var map = byLocale.get("en".equals(locale) ? "en" : "tr");
                if (map != null && map.containsKey(e.name())) return map.get(e.name());
            }
            return e.name();
        }
        return value.toString();
    }

    public static String money(BigDecimal value, int scale) {
        DecimalFormat format = new DecimalFormat("#,##0." + "0".repeat(scale), DecimalFormatSymbols.getInstance(new Locale("tr", "TR")));
        return format.format(value);
    }

    public ExportFile export(ResourceMeta r, List<Object> rows, Map<String, Map<String, String>> labels, String format, String locale) {
        List<FieldMeta> fields = r.fields.stream().filter(f -> !f.hidden && !"json".equals(f.kind) && !SKIPPED.contains(f.name)).toList();
        List<String> headers = fields.stream().map(f -> f.label.of(locale)).toList();
        List<List<String>> data = rows.stream().map(row -> fields.stream().map(f -> display(f, ResourceService.read(f, row), labels, locale)).toList()).toList();
        return table(r.label.of(locale), r.resource, headers, data, format);
    }

    public ExportFile table(String title, String fileBase, List<String> headers, List<List<String>> rows, String format) {
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(LocalDateTime.now(ZONE));
        if ("pdf".equals(format)) return new ExportFile(pdf(title, headers, rows), "application/pdf", fileBase + "-" + stamp + ".pdf");
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(title.length() > 31 ? title.substring(0, 31) : title);
            XSSFCellStyle head = wb.createCellStyle();
            XSSFFont bold = wb.createFont();
            bold.setBold(true);
            head.setFont(bold);
            head.setFillForegroundColor(new XSSFColor(new byte[] {(byte) 0xEE, (byte) 0xF2, (byte) 0xFF}, null));
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Row header = sheet.createRow(0);
            for (int c = 0; c < headers.size(); c++) {
                var cell = header.createCell(c);
                cell.setCellValue(headers.get(c));
                cell.setCellStyle((CellStyle) head);
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows.get(r).size(); c++) row.createCell(c).setCellValue(rows.get(r).get(c));
            }
            sheet.createFreezePane(0, 1);
            for (int c = 0; c < headers.size(); c++) {
                int width = headers.get(c).length();
                for (int r = 0; r < Math.min(rows.size(), 200); r++) width = Math.max(width, rows.get(r).get(c).length());
                sheet.setColumnWidth(c, Math.min(60, width + 2) * 256);
            }
            wb.write(out);
            return new ExportFile(out.toByteArray(), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", fileBase + "-" + stamp + ".xlsx");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] pdf(String title, List<String> headers, List<List<String>> rows) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Rectangle size = headers.size() > 6 ? PageSize.A4.rotate() : PageSize.A4;
            Document doc = new Document(size, 24, 24, 30, 30);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            int fontSize = headers.size() > 10 ? 6 : 8;
            Font regular = Liberation.SANS.create(fontSize);
            Font boldFont = Liberation.SANS_BOLD.create(fontSize);
            Font titleFont = Liberation.SANS_BOLD.create(13);
            titleFont.setColor(new Color(0x31, 0x2e, 0x81));
            Font footerFont = Liberation.SANS.create(7);
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter w, Document d) {
                    ColumnText.showTextAligned(w.getDirectContent(), Element.ALIGN_RIGHT,
                            new Phrase("Sayfa " + w.getPageNumber(), footerFont), d.right(), d.bottom() - 15, 0);
                }
            });
            doc.open();
            Paragraph heading = new Paragraph(title + "    ", titleFont);
            heading.add(new Phrase(DATE_TIME.format(LocalDateTime.now(ZONE)), footerFont));
            heading.setSpacingAfter(8);
            doc.add(heading);
            PdfPTable table = new PdfPTable(Math.max(1, headers.size()));
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, boldFont));
                cell.setBackgroundColor(new Color(0xEE, 0xF2, 0xFF));
                cell.setPadding(3);
                cell.setBorderColor(new Color(0xE5, 0xE7, 0xEB));
                table.addCell(cell);
            }
            for (List<String> row : rows) {
                for (String value : row) {
                    PdfPCell cell = new PdfPCell(new Phrase(value, regular));
                    cell.setPadding(3);
                    cell.setBorder(Rectangle.BOTTOM);
                    cell.setBorderColor(new Color(0xE5, 0xE7, 0xEB));
                    table.addCell(cell);
                }
            }
            if (headers.isEmpty()) table.addCell("");
            doc.add(table);
            doc.close();
            return out.toByteArray();
        } catch (IOException | com.lowagie.text.DocumentException e) {
            throw new IllegalStateException(e);
        }
    }
}
