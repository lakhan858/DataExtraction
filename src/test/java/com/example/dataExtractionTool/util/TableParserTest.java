package com.example.dataExtractionTool.util;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TableParserTest {

    @Test
    void testSplitByMultipleSpaces() {
        String line = "Property Name  Sample 1   Sample 2    Sample 3";
        List<String> parts = TableParser.splitByMultipleSpaces(line);

        assertEquals(4, parts.size());
        assertEquals("Property Name", parts.get(0));
        assertEquals("Sample 1", parts.get(1));
        assertEquals("Sample 2", parts.get(2));
        assertEquals("Sample 3", parts.get(3));
    }

    @Test
    void testIsTableHeader() {
        assertTrue(TableParser.isTableHeader("MUD PROPERTIES"));
        assertTrue(TableParser.isTableHeader("Sample 1 Sample 2"));
        assertTrue(TableParser.isTableHeader("Product Initial Received"));
        assertFalse(TableParser.isTableHeader("Diesel 10433 8740"));
    }

    @Test
    void testExtractNumericValue() {
        assertEquals("123.45", TableParser.extractNumericValue("123.45"));
        assertEquals("-122", TableParser.extractNumericValue("-122"));
        assertEquals("22496.80", TableParser.extractNumericValue("$22,496.80"));
        assertEquals("0.00", TableParser.extractNumericValue("0.00"));
    }

    @Test
    void testCleanText() {
        assertEquals("Hello World", TableParser.cleanText("  Hello   World  "));
    }
}
