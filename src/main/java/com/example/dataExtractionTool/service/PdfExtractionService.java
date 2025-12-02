package com.example.dataExtractionTool.service;

import com.example.dataExtractionTool.model.InventoryItem;
import com.example.dataExtractionTool.model.MudProperty;
import com.example.dataExtractionTool.model.PdfExtractionResult;
import com.example.dataExtractionTool.util.TableParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Service for extracting data from PDF files
 */
@Slf4j
@Service
public class PdfExtractionService {

    /**
     * Main method to extract data from a PDF file
     */
    public PdfExtractionResult extractData(File pdfFile) {
        PdfExtractionResult result = new PdfExtractionResult(pdfFile.getName());
        result.setExtractionTimestamp(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true); // Essential for maintaining layout
            String text = stripper.getText(document);

            result.setRawText(text);

            log.info("Extracted text from PDF: {} ({} characters)",
                    pdfFile.getName(), text.length());

            // Process the text line by line to handle side-by-side tables
            processSideBySideTables(text, result);

            result.setSuccess(true);

        } catch (IOException e) {
            log.error("Error extracting data from PDF: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }

        return result;
    }

    /**
     * Process text containing side-by-side tables
     */
    private void processSideBySideTables(String text, PdfExtractionResult result) {
        List<MudProperty> mudProperties = new ArrayList<>();
        List<InventoryItem> inventoryItems = new ArrayList<>();

        List<String> lines = Arrays.asList(text.split("\\r?\\n"));

        boolean inDataSection = false;

        for (String line : lines) {
            String trimmedLine = line.trim();

            // Detect start of data section
            if (trimmedLine.contains("Properties") && trimmedLine.contains("Product")) {
                inDataSection = true;
                continue;
            }

            // Detect end of data section
            if (inDataSection) {
                if (trimmedLine.contains("RECOMMENDED TOUR TREATMENTS") ||
                        trimmedLine.startsWith("Formation") ||
                        trimmedLine.startsWith("Mud") && trimmedLine.contains("OBM")) {
                    inDataSection = false;
                    continue;
                }

                // We removed the TableParser.isTableHeader check here because it was falsely
                // identifying
                // "Sample from" and "Time sample taken" as headers and skipping them.

                if (!trimmedLine.isEmpty()) {
                    parseLineForBothTables(trimmedLine, mudProperties, inventoryItems);
                }
            }
        }

        result.setMudProperties(mudProperties);
        result.setInventoryItems(inventoryItems);

        log.info("Extracted {} MUD PROPERTIES and {} INVENTORY items",
                mudProperties.size(), inventoryItems.size());
    }

    private void parseLineForBothTables(String line, List<MudProperty> mudProps, List<InventoryItem> invItems) {
        // Strategy: Tokenize by single space to ensure we don't miss the split if
        // multiple spaces are missing
        String[] rawTokens = line.trim().split("\\s+");
        List<String> tokens = Arrays.asList(rawTokens);

        if (tokens.isEmpty())
            return;

        int splitIndex = -1;

        // Find the boundary between Mud Property Value and Inventory Product Name
        for (int i = 1; i < tokens.size() - 1; i++) {
            String current = tokens.get(i);
            String next = tokens.get(i + 1);

            if (isMudValue(current) && isInventoryProductStart(next)) {
                // Potential split found

                // Also check if 'next' is actually a Unit for the Mud Property
                if (isUnit(next)) {
                    if (i + 2 < tokens.size()) {
                        splitIndex = i + 2;
                    } else {
                        splitIndex = i + 1; // End of line
                    }
                } else {
                    splitIndex = i + 1;
                }

                break;
            }
        }

        String leftLine = "";
        String rightLine = "";

        if (splitIndex != -1) {
            leftLine = String.join(" ", tokens.subList(0, splitIndex));
            rightLine = String.join(" ", tokens.subList(splitIndex, tokens.size()));
        } else {
            leftLine = line;
        }

        // Parse Left (Mud Property)
        if (!leftLine.isEmpty()) {
            MudProperty mp = parseMudPropertyLine(leftLine);
            if (mp != null)
                mudProps.add(mp);
        }

        // Parse Right (Inventory)
        if (!rightLine.isEmpty()) {
            InventoryItem item = parseInventoryLine(rightLine);
            if (item != null)
                invItems.add(item);
        }
    }

    private boolean isMudValue(String token) {
        if (token.equals("Active"))
            return true;
        if (token.matches("-?[0-9]+(\\.[0-9]+)?"))
            return true; // Number
        if (token.matches("[0-9]+(/[0-9]+)+"))
            return true; // 67/38/29
        if (token.matches("[0-9]+:[0-9]+"))
            return true; // 10:00
        return false;
    }

    private boolean isInventoryProductStart(String token) {
        return token.matches("[A-Z][a-zA-Z0-9-]*");
    }

    private boolean isUnit(String token) {
        return Arrays.asList("F", "cP", "lb/bbl", "mg/L", "ppm", "%", "Volt", "sec/qt").contains(token);
    }

    /**
     * Parse a single MUD PROPERTIES table row
     */
    private MudProperty parseMudPropertyLine(String line) {
        try {
            List<String> parts = TableParser.splitByMultipleSpaces(line);
            if (parts.size() < 2) {
                parts = Arrays.asList(line.trim().split("\\s+"));
            }

            if (parts.isEmpty())
                return null;

            MudProperty property = new MudProperty();

            // Special handling for known "value-looking" property names
            String firstPart = parts.get(0);
            if (firstPart.equals("600/300/200") || firstPart.equals("100/6/3")) {
                property.setPropertyName(firstPart);
                if (parts.size() > 1)
                    property.setSample1(parts.get(1));
                if (parts.size() > 2)
                    property.setSample2(parts.get(2));
                if (parts.size() > 3)
                    property.setSample3(parts.get(3));
                if (parts.size() > 4)
                    property.setSample4(parts.get(4));
                return property;
            }

            // Heuristic to assign fields
            int firstValueIndex = -1;
            for (int i = 0; i < parts.size(); i++) {
                String part = parts.get(i);

                if (isMudValue(part)) {
                    // Check if this is "83" followed by "/" and "17"
                    if (part.matches("[0-9]+") && i + 2 < parts.size() && parts.get(i + 1).equals("/")
                            && parts.get(i + 2).matches("[0-9]+")) {
                        // This is a split ratio like 83 / 17. Treat "83" as the start of value.
                        firstValueIndex = i;
                        break;
                    }

                    firstValueIndex = i;
                    break;
                }
            }

            if (firstValueIndex != -1) {
                // Name is 0 to firstValueIndex
                String name = String.join(" ", parts.subList(0, firstValueIndex));
                property.setPropertyName(name);

                // Values follow
                if (firstValueIndex < parts.size()) {
                    String val1 = parts.get(firstValueIndex);
                    // Handle split ratio "83 / 17"
                    if (val1.matches("[0-9]+") && firstValueIndex + 2 < parts.size() &&
                            parts.get(firstValueIndex + 1).equals("/")
                            && parts.get(firstValueIndex + 2).matches("[0-9]+")) {
                        val1 = val1 + " / " + parts.get(firstValueIndex + 2);
                        // Skip the next two parts
                        firstValueIndex += 2;
                    }
                    property.setSample1(val1);
                }

                if (firstValueIndex + 1 < parts.size())
                    property.setSample2(parts.get(firstValueIndex + 1));
                if (firstValueIndex + 2 < parts.size())
                    property.setSample3(parts.get(firstValueIndex + 2));
                if (firstValueIndex + 3 < parts.size())
                    property.setSample4(parts.get(firstValueIndex + 3));

                // Check for unit at the end
                String lastPart = parts.get(parts.size() - 1);
                if (isUnit(lastPart)) {
                    property.setUnit(lastPart);
                }
            } else {
                property.setPropertyName(line);
            }

            return property;

        } catch (Exception e) {
            return null;
        }
    }

    private InventoryItem parseInventoryLine(String line) {
        try {
            List<String> parts = TableParser.splitByMultipleSpaces(line);
            if (parts.size() < 2) {
                parts = Arrays.asList(line.trim().split("\\s+"));
            }

            if (parts.isEmpty())
                return null;

            log.debug("Parsing inventory line: {} | Parts: {}", line, parts);

            InventoryItem item = new InventoryItem();

            // Find where the product name ends and numeric data begins
            // Product names can be:
            // - Text only: "Diesel", "NewPhalt"
            // - Text + Number + Unit: "DYNADET 5 GAL", "LIME 50#"
            // - Text + Number (no unit): "EXPL 9090"
            //
            // Key insight: Data columns always have MULTIPLE consecutive numbers
            // So we look for at least 2 consecutive numeric tokens
            int firstNumIndex = -1;
            for (int i = 0; i < parts.size(); i++) {
                String part = parts.get(i);

                // Check if this is a pure number
                if (part.matches("-?[0-9]+(\\.[0-9]+)?")) {
                    // Check if the next token is also a number (or negative number)
                    if (i + 1 < parts.size()) {
                        String next = parts.get(i + 1);

                        log.debug("Found number '{}' at index {}, next token: '{}'", part, i, next);

                        // If next is a unit, this number is part of product name
                        if (next.matches("(GAL|LB|#|BAG|TON|BULK).*")) {
                            log.debug("Next is a unit, skipping");
                            continue; // Skip this number, it's part of the product name
                        }

                        // If next is also a number, we found the data columns!
                        if (next.matches("-?[0-9]+(\\.[0-9]+)?")) {
                            firstNumIndex = i;
                            log.debug("Found consecutive numbers! Data starts at index {}", firstNumIndex);
                            break;
                        }

                        // If next is NOT a number and NOT a unit, this number is likely part of product
                        // name
                        // (e.g., "EXPL 9090" where next token might be end of line or another product
                        // detail)
                        log.debug("Next is not a number or unit, treating as part of product name");
                        continue;
                    } else {
                        // This is the last token and it's a number
                        // Could be part of product name (e.g., "EXPL 9090") or a single data value
                        // If we haven't found data columns yet, treat it as part of product name
                        log.debug("Number '{}' is last token, treating as part of product name", part);
                        continue;
                    }
                }
            }

            if (firstNumIndex != -1) {
                String product = String.join(" ", parts.subList(0, firstNumIndex));
                item.setProduct(product);

                log.debug("Product name: '{}', data starts at index {}", product, firstNumIndex);

                if (firstNumIndex < parts.size())
                    item.setInitial(parts.get(firstNumIndex));
                if (firstNumIndex + 1 < parts.size())
                    item.setReceived(parts.get(firstNumIndex + 1));
                if (firstNumIndex + 2 < parts.size())
                    item.setFinalValue(parts.get(firstNumIndex + 2));
                if (firstNumIndex + 3 < parts.size())
                    item.setUsed(parts.get(firstNumIndex + 3));
                if (firstNumIndex + 4 < parts.size())
                    item.setCumulative(parts.get(firstNumIndex + 4));
                if (firstNumIndex + 5 < parts.size())
                    item.setCost(parts.get(firstNumIndex + 5));
            } else {
                log.debug("No data columns found, entire line is product name");
                item.setProduct(line);
            }

            return item;
        } catch (Exception e) {
            log.error("Error parsing inventory line: {}", line, e);
            return null;
        }
    }
}
