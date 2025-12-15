package com.example.dataExtractionTool.controller;

import com.example.dataExtractionTool.model.PdfExtractionResult;
import com.example.dataExtractionTool.service.FileExportService;
import com.example.dataExtractionTool.service.MudReportMappingService;
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
    private final MudReportMappingService mudReportMappingService;

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

            // Set the original filename instead of temp file name
            result.setSourceFileName(file.getOriginalFilename());

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
    public ResponseEntity<Object> extractPdfJson(
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

            // Set the original filename instead of temp file name
            result.setSourceFileName(file.getOriginalFilename());

            // Clean up
            Files.deleteIfExists(tempFile);

            if (result.isSuccess()) {
                // Transform to unified format
                java.util.List<java.util.Map<String, Object>> unifiedResponse = fileExportService
                        .transformToUnifiedFormat(result);
                return ResponseEntity.ok(unifiedResponse);
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

    /**
     * Extract data from PDF and return MudReportDTO JSON format
     * This endpoint returns data mapped to the MudReportDTO structure
     * Supports both single file and multiple files
     */
    @PostMapping("/extract-mud-report")
    public ResponseEntity<Object> extractPdfMudReport(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "files", required = false) MultipartFile[] files) {

        try {
            // Determine if single or multiple files
            MultipartFile[] filesToProcess;

            if (files != null && files.length > 0) {
                // Multiple files provided
                filesToProcess = files;
            } else if (file != null && !file.isEmpty()) {
                // Single file provided
                filesToProcess = new MultipartFile[] { file };
            } else {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Please upload at least one PDF file using 'file' or 'files' parameter.");
                return ResponseEntity.badRequest().body(error);
            }

            log.info("Extracting {} PDF file(s) to MudReportDTO format", filesToProcess.length);

            // Process all PDFs and collect all MudReportDTOs
            java.util.List<com.example.dataExtractionTool.dto.MudReportDTO> allMudReportDTOs = new java.util.ArrayList<>();

            for (MultipartFile pdfFile : filesToProcess) {
                if (pdfFile.isEmpty()) {
                    log.warn("Skipping empty file");
                    continue;
                }

                if (!pdfFile.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
                    log.warn("Skipping non-PDF file: {}", pdfFile.getOriginalFilename());
                    continue;
                }

                log.info("Processing PDF: {}", pdfFile.getOriginalFilename());

                // Save uploaded file temporarily
                Path tempFile = Files.createTempFile("upload_", ".pdf");
                Files.copy(pdfFile.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);

                // Extract data
                PdfExtractionResult result = pdfExtractionService.extractData(tempFile.toFile());

                // Set the original filename instead of temp file name
                result.setSourceFileName(pdfFile.getOriginalFilename());

                // Clean up
                Files.deleteIfExists(tempFile);

                if (result.isSuccess()) {
                    // Transform to MudReportDTO format and add to the list
                    java.util.List<com.example.dataExtractionTool.dto.MudReportDTO> mudReportDTOs = mudReportMappingService
                            .transformToMudReportDTOs(result);
                    allMudReportDTOs.addAll(mudReportDTOs);
                    log.info("Successfully extracted {} records from {}", mudReportDTOs.size(),
                            pdfFile.getOriginalFilename());
                } else {
                    log.error("Extraction failed for {}: {}", pdfFile.getOriginalFilename(), result.getErrorMessage());
                }
            }

            if (allMudReportDTOs.isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "No data could be extracted from the provided PDF file(s).");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
            }

            // Return response with mudDataList format
            Map<String, Object> response = new HashMap<>();
            response.put("mudDataList", allMudReportDTOs);
            response.put("totalRecords", allMudReportDTOs.size());
            response.put("filesProcessed", filesToProcess.length);

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("Error processing PDF: {}", e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error processing PDF: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Extract data from multiple PDFs and return consolidated MudReportDTO JSON
     * format
     * This endpoint accepts multiple PDF files and returns all mud data in a single
     * mudDataList array
     */
    @PostMapping("/extract-mud-report-batch")
    public ResponseEntity<Object> extractMultiplePdfsMudReport(
            @RequestParam("files") MultipartFile[] files) {

        try {
            if (files == null || files.length == 0) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Please upload at least one PDF file.");
                return ResponseEntity.badRequest().body(error);
            }

            log.info("Extracting {} PDF files to MudReportDTO format", files.length);

            // Process all PDFs and collect all MudReportDTOs
            java.util.List<com.example.dataExtractionTool.dto.MudReportDTO> allMudReportDTOs = new java.util.ArrayList<>();

            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    log.warn("Skipping empty file");
                    continue;
                }

                if (!file.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
                    log.warn("Skipping non-PDF file: {}", file.getOriginalFilename());
                    continue;
                }

                log.info("Processing PDF: {}", file.getOriginalFilename());

                // Save uploaded file temporarily
                Path tempFile = Files.createTempFile("upload_", ".pdf");
                Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);

                // Extract data
                PdfExtractionResult result = pdfExtractionService.extractData(tempFile.toFile());

                // Set the original filename instead of temp file name
                result.setSourceFileName(file.getOriginalFilename());

                // Clean up
                Files.deleteIfExists(tempFile);

                if (result.isSuccess()) {
                    // Transform to MudReportDTO format and add to the list
                    java.util.List<com.example.dataExtractionTool.dto.MudReportDTO> mudReportDTOs = mudReportMappingService
                            .transformToMudReportDTOs(result);
                    allMudReportDTOs.addAll(mudReportDTOs);
                    log.info("Successfully extracted {} records from {}", mudReportDTOs.size(),
                            file.getOriginalFilename());
                } else {
                    log.error("Extraction failed for {}: {}", file.getOriginalFilename(), result.getErrorMessage());
                }
            }

            if (allMudReportDTOs.isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "No data could be extracted from the provided PDF files.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
            }

            // Create response with mudDataList
            Map<String, Object> response = new HashMap<>();
            response.put("mudDataList", allMudReportDTOs);
            response.put("totalRecords", allMudReportDTOs.size());
            response.put("filesProcessed", files.length);

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("Error processing PDFs: {}", e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error processing PDFs: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
