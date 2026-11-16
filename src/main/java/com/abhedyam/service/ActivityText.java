package com.abhedyam.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Short, plain wording for recent-activity cards. Pure, so it is unit-tested. */
final class ActivityText {
    private ActivityText() {}

    record Line(String name, BigDecimal qty) {}

    private static final int MAX_DETAIL = 255;
    private static final ZoneId OWNER_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DUE_FORMAT =
            DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH).withZone(OWNER_ZONE);

    /** 16000.00 -> ₹16,000; 444180 -> ₹4,44,180; 1234.5 -> ₹1,234.50 (Indian grouping). */
    static String money(BigDecimal v) {
        if (v == null) return "₹0";
        boolean whole = v.stripTrailingZeros().scale() <= 0;
        String plain = v.abs().setScale(whole ? 0 : 2, RoundingMode.HALF_UP).toPlainString();
        int dot = plain.indexOf('.');
        String intPart = dot >= 0 ? plain.substring(0, dot) : plain;
        String frac = dot >= 0 ? plain.substring(dot) : "";
        return (v.signum() < 0 ? "-₹" : "₹") + groupIndian(intPart) + frac;
    }

    private static String groupIndian(String digits) {
        if (digits.length() <= 3) return digits;
        List<String> parts = new java.util.ArrayList<>();
        parts.add(digits.substring(digits.length() - 3));
        String rest = digits.substring(0, digits.length() - 3);
        while (rest.length() > 2) {
            parts.add(0, rest.substring(rest.length() - 2));
            rest = rest.substring(0, rest.length() - 2);
        }
        if (!rest.isEmpty()) parts.add(0, rest);
        return String.join(",", parts);
    }

    /** "Rice ×2, Oil, Sugar +2 more" (first 3 items). */
    static String itemsSummary(List<Line> lines) {
        if (lines == null || lines.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(3, lines.size());
        for (int i = 0; i < shown; i++) {
            Line l = lines.get(i);
            if (i > 0) sb.append(", ");
            sb.append(l.name());
            BigDecimal q = l.qty();
            if (q != null && q.compareTo(BigDecimal.ONE) != 0) {
                sb.append(" ×").append(q.stripTrailingZeros().toPlainString());
            }
        }
        if (lines.size() > shown) sb.append(" +").append(lines.size() - shown).append(" more");
        return sb.toString();
    }

    static String saleDetail(String items, BigDecimal paid, BigDecimal total) {
        String tail;
        if (paid == null || paid.signum() <= 0) {
            tail = "On udhar";
        } else if (paid.compareTo(total) >= 0) {
            tail = "Paid in full";
        } else {
            tail = "Paid " + money(paid) + ", due " + money(total.subtract(paid));
        }
        return clip(items == null || items.isBlank() ? tail : items + " · " + tail);
    }

    static String paymentDetail(String medium, String product) {
        String label = mediumLabel(medium);
        boolean hasProduct = product != null && !product.isBlank();
        if (label == null && !hasProduct) return null;
        if (label == null) return clip("for " + product);
        return clip(hasProduct ? label + " · for " + product : label);
    }

    static String reminderDetail(Instant due) {
        return due == null ? null : "Due " + DUE_FORMAT.format(due);
    }

    private static String mediumLabel(String medium) {
        if (medium == null) return null;
        return switch (medium) {
            case "CASH" -> "Cash";
            case "UPI" -> "UPI";
            case "PHONEPE" -> "PhonePe";
            case "PAYTM" -> "Paytm";
            case "IN_APP" -> "In-app";
            case "OFFLINE" -> "Offline";
            default -> null;
        };
    }

    static String clip(String s) {
        return s == null || s.length() <= MAX_DETAIL ? s : s.substring(0, MAX_DETAIL - 1) + "…";
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    /** Facts of a sale: the first 3 items (name, qty), how many more, paid and total. */
    static String saleFacts(List<Line> lines, BigDecimal paid, BigDecimal total) {
        java.util.Map<String, Object> f = new java.util.LinkedHashMap<>();
        List<java.util.Map<String, Object>> items = new java.util.ArrayList<>();
        if (lines != null) {
            for (Line l : lines.subList(0, Math.min(3, lines.size()))) {
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("name", l.name());
                if (l.qty() != null) m.put("qty", l.qty().stripTrailingZeros());
                items.add(m);
            }
            if (lines.size() > 3) f.put("more", lines.size() - 3);
        }
        f.put("items", items);
        f.put("paid", paid == null ? BigDecimal.ZERO : paid);
        f.put("total", total == null ? BigDecimal.ZERO : total);
        return write(f);
    }

    static String write(java.util.Map<String, Object> facts) {
        try {
            return JSON.writeValueAsString(facts);
        } catch (Exception e) {
            return null; // facts are a nicety: the row still has its headline and detail
        }
    }

    @SuppressWarnings("unchecked")
    static java.util.Map<String, Object> read(String facts) {
        if (facts == null || facts.isBlank()) return null;
        try {
            return JSON.readValue(facts, java.util.Map.class);
        } catch (Exception e) {
            return null;
        }
    }
}
