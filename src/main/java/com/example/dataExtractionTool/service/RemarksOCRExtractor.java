package com.example.dataExtractionTool.service;
import net.sourceforge.tess4j.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

public class RemarksOCRExtractor {

    public static BufferedImage cropRemarksArea(BufferedImage pageImage) {

        // REMARKS exact coordinates
        int x = 1221;
        int y = 2765;
        int width = 1200;
        int height = 370;

        return pageImage.getSubimage(x, y, width, height);
    }

    public static String extractRemarks(String pdfPath) throws Exception {

        PDDocument doc = PDDocument.load(new File(pdfPath));
        PDFRenderer renderer = new PDFRenderer(doc);

        // Render at 300 DPI (same resolution as your screenshot)
        BufferedImage pageImage = renderer.renderImageWithDPI(0, 300);
        doc.close();

        BufferedImage remarksImage = cropRemarksArea(pageImage);

        // For debugging — saves cropped REMARKS region
        ImageIO.write(remarksImage, "png", new File("remarks.png"));

        Tesseract t = new Tesseract();
        t.setDatapath("/usr/share/tesseract-ocr/5/tessdata");
        t.setLanguage("eng");

        // OCR only the REMARKS region
        return t.doOCR(remarksImage).trim();
    }


    public static void main(String[] args) throws Exception {
        String remarks = extractRemarks("/home/pd/Downloads/FILE_2561 3 (1).pdf");
        System.out.println("Extracted REMARKS:\n" + remarks);
    }
}
