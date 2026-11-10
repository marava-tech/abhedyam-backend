package com.abhedyam.service;

import com.abhedyam.model.Notification;
import com.abhedyam.model.enums.AppLanguage;
import com.abhedyam.util.Txt;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** Owner notifications in English and Telugu (specs/notifications-telugu.md). */
class NotificationTextTest {

    private static Properties load(String file) throws Exception {
        Properties p = new Properties();
        try (var in = new InputStreamReader(NotificationTextTest.class.getResourceAsStream("/" + file), StandardCharsets.UTF_8)) {
            p.load(in);
        }
        return p;
    }

    @Test
    void everyEnglishTextHasATeluguOne() throws Exception {
        assertThat(load("messages_te.properties").stringPropertyNames()).containsExactlyInAnyOrderElementsOf(
            load("messages.properties").stringPropertyNames());
    }

    @Test
    void dueReminderInTelugu_namesAndNotesStayAsTyped() {
        Txt title = Txt.of("notif.due.title.tomorrow", "Prava");
        Txt body = Txt.of("notif.join", List.of(Txt.of("notif.due.body", "5,000", LocalDate.of(2026, 11, 12)), "after harvest"));
        assertThat(Texts.te(title)).isEqualTo("Prava రేపు కడతారు");
        assertThat(Texts.te(body)).isEqualTo("₹5,000 12 నవంబర్, గురువారం న కట్టాలి · after harvest");
        assertThat(Texts.en(body)).isEqualTo("₹5,000 due on Thu, 12 Nov · after harvest");
    }

    @Test
    void digestPluralsInBothLanguages() {
        assertThat(Texts.en(Txt.of("notif.digest.owe", 1, "500", 1L))).isEqualTo("1 customer owes ₹500 · oldest 1 day");
        assertThat(Texts.en(Txt.of("notif.digest.owe", 7, "1,24,000", 45L))).isEqualTo("7 customers owe ₹1,24,000 · oldest 45 days");
        assertThat(Texts.te(Txt.of("notif.digest.owe", 7, "1,24,000", 45L))).isEqualTo("7 మంది కస్టమర్లు ₹1,24,000 బాకీ · పాతది 45 రోజులు");
        assertThat(Texts.te(Txt.of("notif.digest.duesToday", 1, "5,000"))).isEqualTo("ఈరోజు 1 బాకీ ₹5,000");
    }

    @Test
    void rowKeepsEveryLanguage_andTheListPicksOne() {
        Notification n = new Notification();
        n.setLocalized(Texts.TEXT.renderAll(Txt.of("notif.digest.title.money"), Txt.raw("x")));
        assertThat(n.getTitle()).isEqualTo("Money to collect");
        assertThat(n.textIn("te")[0]).isEqualTo("వసూలు చేయాల్సిన డబ్బు");
        assertThat(n.textIn("en")[0]).isEqualTo("Money to collect");
        assertThat(n.textIn("hi")[0]).isEqualTo("Money to collect"); // no Hindi yet: English
    }

    @Test
    void unknownPhoneLanguageGetsEnglish() {
        var byCode = Texts.TEXT.byCode(Txt.of("notif.test.title"), Txt.of("notif.test.body"));
        assertThat(byCode.apply("te")[0]).isEqualTo("టెస్ట్ నోటిఫికేషన్");
        assertThat(byCode.apply("xx")[0]).isEqualTo("Test notification");
        assertThat(AppLanguage.values()).hasSize(2); // a new language needs messages_<code>.properties first
    }
}
