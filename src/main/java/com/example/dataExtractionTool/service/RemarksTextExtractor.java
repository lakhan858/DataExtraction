package com.example.dataExtractionTool.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;

/**
 * Service for extracting remarks from PDF using PDFBox text extraction.
 * This is a pure Java alternative to Tess4j OCR, Docker-friendly with no native
 * dependencies.
 * 
 * <p>
 * Supports two extraction strategies:
 * <ul>
 * <li>Region-based extraction: Extracts text from a specific rectangular
 * area</li>
 * <li>Full-page extraction: Fallback method that searches entire page
 * content</li>
 * </ul>
 * 
 * @author DataExtraction Team
 * @since 1.0
 */
@Slf4j
@Service
public class RemarksTextExtractor {

    // Configuration properties
    @Value("${remarks.extraction.enabled:true}")
    private boolean extractionEnabled;

    @Value("${remarks.extraction.debug:false}")
    private boolean debugMode;

    // DPI conversion constants
    private static final double SOURCE_DPI = 300.0;
    private static final double TARGET_DPI = 72.0;
    private static final double DPI_CONVERSION_FACTOR = TARGET_DPI / SOURCE_DPI;

    // REMARKS region coordinates (in pixels at 300 DPI)
    private static final int REMARKS_X_PIXELS = 1221;
    private static final int REMARKS_Y_PIXELS = 2765;
    private static final int REMARKS_WIDTH_PIXELS = 1200;
    private static final int REMARKS_HEIGHT_PIXELS = 370;

    // REMARKS region coordinates (converted to points at 72 DPI)
    private static final int REMARKS_X = (int) (REMARKS_X_PIXELS * DPI_CONVERSION_FACTOR);
    private static final int REMARKS_Y = (int) (REMARKS_Y_PIXELS * DPI_CONVERSION_FACTOR);
    private static final int REMARKS_WIDTH = (int) (REMARKS_WIDTH_PIXELS * DPI_CONVERSION_FACTOR);
    private static final int REMARKS_HEIGHT = (int) (REMARKS_HEIGHT_PIXELS * DPI_CONVERSION_FACTOR);

    // Region and section identifiers
    private static final String REMARKS_REGION_NAME = "remarks";
    private static final String REMARKS_HEADER = "REMARKS";
    private static final String SECTION_DELIMITER = "RECOMMENDED TOUR TREATMENTS";

    // Patterns to remove from extracted text
    private static final String PATTERN_RECOMMENDED_TREATMENTS = "RECOMMENDED TOUR TREATMENTS.*";
    private static final String PATTERN_REMARKS_HEADER = "REMARKS\\s*";
    private static final String PATTERN_EXCESSIVE_WHITESPACE = "\\s+";

    // Page constants
    private static final int FIRST_PAGE_INDEX = 0;
    private static final int FIRST_PAGE_NUMBER = 1;

    // Empty string constant
    private static final String EMPTY_STRING = "";

    /**
     * Extracts remarks from a PDF file using text-based extraction.
     * 
     * @param pdfFile The PDF file to extract from
     * @return Extracted and cleaned remarks text, or empty string if extraction
     *         fails or is disabled
     */
    public String extractRemarks(File pdfFile) {
        if (!isExtractionEnabled()) {
            return EMPTY_STRING;
        }

        try (PDDocument document = PDDocument.load(pdfFile)) {
            return extractRemarks(document);
        } catch (IOException e) {
            log.error("Error loading PDF file for remarks extraction: {}", e.getMessage(), e);
            return EMPTY_STRING;
        }
    }

    /**
     * Extracts remarks from a PDDocument using region-based text extraction.
     * 
     * @param document The PDDocument to extract from
     * @return Extracted and cleaned remarks text, or empty string if extraction
     *         fails or is disabled
     */
    public String extractRemarks(PDDocument document) {
        if (!isExtractionEnabled()) {
            return EMPTY_STRING;
        }

        if (!validateDocument(document)) {
            return EMPTY_STRING;
        }

        try {
            PDPage firstPage = document.getPage(FIRST_PAGE_INDEX);
            PDFTextStripperByArea stripper = createTextStripper();

            Rectangle remarksRegion = createRemarksRegion();
            stripper.addRegion(REMARKS_REGION_NAME, remarksRegion);

            logExtractionDetails();

            stripper.extractRegions(firstPage);
            String extractedText = stripper.getTextForRegion(REMARKS_REGION_NAME);

            String cleanedText = cleanRemarksText(extractedText);
            logExtractionResult(cleanedText);

            return cleanedText;

        } catch (IOException e) {
            log.error("Error extracting text from PDF: {}", e.getMessage(), e);
            return EMPTY_STRING;
        } catch (Exception e) {
            log.error("Unexpected error during text extraction: {}", e.getMessage(), e);
            return EMPTY_STRING;
        }
    }

    /**
     * Extracts remarks using fallback full-page text extraction.
     * This method searches the entire page content for the REMARKS section.
     * 
     * @param document The PDDocument to extract from
     * @return Extracted and cleaned remarks text, or empty string if extraction
     *         fails
     */
    public String extractRemarksFullPage(PDDocument document) {
        try {
            PDFTextStripper textStripper = createFullPageTextStripper();
            String fullText = textStripper.getText(document);
            return extractRemarksFromFullText(fullText);

        } catch (IOException e) {
            log.error("Error in fallback text extraction: {}", e.getMessage(), e);
            return EMPTY_STRING;
        }
    }

    // ==================== Private Helper Methods ====================

    /**
     * Checks if extraction is enabled.
     */
    private boolean isExtractionEnabled() {
        if (!extractionEnabled) {
            log.info("Remarks extraction is disabled");
            return false;
        }
        return true;
    }

    /**
     * Validates that the document has pages.
     */
    private boolean validateDocument(PDDocument document) {
        if (document.getNumberOfPages() == 0) {
            log.warn("PDF document has no pages");
            return false;
        }
        return true;
    }

    /**
     * Creates and configures a PDFTextStripperByArea for region-based extraction.
     */
    private PDFTextStripperByArea createTextStripper() throws IOException {
        PDFTextStripperByArea stripper = new PDFTextStripperByArea();
        stripper.setSortByPosition(true);
        return stripper;
    }

    /**
     * Creates and configures a PDFTextStripper for full-page extraction.
     */
    private PDFTextStripper createFullPageTextStripper() throws IOException {
        PDFTextStripper textStripper = new PDFTextStripper();
        textStripper.setStartPage(FIRST_PAGE_NUMBER);
        textStripper.setEndPage(FIRST_PAGE_NUMBER);
        return textStripper;
    }

    /**
     * Creates the Rectangle defining the REMARKS region.
     */
    private Rectangle createRemarksRegion() {
        return new Rectangle(REMARKS_X, REMARKS_Y, REMARKS_WIDTH, REMARKS_HEIGHT);
    }

    /**
     * Logs extraction details if debug mode is enabled.
     */
    private void logExtractionDetails() {
        if (debugMode) {
            log.info("Extracting REMARKS from region: x={}, y={}, width={}, height={}",
                    REMARKS_X, REMARKS_Y, REMARKS_WIDTH, REMARKS_HEIGHT);
        }
    }

    /**
     * Logs the extraction result.
     */
    private void logExtractionResult(String extractedText) {
        log.info("Text extraction completed. Extracted {} characters", extractedText.length());

        if (debugMode) {
            log.debug("Extracted REMARKS text: {}", extractedText);
        }
    }

    /**
     * Cleans and formats the extracted remarks text by removing unwanted patterns
     * and whitespace.
     * 
     * @param rawText Raw extracted text
     * @return Cleaned and formatted text
     */
    private String cleanRemarksText(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return EMPTY_STRING;
        }

        return rawText
                .replaceAll(PATTERN_EXCESSIVE_WHITESPACE, " ")
                .trim()
                .replaceFirst(PATTERN_REMARKS_HEADER, EMPTY_STRING)
                .replaceAll(PATTERN_RECOMMENDED_TREATMENTS, EMPTY_STRING)
                .trim();
    }

    /**
     * Extracts REMARKS section from full page text using pattern matching.
     * 
     * @param fullText Full page text
     * @return Extracted and cleaned remarks text
     */
    private String extractRemarksFromFullText(String fullText) {
        int remarksStartIndex = fullText.indexOf(REMARKS_HEADER);
        if (remarksStartIndex == -1) {
            return EMPTY_STRING;
        }

        String remarksSection = fullText.substring(remarksStartIndex);
        int remarksEndIndex = findRemarksEndIndex(remarksSection);

        String remarks = remarksSection.substring(0, remarksEndIndex);
        remarks = removeRemarksHeader(remarks);

        return cleanRemarksText(remarks);
    }

    /**
     * Finds the end index of the REMARKS section.
     */
    private int findRemarksEndIndex(String remarksSection) {
        int endIndex = remarksSection.indexOf(SECTION_DELIMITER);
        return endIndex == -1 ? remarksSection.length() : endIndex;
    }

    /**
     * Removes the REMARKS header from the extracted text.
     */
    private String removeRemarksHeader(String text) {
        return text.replaceFirst(PATTERN_REMARKS_HEADER, EMPTY_STRING);
    }
}
