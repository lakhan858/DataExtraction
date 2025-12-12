package com.example.dataExtractionTool.service;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Service for extracting remarks from PDF using OCR (Tesseract)
 * This provides more accurate extraction of the REMARKS section
 */
@Slf4j
@Service
public class RemarksOCRExtractor {

    @Value("${tesseract.datapath:C:\\\\Program Files\\\\Tesseract-OCR\\\\tessdata}")
    private String tesseractDataPath;

    @Value("${remarks.ocr.enabled:true}")
    private boolean ocrEnabled;

    @Value("${remarks.ocr.debug:false}")
    private boolean debugMode;

    // REMARKS exact coordinates for cropping
    private static final int REMARKS_X = 1221;
    private static final int REMARKS_Y = 2765;
    private static final int REMARKS_WIDTH = 1200;
    private static final int REMARKS_HEIGHT = 370;
    private static final int RENDER_DPI = 300;

    /**
     * Crop the REMARKS area from the page image
     */
    private BufferedImage cropRemarksArea(BufferedImage pageImage) {
        return pageImage.getSubimage(REMARKS_X, REMARKS_Y, REMARKS_WIDTH, REMARKS_HEIGHT);
    }

    /**
     * Extract remarks from a PDF file using OCR
     * 
     * @param pdfFile The PDF file to extract from
     * @return Extracted remarks text
     */
    public String extractRemarks(File pdfFile) {
        if (!ocrEnabled) {
            log.info("OCR extraction is disabled");
            return "";
        }

        try (PDDocument doc = PDDocument.load(pdfFile)) {
            return extractRemarks(doc);
        } catch (IOException e) {
            log.error("Error loading PDF file for OCR extraction: {}", e.getMessage(), e);
            return "";
        }
    }

    /**
     * Extract remarks from a PDDocument using OCR
     * 
     * @param document The PDDocument to extract from
     * @return Extracted remarks text
     */
    public String extractRemarks(PDDocument document) {
        if (!ocrEnabled) {
            log.info("OCR extraction is disabled");
            return "";
        }

        try {
            PDFRenderer renderer = new PDFRenderer(document);

            // Render first page at 300 DPI
            BufferedImage pageImage = renderer.renderImageWithDPI(0, RENDER_DPI);

            // Crop the REMARKS region
            BufferedImage remarksImage = cropRemarksArea(pageImage);

            // Save debug image if enabled
            if (debugMode) {
                try {
                    ImageIO.write(remarksImage, "png", new File("remarks_debug.png"));
                    log.info("Saved debug image: remarks_debug.png");
                } catch (IOException e) {
                    log.warn("Could not save debug image: {}", e.getMessage());
                }
            }

            // Perform OCR
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(tesseractDataPath);
            tesseract.setLanguage("eng");

            String extractedText = tesseract.doOCR(remarksImage).trim();
            log.info("OCR extraction completed. Extracted {} characters", extractedText.length());

            return extractedText;

        } catch (TesseractException e) {
            log.error("Tesseract OCR error: {}", e.getMessage(), e);
            return "";
        } catch (IOException e) {
            log.error("Error rendering PDF page for OCR: {}", e.getMessage(), e);
            return "";
        } catch (Exception e) {
            log.error("Unexpected error during OCR extraction: {}", e.getMessage(), e);
            return "";
        }
    }
}
