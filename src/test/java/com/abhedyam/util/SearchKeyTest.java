package com.abhedyam.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SearchKeyTest {

    /** A query finds a stored name when its key starts one of the stored key's words (the SQL does the same). */
    private static boolean finds(String query, String stored) {
        String q = SearchKey.forQuery(query);
        return q != null && (" " + SearchKey.of(stored)).contains(" " + q);
    }

    @Test
    void of_sameNameInEnglishTeluguHindi_sameKey() {
        String[][] same = {
            {"Bhaskar Naidu", "భాస్కర్ నాయుడు"}, {"Lakshmi Devi", "లక్ష్మీ దేవి"}, {"Srinivas Reddy", "శ్రీనివాస్ రెడ్డి"},
            {"Narsimha Chary", "నరసింహ చారి"}, {"Warangal", "వరంగల్"}, {"Janampeta", "జనంపేట"}, {"Hanamkonda", "హనమకొండ"},
            {"Sunita Sharma", "सुनीता शर्मा"}, {"Rajender", "राजेन्द्र"}, {"Faizan", "फ़ैज़ान"},
            {"Chandni", "चाँदनी"}, {"Gorakhpur", "गोरखपुर"},
        };
        for (String[] pair : same) assertThat(SearchKey.of(pair[1])).as(pair[0]).isEqualTo(SearchKey.of(pair[0]));
    }

    @Test
    void forQuery_partEnglishText_findsTeluguAndHindiNames() {
        assertThat(finds("bhas", "భాస్కర్ నాయుడు")).isTrue();
        assertThat(finds("naidu", "భాస్కర్ నాయుడు")).isTrue();
        assertThat(finds("venk", "వెంకటేష్ యాదవ్")).isTrue();
        assertThat(finds("warang", "వరంగల్")).isTrue();
        assertThat(finds("mahender rao", "మహేందర్ రావు")).isTrue();
        assertThat(finds("rajend", "राजेंद्र प्रसाद")).isTrue();
    }

    @Test
    void forQuery_teluguText_findsEnglishName() {
        assertThat(finds("భాస్కర్", "Bhaskar Naidu")).isTrue();
        assertThat(finds("వరంగల్", "Warangal")).isTrue();
    }

    @Test
    void forQuery_keyInsideAWord_noMatch() {
        assertThat(finds("naidu", "హనమకొండ")).isFalse(); // "nd" sits inside hnmknd
        assertThat(finds("ram", "सुनीता शर्मा")).isFalse();
    }

    @Test
    void forQuery_shortTextOrPhone_null() {
        assertThat(SearchKey.forQuery("ra")).isNull();
        assertThat(SearchKey.forQuery("98765")).isNull();
        assertThat(SearchKey.forQuery("+91 98765")).isNull();
        assertThat(SearchKey.forQuery("   ")).isNull();
        assertThat(SearchKey.forQuery(null)).isNull();
    }

    @Test
    void of_teluguKeyboardJoiners_ignored() {
        assertThat(SearchKey.of("కస్టమర్‌")).isEqualTo(SearchKey.of("కస్టమర్"));
    }

    @Test
    void of_blank_null() {
        assertThat(SearchKey.of(" ")).isNull();
        assertThat(SearchKey.of(null)).isNull();
    }

    /** The app runs the same function (item search); both test against this file so they never drift apart. */
    @Test
    void of_sharedVectors_match() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/search-key-vectors.json")) {
            List<Map<String, String>> vectors = new ObjectMapper().readValue(in, new TypeReference<>() {});
            assertThat(vectors).hasSizeGreaterThan(30);
            for (Map<String, String> v : vectors) assertThat(SearchKey.of(v.get("text"))).as(v.get("text")).isEqualTo(v.get("key"));
        }
    }
}
