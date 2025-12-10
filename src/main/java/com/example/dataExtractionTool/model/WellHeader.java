package com.example.dataExtractionTool.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model class for well header information extracted from PDF
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WellHeader {

    // Well identification
    private String wellName;

    // Report metadata
    private String reportNo;
    private String reportDate;
    private String reportTime;
    private String spudDate;

    // Rig and Activity
    private String rig;
    private String activity;

    // Drilling data
    private String md; // Measured Depth (ft)
    private String tvd; // True Vertical Depth (ft)
    private String inc; // Inclination (deg)
    private String azi; // Azimuth (deg)

    // API Well Number
    private String apiWellNo;

    /**
     * Convert to tilde-separated format for export
     */
    public String toTildeSeparated() {
        return String.format("%s~%s~%s~%s~%s~%s~%s~%s~%s",
                wellName != null ? wellName : "",
                reportNo != null ? reportNo : "",
                reportDate != null ? reportDate : "",
                reportTime != null ? reportTime : "",
                spudDate != null ? spudDate : "",
                md != null ? md : "",
                tvd != null ? tvd : "",
                inc != null ? inc : "",
                azi != null ? azi : "");
    }
}
