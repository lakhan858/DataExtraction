package com.example.dataExtractionTool.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;

/**
 * Extracts REMARKS from Daily Mud Report PDFs
 * by reading ONLY the right 50% of the page.
 *
 * ✔ Independent of vertical position
 * ✔ Ignores left-side tables
 * ✔ Very stable for DMR layout
 */
@Slf4j
@Service
public class RemarksTextExtractor {

    @Value("${remarks.extraction.enabled:true}")
    private boolean extractionEnabled;

    private static final String REGION_RIGHT = "RIGHT_HALF";
    private static final String EMPTY = "";

    private static final String REMARKS_HEADER = "REMARKS";

    public String extractRemarks(File pdfFile) {

        if (!extractionEnabled) {
            return EMPTY;
        }

        try (PDDocument document = PDDocument.load(pdfFile)) {

            if (document.getNumberOfPages() == 0) {
                return EMPTY;
            }

            PDPage page = document.getPage(0);
            PDRectangle mediaBox = page.getMediaBox();

            float pageWidth = mediaBox.getWidth();
            float pageHeight = mediaBox.getHeight();

            // Right side of the page - start slightly to the left of center to avoid cutting off text
            // This ensures we capture the full beginning of lines
            int marginLeft = 20; // Small margin to capture text that starts near the center line
            Rectangle rightHalf = new Rectangle(
                    (int) (pageWidth / 2) - marginLeft,
                    0,                      // Y
                    (int) (pageWidth / 2) + marginLeft,  // width
                    (int) pageHeight
            );

            PDFTextStripperByArea stripper = new PDFTextStripperByArea();
            stripper.setSortByPosition(true);
            stripper.addRegion(REGION_RIGHT, rightHalf);

            stripper.extractRegions(page);

            String rightText = stripper.getTextForRegion(REGION_RIGHT);

            return extractRemarksFromRightSide(rightText);

        } catch (Exception e) {
            log.error("Failed to extract remarks", e);
            return EMPTY;
        }
    }

    // ==========================================================
    // Parse remarks from right half text
    // ==========================================================

    private String extractRemarksFromRightSide(String text) {

        if (text == null || text.isBlank()) {
            return EMPTY;
        }

        String upper = text.toUpperCase();

        int remarksIndex = upper.indexOf(REMARKS_HEADER);

        if (remarksIndex == -1) {
            return EMPTY;
        }

        // Take content AFTER remarks header
        String remarksBlock = text.substring(remarksIndex);

        // Remove header
        remarksBlock = remarksBlock.replaceFirst("(?i)REMARKS", "");

        // Stop at next table section
        remarksBlock = remarksBlock
                .split("(?i)ADDITION \\(BBL\\)|LOSS \\(BBL\\)|VOL\\. TRACK|ANNULAR HYDRAULICS")[0];

        return cleanText(remarksBlock);
    }

    // ==========================================================
    // Cleanup
    // ==========================================================

    private String cleanText(String text) {

        return text
                .replaceAll("\r\n|\r", "\n")
                .replaceAll("[ \t]+", " ")
                .replaceAll(" *\n *", "\n")
                .replaceAll("\\n{2,}", "\n")
                .trim();
    }
}
