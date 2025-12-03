package com.example.dataExtractionTool.service;

import com.example.dataExtractionTool.model.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
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

            extractor.close();

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
            } else {
                log.warn("No main data table found");
            }

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
     * Process the main table to extract all sections
     */
    private void processMainTable(Table table, PdfExtractionResult result) {
        // 1. Extract MUD PROPERTIES
        int mudPropertiesRow = findRowWithText(table, "Properties");
        if (mudPropertiesRow != -1) {
            result.setMudProperties(extractMudPropertiesFromTable(table, mudPropertiesRow));
        } else {
            mudPropertiesRow = findRowWithText(table, "Sample 1");
            if (mudPropertiesRow != -1) {
                result.setMudProperties(extractMudPropertiesFromTable(table, mudPropertiesRow));
            }
        }

        // 2. Extract REMARKS
        int remarksRow = findRowWithText(table, "REMARKS");
        if (remarksRow != -1) {
            result.setRemark(extractRemarksFromTable(table, remarksRow));
        }

        // 3. Extract LOSS
        int lossDataRow = findRowWithText(table, "Cuttings/retention");
        if (lossDataRow != -1) {
            result.setLosses(extractLossFromTable(table, lossDataRow));
        } else {
            int lossHeaderRow = findRowWithText(table, "LOSS");
            if (lossHeaderRow != -1) {
                result.setLosses(extractLossFromTable(table, lossHeaderRow + 1));
            }
        }

        // 4. Extract VOL.TRACK
        int volTrackDataRow = findRowWithText(table, "Start vol.");
        if (volTrackDataRow != -1) {
            result.setVolumeTracks(extractVolumeTrackFromTable(table, volTrackDataRow));
        } else {
            int volHeaderRow = findRowWithText(table, "VOL. TRACK");
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
     * Extract MUD PROPERTIES from the table
     */
    private List<MudProperty> extractMudPropertiesFromTable(Table table, int startRow) {
        List<MudProperty> mudProperties = new ArrayList<>();

        int propertiesColIndex = -1;
        List<RectangularTextContainer> headerRow = table.getRows().get(startRow);

        for (int col = 0; col < headerRow.size(); col++) {
            String cellText = getCellText(headerRow, col);
            if (cellText.contains("Properties") || cellText.contains("Sample")) {
                propertiesColIndex = col;
                break;
            }
        }

        if (propertiesColIndex == -1)
            propertiesColIndex = 0;

        for (int i = startRow + 1; i < table.getRowCount(); i++) {
            List<RectangularTextContainer> row = table.getRows().get(i);
            String rowText = getRowText(row);

            if (rowText.contains("REMARKS") || rowText.contains("ANNULAR") || rowText.trim().isEmpty()) {
                break;
            }

            if (row.size() > propertiesColIndex) {
                MudProperty property = new MudProperty();
                String propertyName = getCellText(row, propertiesColIndex);

                if (propertyName.trim().isEmpty() || propertyName.contains("Properties"))
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

        for (int col = 0; col < headerRow.size(); col++) {
            String cellText = getCellText(headerRow, col);
            if (cellText.contains("REMARKS") && !cellText.contains("RECOMMENDED")) {
                remarksColIndex = col;
                break;
            }
        }

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
            log.warn("Could not find REMARKS column");
            return remark;
        }

        // Scan rows
        for (int i = headerRowIndex + 1; i < Math.min(headerRowIndex + 30, table.getRowCount()); i++) {
            List<RectangularTextContainer> row = table.getRows().get(i);

            // Stop if we hit the next section
            String rowText = getRowText(row);
            if (rowText.contains("ADDITION") || rowText.contains("LOSS") || rowText.contains("VOL. TRACK")) {
                break;
            }

            // SMART COLUMN READING
            String cellText = getSmartCellText(row, remarksColIndex);

            // Check for OBM/WBM
            if (cellText.contains("OBM on Location/Lease")) {
                Pattern pattern = Pattern.compile("OBM on Location/Lease.*?:\\s*([\\d,/\\s]+)");
                Matcher matcher = pattern.matcher(cellText);
                if (matcher.find())
                    remark.setObmOnLocationLease(matcher.group(1).trim());
            } else if (cellText.contains("WBM Tanks")) {
                Pattern pattern = Pattern.compile("WBM Tanks.*?:\\s*(.+)");
                Matcher matcher = pattern.matcher(cellText);
                if (matcher.find())
                    remark.setWbmTanks(matcher.group(1).trim());
            } else if (!cellText.trim().isEmpty()) {
                // Narrative text
                if (remarkText.length() > 0)
                    remarkText.append(" ");
                remarkText.append(cellText.trim());
            }
        }

        remark.setRemarkText(remarkText.toString());
        return remark;
    }

    /**
     * Extract LOSS(bbl) table using Anchor Data Row
     */
    private List<Loss> extractLossFromTable(Table table, int startRowIndex) {
        List<Loss> losses = new ArrayList<>();

        int lossCategoryColIndex = -1;
        List<RectangularTextContainer> startRow = table.getRows().get(startRowIndex);

        for (int col = 0; col < startRow.size(); col++) {
            String cellText = getCellText(startRow, col);
            if (cellText.contains("Cuttings/retention")) {
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
            if (rowText.contains("ANNULAR HYDRAULICS")) {
                // Search specifically for "Formation" in this row
                for (RectangularTextContainer cell : row) {
                    if (cell.getText().trim().equals("Formation")) {
                        Loss loss = Loss.builder().category("Formation").value("").build();
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

            if (category.trim().isEmpty() || category.contains("LOSS") || category.contains("bbl"))
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
    private List<VolumeTrack> extractVolumeTrackFromTable(Table table, int startRowIndex) {
        List<VolumeTrack> volumeTracks = new ArrayList<>();

        int volCategoryColIndex = -1;
        List<RectangularTextContainer> startRow = table.getRows().get(startRowIndex);

        for (int col = 0; col < startRow.size(); col++) {
            String cellText = getCellText(startRow, col);
            if (cellText.contains("Start vol.")) {
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
            if (rowText.contains("ANNULAR HYDRAULICS")) {
                // Search specifically for "Returned" in this row
                for (RectangularTextContainer cell : row) {
                    if (cell.getText().trim().equals("Returned")) {
                        VolumeTrack vt = VolumeTrack.builder().category("Returned").value("").build();
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
