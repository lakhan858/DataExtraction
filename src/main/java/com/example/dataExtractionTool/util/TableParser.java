package com.example.dataExtractionTool.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for parsing table data from PDF text
 */
public class TableParser {

    /**
     * Clean and normalize text by removing extra whitespace
     */
    public static String cleanText(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().replaceAll("\\s+", " ");
    }

    /**
     * Split a line by multiple spaces (typically used in table parsing)
     */
    public static List<String> splitByMultipleSpaces(String line) {
        List<String> parts = new ArrayList<>();
        if (line == null || line.trim().isEmpty()) {
            return parts;
        }

        // Split by 2 or more spaces
        String[] tokens = line.split("\\s{2,}");
        for (String token : tokens) {
            String cleaned = token.trim();
            if (!cleaned.isEmpty()) {
                parts.add(cleaned);
            }
        }
        return parts;
    }

    /**
     * Check if a line contains a specific keyword (case-insensitive)
     */
    public static boolean containsKeyword(String line, String keyword) {
        if (line == null || keyword == null) {
            return false;
        }
        return line.toLowerCase().contains(keyword.toLowerCase());
    }

    /**
     * Extract lines between two markers
     */
    public static List<String> extractLinesBetween(List<String> allLines, String startMarker, String endMarker) {
        List<String> result = new ArrayList<>();
        boolean capturing = false;

        for (String line : allLines) {
            if (containsKeyword(line, startMarker)) {
                capturing = true;
                continue;
            }

            if (capturing && containsKeyword(line, endMarker)) {
                break;
            }

            if (capturing && !line.trim().isEmpty()) {
                result.add(line);
            }
        }

        return result;
    }

    /**
     * Check if a line appears to be a table header
     */
    public static boolean isTableHeader(String line) {
        if (line == null) {
            return false;
        }
        String lower = line.toLowerCase();
        return lower.contains("properties") ||
                lower.matches(".*sample\\s*[0-9].*") || // Only match "Sample 1", "Sample 2" etc.
                (lower.contains("product") && lower.contains("initial")) ||
                lower.contains("inventory");
    }

    /**
     * Remove non-numeric characters except decimal points and negative signs
     */
    public static String extractNumericValue(String value) {
        if (value == null) {
            return "";
        }
        // Keep digits, decimal points, and negative signs
        return value.replaceAll("[^0-9.\\-]", "").trim();
    }
}
