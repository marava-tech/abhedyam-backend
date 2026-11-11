package com.abhedyam.service;

import com.abhedyam.model.enums.AppLanguage;
import com.abhedyam.util.Txt;
import org.springframework.context.support.ResourceBundleMessageSource;

import static org.mockito.ArgumentMatchers.argThat;

/** Notification texts written from the real messages*.properties, for tests that check the exact words. */
final class Texts {
    static final NotificationText TEXT = new NotificationText(source());

    private Texts() {}

    static ResourceBundleMessageSource source() {
        ResourceBundleMessageSource s = new ResourceBundleMessageSource();
        s.setBasename("messages");
        s.setDefaultEncoding("UTF-8");
        s.setFallbackToSystemLocale(false);
        return s;
    }

    static String en(Txt t) {
        return TEXT.render(t, AppLanguage.EN);
    }

    static String te(Txt t) {
        return TEXT.render(t, AppLanguage.TE);
    }

    static String hi(Txt t) {
        return TEXT.render(t, AppLanguage.HI);
    }

    /** Matches a Txt that reads exactly [english] in English. */
    static Txt says(String english) {
        return argThat(t -> t != null && english.equals(en(t)));
    }

    static Txt startsWith(String english) {
        return argThat(t -> t != null && en(t).startsWith(english));
    }
}
