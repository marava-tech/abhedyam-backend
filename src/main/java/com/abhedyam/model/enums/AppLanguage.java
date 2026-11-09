package com.abhedyam.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** A language the owner app is shown in. JSON uses the code ("en", "te"); an unknown code is a 400. */
public enum AppLanguage {
    EN("en"),
    TE("te");

    private final String code;

    AppLanguage(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    public Locale locale() {
        return Locale.forLanguageTag(code);
    }
}
