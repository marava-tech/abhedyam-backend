package com.abhedyam.service;

import com.abhedyam.model.enums.AppLanguage;
import com.abhedyam.util.Txt;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Writes a {@link Txt} in an app language from messages*.properties (specs/notifications-telugu.md). */
@Component
@RequiredArgsConstructor
public class NotificationText {

    private final MessageSource messages;

    public String render(Txt t, AppLanguage lang) {
        if (t == null) return null;
        if (t.key() == null) return String.valueOf(t.args().get(0));
        Object[] args = t.args().stream().map(a -> arg(a, lang)).toArray();
        return messages.getMessage(t.key(), args, lang.locale());
    }

    /** Title and body in every app language. */
    public Map<AppLanguage, String[]> renderAll(Txt title, Txt body) {
        Map<AppLanguage, String[]> out = new EnumMap<>(AppLanguage.class);
        for (AppLanguage lang : AppLanguage.values()) out.put(lang, new String[] {render(title, lang), render(body, lang)});
        return out;
    }

    /** For {@link com.abhedyam.service.interfaces.IFcmService}: {title, body} for a language code; unknown code = English. */
    public java.util.function.Function<String, String[]> byCode(Txt title, Txt body) {
        Map<AppLanguage, String[]> all = renderAll(title, body);
        return code -> {
            for (AppLanguage l : AppLanguage.values()) if (l.code().equals(code)) return all.get(l);
            return all.get(AppLanguage.EN);
        };
    }

    private Object arg(Object a, AppLanguage lang) {
        if (a instanceof Txt t) return render(t, lang);
        if (a instanceof List<?> parts) {
            return String.join(" · ", parts.stream().map(p -> String.valueOf(arg(p, lang))).toList());
        }
        if (a instanceof LocalDate d) {
            return d.format(DateTimeFormatter.ofPattern(messages.getMessage("fmt.day", null, lang.locale()), lang.locale()));
        }
        return a;
    }
}
