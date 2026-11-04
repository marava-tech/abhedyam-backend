package com.abhedyam;

import com.abhedyam.util.CsvUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvUtilTest {
    @Test
    void quotesAndEscapes() {
        assertEquals("a,\"b,c\",\"d\"\"e\"\r\n", CsvUtil.row(List.of("a", "b,c", "d\"e")));
        assertEquals("\r\n", CsvUtil.row(List.of("")));
    }

    @Test
    void neutralisesFormulas() {
        assertEquals("'=SUM(A1)", CsvUtil.cell("=SUM(A1)"));
        assertEquals("'@x", CsvUtil.cell("@x"));
        assertEquals("'-5", CsvUtil.cell("-5"));
        assertEquals("Rice", CsvUtil.cell("Rice"));
        assertEquals("", CsvUtil.cell(null));
    }
}
