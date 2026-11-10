package com.abhedyam.util;

import java.util.Arrays;
import java.util.List;

/**
 * A text to show an owner, still in no language: a messages key plus its arguments (specs/notifications-telugu.md).
 * Arguments may be other Txts, a List of Txts (joined with " · "), a LocalDate, numbers or plain strings
 * (names the owner typed stay as typed). A null key means {@link #raw}: the first argument is the text.
 */
public record Txt(String key, List<Object> args) {

    public static Txt of(String key, Object... args) {
        return new Txt(key, Arrays.asList(args));
    }

    /** Text the owner typed (a reminder): the same in every language. */
    public static Txt raw(String text) {
        return new Txt(null, Arrays.asList((Object) text));
    }
}
