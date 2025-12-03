package com.example.dataExtractionTool.controller;

import com.example.dataExtractionTool.model.PdfExtractionResult;
import com.example.dataExtractionTool.service.FileExportService;
import com.example.dataExtractionTool.service.PdfExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for PDF data extraction
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PdfExtractionController {

    private final PdfExtractionService pdfExtractionService;
    private final FileExportService fileExportService;

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "PDF Data Extraction Tool");
        return ResponseEntity.ok(response);
    }

    /**
     * Extract data from PDF and save to TXT files
     */
    @PostMapping("/extract")
    public ResponseEntity<Map<String, Object>> extractPdf(
            @RequestParam("file") MultipartFile file) {

        Map<String, Object> response = new HashMap<>();

        try {
            // Validate file
            if (file.isEmpty()) {
                response.put("success", false);
                response.put("message", "Please upload a PDF file");
                return ResponseEntity.badRequest().body(response);
            }

            if (!file.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
                response.put("success", false);
                response.put("message", "Only PDF files are supported");
                return ResponseEntity.badRequest().body(response);
            }

            log.info("Received PDF file: {} ({} bytes)",
                    file.getOriginalFilename(), file.getSize());

            // Save uploaded file temporarily
            Path tempFile = Files.createTempFile("upload_", ".pdf");
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);

            // Extract data
            PdfExtractionResult result = pdfExtractionService.extractData(tempFile.toFile());

            if (!result.isSuccess()) {
                response.put("success", false);
                response.put("message", "Extraction failed: " + result.getErrorMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

            // Export to TXT files
            String baseFileName = file.getOriginalFilename()
                    .replace(".pdf", "")
                    .replaceAll("[^a-zA-Z0-9_-]", "_");

            fileExportService.exportAll(result, baseFileName);

            // Clean up temp file
            Files.deleteIfExists(tempFile);

            // Prepare response
            response.put("success", true);
            response.put("message", "Data extracted and exported successfully");
            response.put("mudPropertiesCount", result.getMudProperties().size());
            response.put("remarkExtracted", result.getRemark() != null);
            response.put("lossCount", result.getLosses().size());
            response.put("volumeTrackCount", result.getVolumeTracks().size());
            response.put("outputDirectory", fileExportService.getOutputDirectory());
            response.put("extractionTimestamp", result.getExtractionTimestamp());

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("Error processing PDF: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error processing PDF: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Extract data from PDF and return JSON response only (no file export)
     */
    @PostMapping("/extract-json")
    public ResponseEntity<PdfExtractionResult> extractPdfJson(
            @RequestParam("file") MultipartFile file) {

        try {
            if (file.isEmpty() || !file.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
                return ResponseEntity.badRequest().build();
            }

            // Save uploaded file temporarily
            Path tempFile = Files.createTempFile("upload_", ".pdf");
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);

            // Extract data
            PdfExtractionResult result = pdfExtractionService.extractData(tempFile.toFile());

            // Clean up
            Files.deleteIfExists(tempFile);

            if (result.isSuccess()) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
            }

        } catch (IOException e) {
            log.error("Error processing PDF: {}", e.getMessage(), e);
            PdfExtractionResult errorResult = new PdfExtractionResult(file.getOriginalFilename());
            errorResult.setSuccess(false);
            errorResult.setErrorMessage(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
        }
    }
}
