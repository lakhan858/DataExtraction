package com.example.dataExtractionTool.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper class containing the complete extraction result from a PDF
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfExtractionResult {

    private List<MudProperty> mudProperties;
    private List<InventoryItem> inventoryItems;
    private String sourceFileName;
    private String extractionTimestamp;
    private boolean success;
    private String errorMessage;
    private String rawText; // Added field for debugging

    /**
     * Initialize with empty lists
     */
    public PdfExtractionResult(String sourceFileName) {
        this.sourceFileName = sourceFileName;
        this.mudProperties = new ArrayList<>();
        this.inventoryItems = new ArrayList<>();
        this.success = false;
    }
}
