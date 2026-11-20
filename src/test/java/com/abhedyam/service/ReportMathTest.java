package com.abhedyam.service;

import com.abhedyam.dto.ReportResponse;
import com.abhedyam.model.enums.PaymentMedium;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReportMathTest {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final UUID RAVI = UUID.randomUUID(), SITA = UUID.randomUUID();

    private static Object[] sale(String at, int amount, String bill, UUID customer) {
        return new Object[]{Instant.parse(at), BigDecimal.valueOf(amount), bill, customer};
    }

    private static Object[] pay(String at, int amount, PaymentMedium medium, boolean writeOff) {
        return new Object[]{Instant.parse(at), BigDecimal.valueOf(amount), medium, writeOff};
    }

    @Test
    void sumsByTheShopsDay_splitsCashAndUpi_keepsWriteOffsOut() {
        LocalDate from = LocalDate.of(2026, 11, 19), to = LocalDate.of(2026, 11, 21);
        ReportResponse r = ReportMath.build(from, to, IST,
                List.of(
                        sale("2026-11-19T19:00:00Z", 1000, "t1", RAVI), // 20 Nov 00:30 in India, not the 19th
                        sale("2026-11-19T19:00:00Z", 500, "t1", RAVI),  // same bill, second line
                        sale("2026-11-21T05:00:00Z", 300, "t2", SITA)),
                List.of(
                        pay("2026-11-20T06:00:00Z", 1000, PaymentMedium.CASH, false),
                        pay("2026-11-20T07:00:00Z", 200, PaymentMedium.PHONEPE, false),
                        pay("2026-11-21T08:00:00Z", 50, PaymentMedium.OFFLINE, false),
                        pay("2026-11-21T09:00:00Z", 400, PaymentMedium.CASH, true))); // forgiven, not money in

        assertThat(r.getSold()).isEqualByComparingTo("1800");
        assertThat(r.getBills()).isEqualTo(2);
        assertThat(r.getCustomers()).isEqualTo(2);
        assertThat(r.getCollected()).isEqualByComparingTo("1250");
        assertThat(r.getCollectedCash()).isEqualByComparingTo("1050");
        assertThat(r.getCollectedUpi()).isEqualByComparingTo("200");
        assertThat(r.getWrittenOff()).isEqualByComparingTo("400");

        assertThat(r.getDays()).hasSize(3); // the empty 19th is there too
        assertThat(r.getDays().get(0).getSold()).isEqualByComparingTo("0");
        assertThat(r.getDays().get(1).getSold()).isEqualByComparingTo("1500");
        assertThat(r.getDays().get(1).getBills()).isEqualTo(1);
        assertThat(r.getDays().get(1).getCollected()).isEqualByComparingTo("1200");
        assertThat(r.getDays().get(2).getCollected()).isEqualByComparingTo("50");
    }

    @Test
    void emptyRange_isAllZerosWithEveryDay() {
        ReportResponse r = ReportMath.build(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), IST, List.of(), List.of());
        assertThat(r.getSold()).isEqualByComparingTo("0");
        assertThat(r.getCollected()).isEqualByComparingTo("0");
        assertThat(r.getDays()).hasSize(30);
    }
}
