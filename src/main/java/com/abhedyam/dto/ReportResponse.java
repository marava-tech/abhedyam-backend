package com.abhedyam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Reports for any range of the owner's days: what was sold, what came in, and per day for the chart. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponse {
    private LocalDate from;
    private LocalDate to;
    private BigDecimal sold;
    private int bills;
    private int customers;
    /** Money received (write-offs left out). */
    private BigDecimal collected;
    private BigDecimal collectedCash;
    /** UPI and other app payments. */
    private BigDecimal collectedUpi;
    /** Dues forgiven ("not getting this back"): lowers what is owed without money coming in. */
    private BigDecimal writtenOff;
    /** Every day from `from` to `to`, including days with nothing. */
    private List<Day> days;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Day {
        private LocalDate date;
        private BigDecimal sold;
        private int bills;
        private BigDecimal collected;
    }
}
