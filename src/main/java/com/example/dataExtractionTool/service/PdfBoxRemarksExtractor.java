package com.example.dataExtractionTool.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Service for extracting remarks from PDF using PDFBox text extraction with a Text-First approach.
 * This implementation extracts the full page text and parses it based on section headers.
 * 
 *
 * NOTE: We use setSortByPosition(false) to relying on the PDF's internal stream order.
 * In many two-column PDFs, the text stream is stored column-by-column (Left
 * then Right, or Right then Left).
 * If successful, this separates the "Recommended Tour Treatments" (Left) from "Remarks" (Right) naturally,
 * avoiding the line-merging issue found when sorting by position.
 */
@Slf4j
@Service
public class PdfBoxRemarksExtractor {



    // Known section headers that might follow the REMARKS section.
    // The extractor will stop capturing t
            static final List<String> NEXT_SECTION_HEADERS = Arrays.asList(
            "RECOMMENDED TOUR TREATMENTS",
            "ADDITION",
            "LOSS",
            "VOL. TRACK",
            "SOLIDS ANALYSIS",
            "BIT HYDRAULICS",
            "TIME DISTRIBUTION",
            "ANNULAR HYDRAULICS",
            "ENGINEERING",
            "INVENTORY",
            "MUD PROPERTIES",
            "DRILL STRING",
            "CASING",
            "PUMP",
            "SOLID CONTR",
            "Cuttings/retention",
            "Start vol."
    );
}

    /**
     * Extracts remarks from a PDF file using text-based extraction logic.
     *
                File The PDF file to extract from
                tracted and cleaned rem

                 extractRemarks(File pdfFile) {
        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();

            // IMPORTANT: Set to false to prefer stream order over visual layout order.
            // This often keeps column text c
            stripper.setSortByPosition(false);

            // Extract text from the first page (usually where remarks are)
            stripper.setStartPage(1);
            stripper.setEndPage(1);

            String fullText = stripper.getText(document);


            return parseRemarksFromText(fullText);

        } catch (IOException e) {
            log.error("Failed to extract remarks from PDF: {}", e.getMessage(), e);
            return "";
        }
    }


    private String parseRemarksFromText(String fullText) {
        if (fullText == null || fullText.isEmpty()) {
            return "";
        }

        // 1. Locate the keyword REMARKS
        int remarksStartIndex = fullText.indexOf(REMARKS_HEADER);
        if (remarksStartIndex == -1) {
            log.info("REMARKS header not found in text");
            return "";
        }

        // Move index past "REMARKS"
        String textAfterHeader = fullText.substring(remarksStartIndex + REMARKS_HEADER.length());

        // 2. Find the nearest next section header to stop at
        int minStopIndex = textAfterHeader.length();

        for (String header : NEXT_SECTION_HEADERS) {
            int index = textAfterHeader.indexOf(header);
            if (index != -1 && index < minStopIndex) {
                minStopIndex = index;
            }
        }

        // 3. Capture text
        String rawRemarks = textAfterHeader.substring(0, minStopIndex);

        // 4. Clean the text
        return cleanRemarksText(rawRemarks);
    }

    private String cleanRemarksText(String text) {
        if (text == null) return "";


        return text
                // Replace carriage returns with newlines
                .replaceAll("\r\n", "\
                ")
                .replaceAll("\r", "\n")
                // Remove OBM/WBM specific patterns if they appear in remarks
                .replaceAll("OBM on Location/Lease.*", "")
                .replaceAll("WBM Tanks.*", "")
                // Remove the "Recommended..." text if it accidentally got captured (as a fallback regex)
                // This regex handles cases where interleaved text usually starts with th
            // se known phrases
                .replaceAll("(?m)^.*Circulate brine reserve pit.*$", "")
                .replaceAll("(?m)^.*MF-55.*$", "")
            //
                .replaceAll("(?m)^.*Corrosion switches.*$", "")

                .replaceAll("(?m)^.*Gel sweeps in.*$", "")
                .replaceAll("(?m)^.*Fill with fresh water.*$", "")
                .replaceAll("(?m)^.*sacks caustic.*$", "")
                        ll("(?m)^.*soda ash.*$", "")
                        ize whitespace (multip
                        ll("[ \\t]+", " ")
                // Remove leading/trailing whitespace per line
                .replaceAll("(?m)^\\s+|\\s+$", "")
                // Remove empty lines
                .replaceAll("\n+", "\n")
                .trim();
    }
}



     **/

