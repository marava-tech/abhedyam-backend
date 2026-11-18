package com.abhedyam.service;

import com.abhedyam.constants.ErrorCodes;
import com.abhedyam.dto.CustomerBatchRequest;
import com.abhedyam.dto.CustomerBatchResult;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.service.interfaces.IBulkImportService;
import com.abhedyam.util.PhoneUtil;
import com.abhedyam.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * "Bring in old records → From an Excel file" (specs/customers-batch.md): five columns, Name, Phone, Village, Owes,
 * Due date, with English, Telugu or Hindi headers. Each row goes through the same code as contacts and a typed list.
 */
@Service
@RequiredArgsConstructor
public class BulkImportService implements IBulkImportService {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final int MAX_ROWS = 2000;
    private static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("d/M/uuuu");

    enum Col { NAME, PHONE, VILLAGE, OWES, DUE }

    /** Header words per column, lower case; a header cell matches when it equals one of these. */
    private static final Map<Col, List<String>> HEADERS = Map.of(
            Col.NAME, List.of("name", "customer name", "customer", "పేరు", "నామం", "नाम", "ग्राहक"),
            Col.PHONE, List.of("phone", "mobile", "phone number", "mobile number", "ఫోన్", "ఫోన్ నంబర్", "మొబైల్", "फ़ोन", "फोन", "मोबाइल"),
            Col.VILLAGE, List.of("village", "location", "place", "town", "ఊరు", "గ్రామం", "गाँव", "गांव", "जगह"),
            Col.OWES, List.of("owes", "amount", "balance", "due", "old balance", "బాకీ", "మొత్తం", "बकाया", "बाकी", "रकम"),
            Col.DUE, List.of("due date", "date", "pay by", "చెల్లించే తేదీ", "తేదీ", "तारीख", "देय तिथि"));

    /** The template's column names per app language. */
    private static final Map<String, List<String>> TEMPLATE = Map.of(
            "en", List.of("Name", "Phone", "Village", "Owes", "Due date"),
            "te", List.of("పేరు", "ఫోన్", "ఊరు", "బాకీ", "చెల్లించే తేదీ"),
            "hi", List.of("नाम", "फ़ोन", "गाँव", "बकाया", "देय तिथि"));

    private final CustomerBatchService customerBatchService;

    @Override
    public CustomerBatchResult importData(UUID ownerId, MultipartFile file, boolean dryRun, String label) {
        if (ownerId == null || !ownerId.equals(SecurityUtil.getCurrentUserId())) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "You can only add customers to your own shop");
        }
        validateFile(file);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCodes.VALIDATION_ERROR, "Could not read the uploaded file");
        }
        Parsed parsed = parse(bytes);

        CustomerBatchRequest request = new CustomerBatchRequest();
        // Same file again (a retry after a timeout) = same key, so each old balance is found, not added twice.
        request.setIdempotencyKey("xlsx:" + sha256(bytes).substring(0, 35));
        request.setLabel(label == null || label.length() <= 60 ? label : label.substring(0, 60));
        request.setRows(parsed.rows());
        if (parsed.rows().isEmpty()) {
            return CustomerBatchResult.builder().totalOwes(BigDecimal.ZERO).problems(parsed.problems())
                    .rows(parsed.problems().stream().map(p -> new CustomerBatchResult.RowResult(p.row(), null, null, p.code())).toList()).build();
        }

        CustomerBatchResult result = customerBatchService.save(ownerId, request, dryRun);
        // The batch counts rows from 1 in its list; the owner looks for the row number Excel shows.
        List<CustomerBatchResult.Problem> problems = new ArrayList<>(parsed.problems());
        for (CustomerBatchResult.Problem p : result.getProblems()) {
            problems.add(new CustomerBatchResult.Problem(parsed.sheetRows().get(p.row() - 1), p.code()));
        }
        problems.sort((a, b) -> Integer.compare(a.row(), b.row()));
        result.setProblems(problems);
        List<CustomerBatchResult.RowResult> rows = new ArrayList<>();
        for (CustomerBatchResult.Problem p : parsed.problems()) rows.add(new CustomerBatchResult.RowResult(p.row(), null, null, p.code()));
        for (CustomerBatchResult.RowResult r : result.getRows() == null ? List.<CustomerBatchResult.RowResult>of() : result.getRows()) {
            rows.add(new CustomerBatchResult.RowResult(parsed.sheetRows().get(r.row() - 1), r.name(), r.owes(), r.status()));
        }
        rows.sort((a, b) -> Integer.compare(a.row(), b.row()));
        result.setRows(rows);
        return result;
    }

    @Override
    public byte[] template(String lang) {
        List<String> names = TEMPLATE.getOrDefault(lang, TEMPLATE.get("en"));
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Old records");
            Row header = sheet.createRow(0);
            var bold = wb.createCellStyle();
            var font = wb.createFont();
            font.setBold(true);
            bold.setFont(font);
            for (int i = 0; i < names.size(); i++) {
                Cell c = header.createCell(i);
                c.setCellValue(names.get(i));
                c.setCellStyle(bold);
                sheet.setColumnWidth(i, 18 * 256);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    record Parsed(List<CustomerBatchRequest.Row> rows, List<Integer> sheetRows, List<CustomerBatchResult.Problem> problems) {
    }

    /** Reads the first sheet. Headers are the first row whose cells name a column; with none, columns go in template order. */
    static Parsed parse(byte[] bytes) {
        List<CustomerBatchRequest.Row> rows = new ArrayList<>();
        List<Integer> sheetRows = new ArrayList<>();
        List<CustomerBatchResult.Problem> problems = new ArrayList<>();
        DataFormatter fmt = new DataFormatter(Locale.ENGLISH);

        try (InputStream in = new java.io.ByteArrayInputStream(bytes); XSSFWorkbook wb = new XSSFWorkbook(in)) {
            if (wb.getNumberOfSheets() == 0) throw new BusinessException(ErrorCodes.VALIDATION_ERROR, "The file has no sheets");
            Sheet sheet = wb.getSheetAt(0);

            Map<Col, Integer> cols = null;
            int first = sheet.getFirstRowNum();
            for (int r = first; r <= Math.min(sheet.getLastRowNum(), first + 4) && cols == null; r++) {
                Map<Col, Integer> found = headerColumns(sheet.getRow(r), fmt);
                if (found.containsKey(Col.NAME)) {
                    cols = found;
                    first = r + 1;
                }
            }
            if (cols == null) cols = Map.of(Col.NAME, 0, Col.PHONE, 1, Col.VILLAGE, 2, Col.OWES, 3, Col.DUE, 4);

            for (int r = first; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (isEmpty(row, fmt)) continue;
                if (rows.size() + problems.size() >= MAX_ROWS) {
                    throw new BusinessException(ErrorCodes.VALIDATION_ERROR, "A file can have up to " + MAX_ROWS + " people");
                }
                int sheetRow = r + 1;
                CustomerBatchRequest.Row out = new CustomerBatchRequest.Row();
                out.setName(text(row, cols.get(Col.NAME), fmt));
                out.setPhone(phone(cell(row, cols.get(Col.PHONE)), fmt));
                out.setVillage(text(row, cols.get(Col.VILLAGE), fmt));
                out.setDueDate(date(cell(row, cols.get(Col.DUE)), fmt));
                try {
                    out.setOwes(amount(cell(row, cols.get(Col.OWES)), fmt));
                } catch (NumberFormatException e) {
                    problems.add(new CustomerBatchResult.Problem(sheetRow, "BAD_AMOUNT"));
                    continue;
                }
                rows.add(out);
                sheetRows.add(sheetRow);
            }
        } catch (IOException | org.apache.poi.ooxml.POIXMLException | org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException e) {
            throw new BusinessException(ErrorCodes.VALIDATION_ERROR, "Could not open this Excel file");
        }
        return new Parsed(rows, sheetRows, problems);
    }

    private static Map<Col, Integer> headerColumns(Row row, DataFormatter fmt) {
        Map<Col, Integer> found = new java.util.EnumMap<>(Col.class);
        if (row == null) return found;
        for (Cell c : row) {
            String v = fmt.formatCellValue(c).trim().toLowerCase(Locale.ROOT).replaceAll("[*:]", "").trim();
            for (var e : HEADERS.entrySet()) {
                if (e.getValue().contains(v)) found.putIfAbsent(e.getKey(), c.getColumnIndex());
            }
        }
        return found;
    }

    private static Cell cell(Row row, Integer col) {
        return row == null || col == null ? null : row.getCell(col);
    }

    private static String text(Row row, Integer col, DataFormatter fmt) {
        Cell c = cell(row, col);
        String v = c == null ? "" : fmt.formatCellValue(c).trim();
        return v.isEmpty() ? null : v;
    }

    private static boolean isEmpty(Row row, DataFormatter fmt) {
        if (row == null) return true;
        for (Cell c : row) if (!fmt.formatCellValue(c).trim().isEmpty()) return false;
        return true;
    }

    /** A number cell (9848012345.0) as digits; the batch checks it and says BAD_PHONE when it is not 10 digits. */
    private static String phone(Cell c, DataFormatter fmt) {
        if (c == null || c.getCellType() == CellType.BLANK) return null;
        if (c.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(c)) {
            return BigDecimal.valueOf(c.getNumericCellValue()).setScale(0, RoundingMode.DOWN).toPlainString();
        }
        String v = fmt.formatCellValue(c).trim();
        return v.isEmpty() ? null : v;
    }

    /** "₹4,000", "Rs. 4000", 4000 → 4000; blank → null; anything else throws NumberFormatException. */
    private static BigDecimal amount(Cell c, DataFormatter fmt) {
        if (c == null || c.getCellType() == CellType.BLANK) return null;
        if (c.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(c)) {
            return BigDecimal.valueOf(c.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP);
        }
        String v = fmt.formatCellValue(c).trim().replaceAll("(?i)rs\\.?|₹|,|\\s", "");
        return v.isEmpty() ? null : new BigDecimal(v).setScale(2, RoundingMode.HALF_UP);
    }

    /** A date cell, or text as dd/mm/yyyy or yyyy-mm-dd, → yyyy-MM-dd. Other text is passed on, so the batch says BAD_DATE. */
    private static String date(Cell c, DataFormatter fmt) {
        if (c == null || c.getCellType() == CellType.BLANK) return null;
        if (c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
            return c.getLocalDateTimeCellValue().toLocalDate().toString();
        }
        String v = fmt.formatCellValue(c).trim().replace('-', '/').replace('.', '/');
        if (v.isEmpty()) return null;
        try {
            return LocalDate.parse(v, DMY).toString();
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(v.replace('/', '-')).toString();
            } catch (DateTimeParseException e2) {
                return v;
            }
        }
    }

    private static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCodes.VALIDATION_ERROR, "Please upload a non-empty .xlsx file");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException(ErrorCodes.VALIDATION_ERROR, "File size must be 10MB or less");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx") && !XLSX_CONTENT_TYPE.equalsIgnoreCase(file.getContentType())) {
            throw new BusinessException(ErrorCodes.VALIDATION_ERROR, "Only .xlsx files are supported");
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** "098480 12345", "+91 98480…", "919848012345" → +919848012345; blank → null; anything else throws. */
    static String cleanPhoneText(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String sanitized = raw.replaceAll("[^0-9+]", "");
        if (sanitized.isEmpty()) {
            return null;
        }
        if (sanitized.startsWith("0091") && sanitized.length() > 4) {
            sanitized = "+" + sanitized.substring(2);
        }
        if (sanitized.startsWith("91") && sanitized.length() == 12) {
            sanitized = "+" + sanitized;
        }
        if (!sanitized.startsWith("+")) {
            sanitized = sanitized.replaceFirst("^0+(?!$)", "");
        }
        if (!PhoneUtil.isValidPhone(sanitized)) {
            String digitsOnly = sanitized.replaceAll("[^0-9]", "");
            if (digitsOnly.length() == 10) {
                sanitized = "+91" + digitsOnly;
            } else if (digitsOnly.length() == 12 && digitsOnly.startsWith("91")) {
                sanitized = "+" + digitsOnly;
            } else {
                throw new BusinessException(ErrorCodes.VALIDATION_ERROR, "Phone must be a 10-digit number (got \"" + raw + "\")");
            }
        }
        return PhoneUtil.normalizePhone(sanitized);
    }
}
