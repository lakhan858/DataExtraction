package com.example.dataExtractionTool.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Mud Report data with UOM attributes
 * This DTO represents the complete mud report structure for a single sample
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MudReportDTO {

    // UOM Attributes
    private Float mudWeight;

    private Float depth;

    private Float apiWaterLoss;

    private String companyName;

    private String checkDate;

    private String fluidName;

    private Float gels10Min;

    private Float gels10Sec;

    private Float gels30Min;

    private Float percentWater;

    private Float percentOil;

    private Float plasticViscosity;

    private Float viscosityFunnel;

    private Float yieldPoint;

    private Float percentHighGravitySolids;

    private Float percentLowGravitySolids;

    private Float phValue;

    private Float chloridesConc;

    private Float electroStaticStability;

    private Float hthpWaterLoss;

    private String phase;

    private Float filterCakeHthp;

    private Float filterCakeLtlp;

    private Long reportDate;

    private String remarks;

    private String filePath;

    private Float days;

}
