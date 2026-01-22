package com.example.dataExtractionTool.service;

import com.example.dataExtractionTool.model.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import technology.tabula.*;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for extracting data from PDF files using Tabula for accurate table
 * extraction
 */
@Slf4j
@Service
public class PdfExtractionService {

    private final RemarksTextExtractor remarksTextExtractor;

    public PdfExtractionService(RemarksTextExtractor remarksTextExtractor) {
        this.remarksTextExtractor = remarksTextExtractor;
    }

    // -- Constants for Field Labels --
    private static final String LABEL_WELL_NAME = "Well Name";
    private static final String LABEL_WELL_NO = "Well No";
    private static final String LABEL_WELL_NO_DOT = "Well No.";
    private static final String LABEL_API_WELL_NO = "API well No.";
    private static final String LABEL_API_WELL_NO_NO_DOT = "API well No";
    private static final String LABEL_REPORT_NO = "Report No";
    private static final String LABEL_REPORT_NO_DOT = "Report No.";
    private static final String LABEL_REPORT_DATE = "Report date";
    private static final String LABEL_REPORT_TIME = "Report time";
    private static final String LABEL_SPUD_DATE = "Spud date";
    private static final String LABEL_RIG = "Rig";
    private static final String LABEL_ACTIVITY = "Activity";
    private static final String LABEL_MD_FT = "MD(ft)";
    private static final String LABEL_MD_FT_SPACED = "MD (ft)";
    private static final String LABEL_TVD_FT = "TVD(ft)";
    private static final String LABEL_TVD_FT_SPACED = "TVD (ft)";
    private static final String LABEL_INC = "Inc";
    private static final String LABEL_AZI = "AZI";
    private static final String LABEL_AZI_LOWER = "Azi";
    private static final String LABEL_REMARKS = "REMARKS";
    private static final String LABEL_LOSS = "LOSS";
    private static final String LABEL_VOL_TRACK = "VOL. TRACK";

    // -- Constants for Extraction Headers --
    private static final String HEADER_MUD_PROPERTIES = "Properties";
    private static final String HEADER_MUD_SAMPLE_1 = "Sample 1";
    private static final String HEADER_LOSS_CUTTINGS = "Cuttings/retention";
    private static final String HEADER_VOL_START = "Start vol.";
    private static final String HEADER_ANNULAR_HYDRAULICS = "ANNULAR HYDRAULICS";
    private static final String KEYWORD_SAMPLE = "Sample";
    private static final String DATA_FORMATION = "Formation";
    private static final String DATA_RETURNED = "Returned";

    // -- Regex Patterns --
    private static final Pattern PATTERN_WELL_NAME_1 = Pattern
            .compile("(?:Well Name(?:/No\\.?|/No|\\.| )?)\\s*[:~\\-]?\\s*(.*?)(?:\\r?\\n|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_WELL_NO_2 = Pattern.compile("Well No\\.?\\s*[:~\\-]?\\s*(.*?)(?:\\r?\\n|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_OBM = Pattern.compile("(OBM on Location/Lease.*?:\\s*([\\d,/.\\s]+))");
    private static final Pattern PATTERN_WBM = Pattern.compile("(WBM Tanks.*?:\\s*(.+))");

    /**
     * Main method to extract data from a PDF file using Tabula
     */
    public PdfExtractionResult extractData(File pdfFile) {
        PdfExtractionResult result = new PdfExtractionResult(pdfFile.getName());
        result.setExtractionTimestamp(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        try (PDDocument document = PDDocument.load(pdfFile)) {

            log.info("Extracting tables from PDF: {}", pdfFile.getName());

            // Extract all tables from all pages using Tabula
            @SuppressWarnings("resource")
            ObjectExtractor extractor = new ObjectExtractor(document);
            PageIterator pageIterator = extractor.extract();

            List<Table> allTables = new ArrayList<>();
            StringBuilder rawText = new StringBuilder();

            while (pageIterator.hasNext()) {
                Page page = pageIterator.next();

                // Use SpreadsheetExtractionAlgorithm for better table detection
                SpreadsheetExtractionAlgorithm sea = new SpreadsheetExtractionAlgorithm();
                List<Table> tables = sea.extract(page);

                allTables.addAll(tables);

                log.info("Extracted {} tables from page {}", tables.size(), page.getPageNumber());
            }

            // extractor.close(); // Do not close here as it closes the PDDocument used
            // later

            // Build raw text for debugging
            for (Table table : allTables) {
                rawText.append(tableToString(table)).append("\n\n");
            }
            result.setRawText(rawText.toString());

            // Find the main data table (largest table with most rows)
            Table mainTable = findMainTable(allTables);

            if (mainTable != null) {
                log.info("Processing main table with {} rows", mainTable.getRowCount());

                // Process the main table to extract all sections
                processMainTable(mainTable, result);

                // Post-processing: If Well Name is still empty, search all tables
                if (result.getWellHeader() != null &&
                        (result.getWellHeader().getWellName() == null
                                || result.getWellHeader().getWellName().isEmpty())) {
                    log.warn("Well Name not found in main table, searching all tables...");
                    searchForWellNameInAllTables(allTables, result.getWellHeader());
                }

                // Final Fallback: If Well Name is STILL empty, use raw text extraction
                if (result.getWellHeader() != null &&
                        (result.getWellHeader().getWellName() == null
                                || result.getWellHeader().getWellName().isEmpty())) {
                    log.warn("Well Name not found in tables, attempting raw text extraction...");
                    extractWellNameFromRawText(document, result.getWellHeader());
                }
            } else {
                log.warn("No main data table found");
            }

            // ENHANCED REMARKS EXTRACTION: Use OCR for better accuracy
            extractRemarksUsingOCR(document, pdfFile, result);

            result.setSuccess(true);
            log.info("Successfully extracted all data from PDF");

        } catch (IOException e) {
            log.error("Error extracting data from PDF: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }

        return result;
    }

    /**
     * Find the main data table (usually the largest one)
     */
    private Table findMainTable(List<Table> tables) {
        Table mainTable = null;
        int maxRows = 0;

        for (Table table : tables) {
            if (table.getRowCount() > maxRows) {
                maxRows = table.getRowCount();
                mainTable = table;
            }
        }

        return mainTable;
    }

    /**
     * Search all tables for Well Name if it wasn't found in the main table
     */
    private void searchForWellNameInAllTables(List<Table> tables, WellHeader wellHeader) {
        log.info("Searching {} tables for Well Name...", tables.size());

        for (int tableIdx = 0; tableIdx < tables.size(); tableIdx++) {
            Table table = tables.get(tableIdx);
            log.info("Searching table {} with {} rows", tableIdx, table.getRowCount());

            for (int i = 0; i < Math.min(15, table.getRowCount()); i++) {
                List<RectangularTextContainer> row = table.getRows().get(i);
                String fullRowText = getRowText(row);

                for (int col = 0; col < row.size(); col++) {
                    String cellText = getCellText(row, col).trim();

                    // Check for Well Name label with various patterns
                    // Fix: Exclude "API" to avoid matching "API well No."
                    if ((cellText.toLowerCase().contains("well name") || cellText.toLowerCase().contains("well no"))
                            && !cellText.toLowerCase().contains("api")) {

                        log.info("Table {}, Row {}, Col {}: Found Well Name label: '{}'", tableIdx, i, col, cellText);
                        log.info("Full row: {}", fullRowText);

                        // Try to extract value from adjacent cells
                        String value = extractValueFromRow(row, col, false);
                        log.info("Extracted value from adjacent cells: '{}'", value);

                        // VALIDATION: If value looks like a header, ignore it
                        if (value.contains("Field") || value.contains("Block") || value.contains("Section")) {
                            log.info("Ignoring header value '{}' for Well Name", value);
                            value = "";
                        }

                        // If empty, try next row at same column
                        if (value.isEmpty() && i + 1 < table.getRowCount()) {
                            List<RectangularTextContainer> nextRow = table.getRows().get(i + 1);
                            if (col < nextRow.size()) {
                                String nextRowValue = getCellText(nextRow, col).trim();
                                if (!nextRowValue.isEmpty()) {
                                    value = nextRowValue;
                                    log.info("Found value in NEXT row at same col: '{}'", value);
                                }
                            }
                        }

                        if (!value.isEmpty()) {
                            log.info("✓ Found Well Name in table {}: '{}'", tableIdx, value);
                            wellHeader.setWellName(value);
                            return; // Found it, exit
                        } else {
                            // If adjacent cells are empty, search all cells in this row
                            log.warn("Adjacent cells empty, searching all cells in row...");
                            for (int c = 0; c < row.size(); c++) {
                                String cellValue = getCellText(row, c).trim();
                                // Skip the label itself and empty cells
                                if (!cellValue.isEmpty() && !cellValue.contains("Well Name") &&
                                        !cellValue.contains("Well No.") && !cellValue.contains("Report") &&
                                        cellValue.length() > 2) {
                                    log.info("Found potential well name at col {}: '{}'", c, cellValue);
                                    wellHeader.setWellName(cellValue);
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
        log.warn("✗ Well Name not found in any of the {} tables", tables.size());
    }

    /**
     * Extract Well Name from raw PDF text if table extraction fails
     */
    private void extractWellNameFromRawText(PDDocument document, WellHeader wellHeader) {
        try {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            String text = stripper.getText(document);

            log.info("Raw text extraction from page 1:\n{}", text);

            // Pattern 1: "Well Name/No: value" or "Well Name: value"
            Matcher m1 = PATTERN_WELL_NAME_1.matcher(text);
            if (m1.find()) {
                String value = m1.group(1).trim();
                // Filter out common noise
                if (!value.isEmpty() && !value.toLowerCase().contains("report") && value.length() > 2) {
                    log.info("Found Well Name via raw text (Pattern 1): '{}'", value);
                    wellHeader.setWellName(value);
                    return;
                }
            }

            // Pattern 2: "Well No: value"
            Matcher m2 = PATTERN_WELL_NO_2.matcher(text);
            if (m2.find()) {
                String value = m2.group(1).trim();
                if (!value.isEmpty() && !value.toLowerCase().contains("report") && value.length() > 2) {
                    log.info("Found Well Name via raw text (Pattern 2): '{}'", value);
                    wellHeader.setWellName(value);
                    return;
                }
            }

            // Pattern 3: Look for independent line that might be well name if it's near
            // "Well Name" label
            // This handles cases where label and value are on separate lines
            String[] lines = text.split("\\r?\\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.equalsIgnoreCase(LABEL_WELL_NAME) || line.equalsIgnoreCase("Well Name/No.")
                        || line.equalsIgnoreCase(LABEL_WELL_NO_DOT)) {
                    // check next line
                    if (i + 1 < lines.length) {
                        String nextLine = lines[i + 1].trim();
                        if (!nextLine.isEmpty() && !nextLine.toLowerCase().contains("report")
                                && nextLine.length() > 2) {
                            log.info("Found Well Name via raw text (Pattern 3 - next line): '{}'", nextLine);
                            wellHeader.setWellName(nextLine);
                            return;
                        }
                    }
                }
            }

        } catch (IOException e) {
            log.error("Error extracting raw text: {}", e.getMessage(), e);
        }
    }

    /**
     * Process the main table to extract all sections
     */
    private void processMainTable(Table table, PdfExtractionResult result) {
        // 0. Extract WELL HEADER (first priority - at the top of the table)
        result.setWellHeader(extractWellHeaderFromTable(table));

        // 1. Extract MUD PROPERTIES
        int mudPropertiesRow = findRowWithText(table, HEADER_MUD_PROPERTIES);
        if (mudPropertiesRow != -1) {
            result.setMudProperties(extractMudPropertiesFromTable(table, mudPropertiesRow));
        } else {
            mudPropertiesRow = findRowWithText(table, HEADER_MUD_SAMPLE_1);
            if (mudPropertiesRow != -1) {
                result.setMudProperties(extractMudPropertiesFromTable(table, mudPropertiesRow));
            }
        }

        // 2. Extract REMARKS
        int remarksRow = findRowWithText(table, LABEL_REMARKS);
        if (remarksRow != -1) {
            result.setRemark(extractRemarksFromTable(table, remarksRow));
        }

        // 3. Extract LOSS
        int lossDataRow = findRowWithText(table, HEADER_LOSS_CUTTINGS);
        if (lossDataRow != -1) {
            result.setLosses(extractLossFromTable(table, lossDataRow));
        } else {
            int lossHeaderRow = findRowWithText(table, LABEL_LOSS);
            if (lossHeaderRow != -1) {
                result.setLosses(extractLossFromTable(table, lossHeaderRow + 1));
            }
        }

        // 4. Extract VOL.TRACK
        int volTrackDataRow = findRowWithText(table, HEADER_VOL_START);
        if (volTrackDataRow != -1) {
            result.setVolumeTracks(extractVolumeTrackFromTable(table, volTrackDataRow));
        } else {
            int volHeaderRow = findRowWithText(table, LABEL_VOL_TRACK);
            if (volHeaderRow != -1) {
                result.setVolumeTracks(extractVolumeTrackFromTable(table, volHeaderRow + 1));
            }
        }
    }

    /**
     * Helper to find a row containing specific text
     */
    private int findRowWithText(Table table, String text) {
        for (int i = 0; i < table.getRowCount(); i++) {
            String rowText = getRowText(table.getRows().get(i));
            if (rowText.contains(text)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Extract Well Header information from the table
     * The well header is typically at the top of the PDF
     */
    private WellHeader extractWellHeaderFromTable(Table table) {
        WellHeader wellHeader = new WellHeader();

        // Search the first 25 rows for well header information
        int searchLimit = Math.min(25, table.getRowCount());

        // DEBUG: Print the first 10 rows of the table to see the structure
        log.info("========== TABLE STRUCTURE DEBUG (First 10 rows) ==========");
        for (int i = 0; i < Math.min(10, table.getRowCount()); i++) {
            List<RectangularTextContainer> row = table.getRows().get(i);
            StringBuilder rowDebug = new StringBuilder("Row " + i + ": ");
            for (int col = 0; col < row.size(); col++) {
                rowDebug.append("[").append(col).append("]=").append(getCellText(row, col)).append(" | ");
            }
            log.info(rowDebug.toString());
        }
        log.info("========== END TABLE STRUCTURE DEBUG ==========");

        // First pass: Extract all fields
        for (int i = 0; i < searchLimit; i++) {
            List<RectangularTextContainer> row = table.getRows().get(i);
            String fullRowText = getRowText(row);

            // Scan each cell in the row to find field labels
            for (int col = 0; col < row.size(); col++) {
                String cellText = getCellText(row, col).trim();

                // Check for each field and extract the value from the next cell
                // Check Well Name first (before other fields)
                // Fix: Case insensitive check and check next row
                // Fix: Exclude "API" to avoid false positive on "API well No."
                if ((cellText.toLowerCase().contains(LABEL_WELL_NAME.toLowerCase())
                        || cellText.toLowerCase().contains(LABEL_WELL_NO.toLowerCase()))
                        && !cellText.toLowerCase().contains("api")
                        && wellHeader.getWellName() == null) {

                    // Try same row first
                    String value = extractValueFromRow(row, col, false);

                    // VALIDATION: If value looks like a header, ignore it
                    if (value.contains("Field") || value.contains("Block") || value.contains("Section")) {
                        log.info("Ignoring header value '{}' for Well Name", value);
                        value = "";
                    }

                    // If empty, try next row at same column (common in this file)
                    if (value.isEmpty() && i + 1 < table.getRowCount()) {
                        List<RectangularTextContainer> nextRow = table.getRows().get(i + 1);
                        if (col < nextRow.size()) {
                            String nextRowValue = getCellText(nextRow, col).trim();
                            if (!nextRowValue.isEmpty()) {
                                value = nextRowValue;
                                log.info("Found value in NEXT row at same col: '{}'", value);
                            }
                        }
                    }

                    log.info("Row {}: Found 'Well Name/No.' label at col {}, extracted value: '{}'", i, col, value);
                    log.info("Full row content: {}", fullRowText);
                    wellHeader.setWellName(value);
                } else if (cellText.contains(LABEL_REPORT_NO_DOT) || cellText.equals(LABEL_REPORT_NO)) {
                    String value = extractValueFromRow(row, col, false);
                    wellHeader.setReportNo(value);
                    log.info("Found Report No: {}", value);
                } else if (cellText.contains(LABEL_REPORT_DATE) || cellText.equals(LABEL_REPORT_DATE)) {
                    String value = extractValueFromRow(row, col, false);
                    wellHeader.setReportDate(value);
                    log.info("Found Report Date: {}", value);
                } else if (cellText.contains(LABEL_REPORT_TIME) || cellText.equals(LABEL_REPORT_TIME)) {
                    String value = extractValueFromRow(row, col, false);
                    wellHeader.setReportTime(value);
                    log.info("Found Report Time: {}", value);
                } else if (cellText.contains(LABEL_SPUD_DATE) || cellText.equals(LABEL_SPUD_DATE)) {
                    String value = extractValueFromRow(row, col, false);
                    wellHeader.setSpudDate(value);
                    log.info("Found Spud Date: {}", value);
                } else if (cellText.equals(LABEL_RIG)
                        || (cellText.contains(LABEL_RIG) && !cellText.contains(LABEL_ACTIVITY)
                                && !cellText.contains("Walk"))) {
                    String value = extractValueFromRow(row, col, false);
                    wellHeader.setRig(value);
                    log.info("Found Rig: {}", value);
                } else if (cellText.contains(LABEL_ACTIVITY)) {
                    String value = extractValueFromRow(row, col, false);
                    wellHeader.setActivity(value);
                    log.info("Found Activity: {}", value);
                } else if ((cellText.equals(LABEL_MD_FT) || cellText.equals(LABEL_MD_FT_SPACED))
                        && (wellHeader.getMd() == null || wellHeader.getMd().isEmpty())) {
                    // For MD, we want a numeric value only
                    String value = extractValueFromRow(row, col, true);
                    log.info("Row {}: Found 'MD(ft)' label at col {}, extracted value: '{}'", i, col, value);
                    log.info("Full row content: {}", fullRowText);

                    // If we got "0" or empty, try to find a better value in the same row
                    if (value.isEmpty() || value.equals("0")) {
                        log.warn("MD value is '{}', searching entire row for numeric value", value);
                        value = findNumericValueInRow(row, col);
                        log.info("After full row search, MD value: '{}'", value);
                    }
                    // Only set if we found a valid non-zero value
                    if (!value.isEmpty() && !value.equals("0")) {
                        wellHeader.setMd(value);
                        log.info("Set MD to: {}", value);
                    }
                } else if (cellText.equals(LABEL_TVD_FT) || cellText.equals(LABEL_TVD_FT_SPACED)) {
                    String value = extractValueFromRow(row, col, true);
                    wellHeader.setTvd(value);
                    log.info("Found TVD: {}", value);
                } else if (cellText.contains(LABEL_INC) && cellText.contains("deg")) {
                    String value = extractValueFromRow(row, col, true);
                    wellHeader.setInc(value);
                    log.info("Found Inc: {}", value);
                } else if ((cellText.contains(LABEL_AZI) || cellText.contains(LABEL_AZI_LOWER))
                        && (cellText.contains("deg") || cellText.contains("("))
                        && (wellHeader.getAzi() == null || wellHeader.getAzi().isEmpty())) {
                    String value = extractValueFromRow(row, col, true);
                    log.info("Row {}: Found 'AZI (deg)' label at col {}, extracted value: '{}'", i, col, value);
                    log.info("Full row content: {}", fullRowText);

                    // If we got empty, try to find a numeric value in the same row
                    if (value.isEmpty()) {
                        log.warn("AZI value is empty, searching entire row for numeric value");
                        value = findNumericValueInRow(row, col);
                        log.info("After full row search, AZI value: '{}'", value);
                    }
                    // Only set if we found a valid value
                    if (!value.isEmpty()) {
                        wellHeader.setAzi(value);
                        log.info("Set AZI to: {}", value);
                    }
                } else if (cellText.contains(LABEL_API_WELL_NO) || cellText.contains(LABEL_API_WELL_NO_NO_DOT)) {
                    String value = extractValueFromRow(row, col, false);
                    wellHeader.setApiWellNo(value);
                    log.info("Found API Well No: {}", value);
                }
            }
        }

        return wellHeader;
    }

    /**
     * Search for a numeric value in the entire row, skipping the label column
     */
    private String findNumericValueInRow(List<RectangularTextContainer> row, int labelColIndex) {
        for (int col = labelColIndex + 1; col < row.size(); col++) {
            String value = getCellText(row, col).trim();
            // Look for numeric values (digits, commas, decimals, slashes)
            if (value.matches("[0-9,./\\s-]+") && !value.isEmpty()) {
                log.info("Found numeric value '{}' at column {}", value, col);
                return value;
            }
        }
        return "";
    }

    /**
     * Extract value from the cell next to the current column
     * 
     * @param row           The row to extract from
     * @param labelColIndex The column index of the label
     * @param numericOnly   If true, only accept numeric values (for MD, TVD, Inc,
     *                      AZI)
     * @return The extracted value
     */
    private String extractValueFromRow(List<RectangularTextContainer> row, int labelColIndex, boolean numericOnly) {
        // Log all cells to the right for debugging
        StringBuilder debugCells = new StringBuilder("Cells to the right: ");
        for (int offset = 1; offset <= Math.min(6, row.size() - labelColIndex - 1); offset++) {
            debugCells.append("[").append(offset).append("]='").append(getCellText(row, labelColIndex + offset))
                    .append("' ");
        }
        log.debug(debugCells.toString());

        // Search up to 6 cells to the right (expanded from 4)
        for (int offset = 1; offset <= Math.min(6, row.size() - labelColIndex - 1); offset++) {
            String value = getCellText(row, labelColIndex + offset).trim();

            // Skip empty values, units in parentheses, and colons
            if (value.isEmpty() || value.equals(":") || value.equals("(ft)") || value.equals("(deg)")) {
                continue;
            }

            // If numeric only, check if the value contains digits
            if (numericOnly) {
                // Accept if it's purely numeric or contains digits with allowed characters
                // (comma, decimal, slash)
                // Reject if it's purely alphabetic or contains parentheses
                if (value.matches("[0-9,./\\s-]+") || (value.matches(".*\\d+.*") && !value.matches(".*[A-Za-z].*"))) {
                    log.debug("Accepting numeric value: '{}'", value);
                    return value;
                }
            } else {
                // For non-numeric fields, accept any non-empty value that's not just a unit
                if (!value.contains("(") || value.length() > 5) {
                    return value;
                }
            }
        }

        return "";
    }

    /**
     * Extract MUD PROPERTIES from the table
     */
    /**
     * Extract MUD PROPERTIES from the table
     */
    private List<MudProperty> extractMudPropertiesFromTable(Table table, int startRow) {
        List<MudProperty> mudProperties = new ArrayList<>();

        int propertiesColIndex = -1;
        List<RectangularTextContainer> headerRow = table.getRows().get(startRow);

        for (int col = 0; col < headerRow.size(); col++) {
            String cellText = getCellText(headerRow, col);
            if (cellText.contains(HEADER_MUD_PROPERTIES) || cellText.contains(KEYWORD_SAMPLE)) {
                propertiesColIndex = col;
                break;
            }
        }

        if (propertiesColIndex == -1)
            propertiesColIndex = 0;

        for (int i = startRow + 1; i < table.getRowCount(); i++) {
            List<RectangularTextContainer> row = table.getRows().get(i);
            String rowText = getRowText(row);

            if (rowText.contains(LABEL_REMARKS) || rowText.contains("ANNULAR") || rowText.trim().isEmpty()) {
                break;
            }

            if (row.size() > propertiesColIndex) {
                MudProperty property = new MudProperty();
                String propertyName = getCellText(row, propertiesColIndex);

                if (propertyName.trim().isEmpty() || propertyName.contains(HEADER_MUD_PROPERTIES))
                    continue;

                property.setPropertyName(propertyName);
                if (row.size() > propertiesColIndex + 1)
                    property.setSample1(getCellText(row, propertiesColIndex + 1));
                if (row.size() > propertiesColIndex + 2)
                    property.setSample2(getCellText(row, propertiesColIndex + 2));
                if (row.size() > propertiesColIndex + 3)
                    property.setSample3(getCellText(row, propertiesColIndex + 3));
                if (row.size() > propertiesColIndex + 4)
                    property.setSample4(getCellText(row, propertiesColIndex + 4));

                mudProperties.add(property);
            }
        }
        return mudProperties;
    }

    /**
     * Extract REMARKS section
     */
    private Remark extractRemarksFromTable(Table table, int headerRowIndex) {
        Remark remark = new Remark();
        StringBuilder remarkText = new StringBuilder();

        int remarksColIndex = -1;
        List<RectangularTextContainer> headerRow = table.getRows().get(headerRowIndex);

        // 1. Find REMARKS header column
        for (int col = 0; col < headerRow.size(); col++) {
            String cellText = getCellText(headerRow, col);
            if (cellText.contains("REMARKS") && !cellText.contains("RECOMMENDED")) {
                remarksColIndex = col;
                log.info("Found REMARKS header at column {}", col);
                break;
            }
        }

        // 2. Fallback: Look for content anchors if header not found
        if (remarksColIndex == -1) {
            for (int i = headerRowIndex + 1; i < Math.min(headerRowIndex + 5, table.getRowCount()); i++) {
                List<RectangularTextContainer> row = table.getRows().get(i);
                for (int col = 0; col < row.size(); col++) {
                    String cellText = getCellText(row, col);
                    if (cellText.contains("Run production casing") || cellText.contains("Circulate casing")) {
                        remarksColIndex = col;
                        log.info("Found REMARKS column via content anchor at index {}", col);
                        break;
                    }
                }
                if (remarksColIndex != -1)
                    break;
            }
        }

        if (remarksColIndex == -1) {
            // Last resort: Assume it's in the right half of the table
            if (!headerRow.isEmpty()) {
                remarksColIndex = headerRow.size() / 2;
                log.warn("Could not find REMARKS column, defaulting to middle column index {}", remarksColIndex);
            } else {
                return remark;
            }
        }

        // 3. Scan rows
        for (int i = headerRowIndex + 1; i < Math.min(headerRowIndex + 30, table.getRowCount()); i++) {
            List<RectangularTextContainer> row = table.getRows().get(i);

            // Stop if we hit the next section
            String rowText = getRowText(row);
            if (rowText.contains("ADDITION") || rowText.contains("LOSS") || rowText.contains("VOL. TRACK")) {
                break;
            }

            // SCAN RIGHT: Concatenate text from remarksColIndex to the end of the row
            StringBuilder rowContentBuilder = new StringBuilder();
            for (int col = remarksColIndex; col < row.size(); col++) {
                String cellText = getCellText(row, col);
                if (!cellText.isEmpty()) {
                    if (rowContentBuilder.length() > 0)
                        rowContentBuilder.append(" ");
                    rowContentBuilder.append(cellText);
                }
            }
            String combinedRowText = rowContentBuilder.toString().trim();

            if (combinedRowText.isEmpty())
                continue;

            // Split by newline to handle multi-line cells correctly
            // This is critical because Tabula might merge multiple visual lines into one
            // cell content
            String[] lines = combinedRowText.split("\\r?\\n");

            for (String line : lines) {
                String originalLine = line.trim();
                String cleanLine = originalLine;

                if (originalLine.isEmpty())
                    continue;

                // Check for OBM
                if (originalLine.contains("OBM on Location/Lease")) {
                    try {
                        Matcher matcher = PATTERN_OBM.matcher(originalLine);
                        if (matcher.find()) {
                            // remark.setObmOnLocationLease(matcher.group(2).trim());
                            cleanLine = cleanLine.replace(matcher.group(1), "");
                        } else {
                            String[] parts = originalLine.split(":");
                            if (parts.length > 1) {
                                // remark.setObmOnLocationLease(parts[1].trim());
                                // Attempt cleanup of non-regex match
                                cleanLine = cleanLine.replace("OBM on Location/Lease", "").replace("(bbl)", "")
                                        .replace(":", "").replace(parts[1].trim(), "");
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Error extracting OBM: {}", e.getMessage());
                    }
                }

                // Check for WBM (Independent check)
                if (originalLine.contains("WBM Tanks")) {
                    try {
                        Matcher matcher = PATTERN_WBM.matcher(originalLine);
                        if (matcher.find()) {
                            // remark.setWbmTanks(matcher.group(2).trim());
                            cleanLine = cleanLine.replace(matcher.group(1), "");
                        } else {
                            String[] parts = originalLine.split(":");
                            if (parts.length > 1) {
                                // remark.setWbmTanks(parts[1].trim());
                                cleanLine = cleanLine.replace("WBM Tanks", "").replace("(bbl)", "").replace(":", "")
                                        .replace(parts[1].trim(), "");
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Error extracting WBM: {}", e.getMessage());
                    }
                }

                // Narrative text - Append remaining text if it's not empty/garbage
                cleanLine = cleanLine.trim();
                if (!cleanLine.isEmpty() && !cleanLine.matches("^[\\s,:]+$") && !cleanLine.contains("RECOMMENDED")) {
                    if (remarkText.length() > 0)
                        remarkText.append("\n");
                    remarkText.append(cleanLine);
                }
            }
        }

        remark.setRemarkText(remarkText.toString());
        return remark;
    }

    /**
     * Extract remarks using text extraction for enhanced accuracy
     * This method uses the RemarksTextExtractor to get better quality remarks data
     */
    private void extractRemarksUsingOCR(PDDocument document, File pdfFile, PdfExtractionResult result) {
        try {
            log.info("Attempting text-based remarks extraction...");

            // Extract using text extraction
            String ocrRemarks = remarksTextExtractor.extractRemarks(pdfFile);

            if (ocrRemarks != null && !ocrRemarks.trim().isEmpty()) {
                log.info("Text extraction successful. Extracted {} characters", ocrRemarks.length());

                // Get existing table-based remarks
                String tableRemarks = "";
                if (result.getRemark() != null && result.getRemark().getRemarkText() != null) {
                    tableRemarks = result.getRemark().getRemarkText();
                }

                // Compare and use the better extraction (longer content usually means better
                // extraction)
                if (ocrRemarks.length() > tableRemarks.length()) {
                    log.info(
                            "Text extraction is better (Text: {} chars vs Table: {} chars). Using text extraction result.",
                            ocrRemarks.length(), tableRemarks.length());

                    if (result.getRemark() == null) {
                        result.setRemark(new Remark());
                    }
                    result.getRemark().setRemarkText(ocrRemarks);
                } else {
                    log.info(
                            "Table extraction is better or equal (OCR: {} chars vs Table: {} chars). Keeping table result.",
                            ocrRemarks.length(), tableRemarks.length());
                }
            } else {
                log.warn("OCR extraction returned empty result");
            }

        } catch (Exception e) {
            log.error("Error during OCR remarks extraction: {}", e.getMessage(), e);
            // Don't fail the entire extraction, just log the error
        }
    }

    /**
     * Extract LOSS(bbl) table using Anchor Data Row
     */
    /**
     * Extract LOSS(bbl) table using Anchor Data Row
     */
    private List<Loss> extractLossFromTable(Table table, int startRowIndex) {
        List<Loss> losses = new ArrayList<>();

        int lossCategoryColIndex = -1;
        List<RectangularTextContainer> startRow = table.getRows().get(startRowIndex);

        for (int col = 0; col < startRow.size(); col++) {
            String cellText = getCellText(startRow, col);
            if (cellText.contains(HEADER_LOSS_CUTTINGS)) {
                lossCategoryColIndex = col;
                log.info("Found LOSS Category column at index {} (via anchor)", col);
                break;
            }
        }

        if (lossCategoryColIndex == -1) {
            log.warn("Could not find LOSS anchor column");
            return losses;
        }

        // Extract data
        for (int i = startRowIndex; i < Math.min(startRowIndex + 30, table.getRowCount()); i++) {
            List<RectangularTextContainer> row = table.getRows().get(i);
            String rowText = getRowText(row);

            // SPECIAL CASE: Row containing "ANNULAR HYDRAULICS"
            // This row often contains "Formation" (LOSS) and "Returned" (VOL.TRACK)
            if (rowText.contains(HEADER_ANNULAR_HYDRAULICS)) {
                // Search specifically for "Formation" in this row
                for (RectangularTextContainer cell : row) {
                    if (cell.getText().trim().equals(DATA_FORMATION)) {
                        Loss loss = Loss.builder().category(DATA_FORMATION).value("").build();
                        losses.add(loss);
                        log.info("Found Formation in ANNULAR HYDRAULICS row");
                        break;
                    }
                }
                continue; // Skip normal processing for this row
            }

            // SMART COLUMN READING
            String category = getSmartCellText(row, lossCategoryColIndex);
            String value = "";
            if (row.size() > lossCategoryColIndex + 1) {
                value = getCellText(row, lossCategoryColIndex + 1);
            }

            if (category.trim().isEmpty() || category.contains(LABEL_LOSS) || category.contains("bbl"))
                continue;

            // STRICT STOP CONDITIONS
            if (category.contains("Rig-up") || category.contains("LGS") ||
                    category.contains("HGS") || category.contains("OBM chemicals") ||
                    category.contains("SOLIDS") || category.contains("BIT") ||
                    category.contains("TIME") || category.contains("Drilling") ||
                    category.contains("Circulating")) {
                break;
            }

            Loss loss = Loss.builder().category(category.trim()).value(value.trim()).build();
            losses.add(loss);
        }

        return losses;
    }

    /**
     * Extract VOL.TRACK(bbl) table using Anchor Data Row
     */
    /**
     * Extract VOL.TRACK(bbl) table using Anchor Data Row
     */
    private List<VolumeTrack> extractVolumeTrackFromTable(Table table, int startRowIndex) {
        List<VolumeTrack> volumeTracks = new ArrayList<>();

        int volCategoryColIndex = -1;
        List<RectangularTextContainer> startRow = table.getRows().get(startRowIndex);

        for (int col = 0; col < startRow.size(); col++) {
            String cellText = getCellText(startRow, col);
            if (cellText.contains(HEADER_VOL_START)) {
                volCategoryColIndex = col;
                log.info("Found VOL.TRACK Category column at index {} (via anchor)", col);
                break;
            }
        }

        if (volCategoryColIndex == -1) {
            log.warn("Could not find VOL.TRACK anchor column");
            return volumeTracks;
        }

        // Extract data
        for (int i = startRowIndex; i < Math.min(startRowIndex + 30, table.getRowCount()); i++) {
            List<RectangularTextContainer> row = table.getRows().get(i);
            String rowText = getRowText(row);

            // SPECIAL CASE: Row containing "ANNULAR HYDRAULICS"
            // This row often contains "Returned" (VOL.TRACK)
            if (rowText.contains(HEADER_ANNULAR_HYDRAULICS)) {
                // Search specifically for "Returned" in this row
                for (RectangularTextContainer cell : row) {
                    if (cell.getText().trim().equals(DATA_RETURNED)) {
                        VolumeTrack vt = VolumeTrack.builder().category(DATA_RETURNED).value("").build();
                        volumeTracks.add(vt);
                        log.info("Found Returned in ANNULAR HYDRAULICS row");
                        break;
                    }
                }
                continue; // Skip normal processing for this row
            }

            // SMART COLUMN READING
            String category = getSmartCellText(row, volCategoryColIndex);
            String value = "";
            if (row.size() > volCategoryColIndex + 1) {
                value = getCellText(row, volCategoryColIndex + 1);
            }

            if (category.trim().isEmpty() || category.contains("VOL") || category.contains("TRACK"))
                continue;

            // Stop if we hit unrelated data
            if (category.contains("TIME") || category.contains("DISTRIBUTION"))
                break;

            VolumeTrack volumeTrack = VolumeTrack.builder().category(category.trim()).value(value.trim()).build();
            volumeTracks.add(volumeTrack);
        }

        return volumeTracks;
    }

    /**
     * Smart cell text retrieval: Checks the target column, then left, then right
     */
    private String getSmartCellText(List<RectangularTextContainer> row, int targetColIndex) {
        String text = getCellText(row, targetColIndex);
        if (!text.isEmpty())
            return text;

        // Check left
        if (targetColIndex > 0) {
            text = getCellText(row, targetColIndex - 1);
            if (!text.isEmpty())
                return text;
        }

        // Check right
        if (targetColIndex < row.size() - 1) {
            text = getCellText(row, targetColIndex + 1);
            if (!text.isEmpty())
                return text;
        }

        return "";
    }

    private String getCellText(List<RectangularTextContainer> row, int columnIndex) {
        if (columnIndex < row.size()) {
            return row.get(columnIndex).getText().trim();
        }
        return "";
    }

    private String getRowText(List<RectangularTextContainer> row) {
        StringBuilder sb = new StringBuilder();
        for (RectangularTextContainer cell : row) {
            if (sb.length() > 0)
                sb.append(" ");
            sb.append(cell.getText().trim());
        }
        return sb.toString();
    }

    private String tableToString(Table table) {
        StringBuilder sb = new StringBuilder();
        sb.append("Table with ").append(table.getRowCount()).append(" rows:\n");
        for (List<RectangularTextContainer> row : table.getRows()) {
            for (RectangularTextContainer cell : row) {
                sb.append(cell.getText()).append("\t");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
