package com.abhedyam.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Hidden search key for names typed in English, Telugu or Hindi (specs/cross-script-search.md).
 *
 * <p>Indic letters become English letters (one table: Unicode lays the Devanagari and Telugu blocks out in the same
 * order), then the text is reduced to a sound skeleton: aspirates and sibilants merged, each word's first letter
 * plus its consonants, doubles collapsed. "Bhaskar Naidu" and "భాస్కర్ నాయుడు" are both {@code bskr nd}.
 * Words stay apart: search matches a key only from the start of a word, since short keys would match inside others.
 * The app has the same function; src/test/resources/search-key-vectors.json keeps the two identical.
 */
public final class SearchKey {

    /** Search text shorter than this (or a key shorter than {@link #MIN_KEY}) uses the plain match only. */
    public static final int MIN_QUERY = 3;
    public static final int MIN_KEY = 2;

    private static final int DEVANAGARI = 0x0900;
    private static final int TELUGU = 0x0C00;
    private static final char ANUSVARA = '\u0001';
    private static final char ANUSVARA_HINDI = '\u0002'; // before h it is n in Hindi (सिंह Singh), m in Telugu (సింహ Simha)

    /** Consonants by offset in the block (0x15..0x39); every Indic script uses the same offsets. */
    private static final String[] CONSONANTS = {
        "k", "kh", "g", "gh", "ng", "ch", "chh", "j", "jh", "ny",   // 15-1E
        "t", "th", "d", "dh", "n", "t", "th", "d", "dh", "n", "n", // 1F-29
        "p", "ph", "b", "bh", "m", "y", "r", "r", "l", "l", "l",   // 2A-34
        "v", "sh", "sh", "s", "h"                                  // 35-39
    };

    private SearchKey() {
    }

    /** The key for a stored name or village; null when there is nothing to key. */
    public static String of(String text) {
        if (text == null || text.isBlank()) return null;
        String key = skeleton(toLatin(text));
        return key.isEmpty() ? null : key;
    }

    /** The key to search with, or null when the plain match is enough (short text, phone number). */
    public static String forQuery(String query) {
        if (query == null || query.trim().length() < MIN_QUERY || query.trim().matches("[0-9+ ]+")) return null;
        String key = of(query);
        return key != null && key.length() >= MIN_KEY ? key : null;
    }

    static String toLatin(String text) {
        String s = Normalizer.normalize(text, Normalizer.Form.NFC).replace("‌", "").replace("‍", "");
        StringBuilder out = new StringBuilder(s.length() * 2);
        boolean inherentA = false; // the last thing written is a consonant's implicit "a"
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int base = c >= DEVANAGARI && c < DEVANAGARI + 0x80 ? DEVANAGARI
                     : c >= TELUGU && c < TELUGU + 0x80 ? TELUGU : -1;
            if (base < 0) {
                out.append(c);
                inherentA = false;
                continue;
            }
            int off = c - base;
            String vowelSign = vowelSign(off);
            if (off >= 0x15 && off <= 0x39) {
                out.append(CONSONANTS[off - 0x15]).append('a');
                inherentA = true;
            } else if (base == DEVANAGARI && off >= 0x58 && off <= 0x5F) { // क़ ख़ ग़ ज़ ड़ ढ़ फ़ य़
                out.append(new String[] {"k", "kh", "g", "j", "d", "dh", "ph", "y"}[off - 0x58]).append('a');
                inherentA = true;
            } else if (vowelSign != null) {
                if (inherentA) out.setLength(out.length() - 1);
                out.append(vowelSign);
                inherentA = false;
            } else if (off == 0x4D) { // virama: no vowel after this consonant
                if (inherentA) out.setLength(out.length() - 1);
                inherentA = false;
            } else if (off == 0x02) { // anusvara: n or m, decided by the next letter
                out.append(base == DEVANAGARI ? ANUSVARA_HINDI : ANUSVARA);
                inherentA = false;
            } else if (off == 0x00 || off == 0x01) { // candrabindu
                out.append('n');
                inherentA = false;
            } else if (off == 0x03) { // visarga
                out.append('h');
                inherentA = false;
            } else if (off >= 0x05 && off <= 0x14 || off == 0x60 || off == 0x61) {
                out.append(independentVowel(off));
                inherentA = false;
            } else if (off >= 0x66 && off <= 0x6F) {
                out.append((char) ('0' + off - 0x66));
                inherentA = false;
            }
            // nukta, length marks, danda, avagraha: nothing to add
        }
        return resolveAnusvara(out.toString());
    }

    /** Vowel sign after a consonant; null when the offset is not one. */
    private static String vowelSign(int off) {
        return switch (off) {
            case 0x3E -> "a";
            case 0x3F, 0x40 -> "i";
            case 0x41, 0x42 -> "u";
            case 0x43, 0x44 -> "ri";
            case 0x45, 0x46, 0x47 -> "e";
            case 0x48 -> "ai";
            case 0x49, 0x4A, 0x4B -> "o";
            case 0x4C -> "au";
            case 0x62, 0x63 -> "li";
            default -> null;
        };
    }

    private static String independentVowel(int off) {
        return switch (off) {
            case 0x05, 0x06 -> "a";
            case 0x07, 0x08 -> "i";
            case 0x09, 0x0A -> "u";
            case 0x0B, 0x60 -> "ri";
            case 0x0C, 0x61 -> "li";
            case 0x0D, 0x0E, 0x0F -> "e";
            case 0x10 -> "ai";
            case 0x14 -> "au";
            default -> "o"; // 0x11-0x13
        };
    }

    /** n before k/g/ch/j/t/d/n sounds ("Venkatesh", "Warangal"), m otherwise ("Janampeta", "Vamshi", "Simha"). */
    private static String resolveAnusvara(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != ANUSVARA && c != ANUSVARA_HINDI) {
                out.append(c);
                continue;
            }
            char next = i + 1 < s.length() ? s.charAt(i + 1) : ' ';
            boolean n = "kgcjtdn".indexOf(next) >= 0 || c == ANUSVARA_HINDI && next == 'h';
            out.append(n ? 'n' : 'm');
        }
        return out.toString();
    }

    static String skeleton(String latin) {
        String s = Normalizer.normalize(latin.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9 ]", " ");
        String[][] same = {
            {"ck", "k"}, {"x", "ks"}, {"q", "k"}, {"z", "j"}, {"w", "v"}, {"ph", "p"}, {"f", "p"},
            {"th", "t"}, {"dh", "d"}, {"kh", "k"}, {"gh", "g"}, {"bh", "b"}, {"jh", "j"},
            {"ch", "c"}, {"sh", "s"}, {"y", "i"}
        };
        for (String[] r : same) s = s.replace(r[0], r[1]);
        StringBuilder out = new StringBuilder(s.length());
        for (String word : s.trim().split("\\s+")) {
            if (word.isEmpty()) continue;
            StringBuilder w = new StringBuilder().append(word.charAt(0));
            for (int i = 1; i < word.length(); i++) {
                char c = word.charAt(i);
                if ("aeiouh".indexOf(c) < 0) w.append(c); // h left after vowels: "Mahesh", "Simha" spellings vary
            }
            if (!out.isEmpty()) out.append(' ');
            out.append(w.toString().replaceAll("(.)\\1+", "$1"));
        }
        return out.toString();
    }
}
