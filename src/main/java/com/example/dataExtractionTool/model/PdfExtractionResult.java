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
    private Remark remark; // Single remark section (not a list)
    private List<Loss> losses;
    private List<VolumeTrack> volumeTracks;
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
        this.remark = new Remark();
        this.losses = new ArrayList<>();
        this.volumeTracks = new ArrayList<>();
        this.success = false;
    }
}
