package com.abhedyam.util;

import java.util.List;

/** Minimal CSV writer: RFC 4180 quoting plus a guard against spreadsheet formula injection. */
public final class CsvUtil {
    private CsvUtil() {}

    public static String cell(String v) {
        if (v == null) return "";
        // A leading = + - @ (or tab/CR) makes Excel/Sheets run the cell as a formula.
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    public static String row(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(cell(cells.get(i)));
        }
        return sb.append("\r\n").toString();
    }
}
