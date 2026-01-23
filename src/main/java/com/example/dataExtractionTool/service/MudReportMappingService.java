package com.example.dataExtractionTool.service;

import com.example.dataExtractionTool.dto.MudReportDTO;
import com.example.dataExtractionTool.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for mapping PdfExtractionResult to MudReportDTO structure
 * This service creates a new JSON format based on the DTO structure
 */
@Slf4j
@Service
public class MudReportMappingService {

    private final ObjectMapper objectMapper;

    public MudReportMappingService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    /**
     * Transform PdfExtractionResult to a list of MudReportDTO objects
     * Returns the latest non-empty sample data from each PDF (prefers Sample 4 > 3 > 2 > 1)
     */
    public List<MudReportDTO> transformToMudReportDTOs(PdfExtractionResult result) {
        List<MudReportDTO> dtoList = new ArrayList<>();

        if (result == null || result.getMudProperties() == null || result.getMudProperties().isEmpty()) {
            log.warn("No mud properties found in extraction result");
            return dtoList;
        }

        int sampleIdx = findLatestSampleWithData(result.getMudProperties());
        if (sampleIdx > 0) {
            MudReportDTO dto = createDTOForSample(result, sampleIdx);
            dtoList.add(dto);
            log.info("Created MudReportDTO for latest sample {} from file: {}", sampleIdx, result.getSourceFileName());
        } else {
            log.warn("No sample data found (Samples 1-4 empty), creating a DTO with header information only");
            dtoList.add(createDTOForSample(result, 0)); // Create one with just header info
        }

        return dtoList;
    }

    /**
     * Find the highest sample index (4..1) that contains any mud property data.
     * Returns 0 if all samples are empty.
     */
    private int findLatestSampleWithData(List<MudProperty> mudProperties) {
        for (int sampleIdx = 4; sampleIdx >= 1; sampleIdx--) {
            if (hasSampleData(mudProperties, sampleIdx)) {
                return sampleIdx;
            }
        }
        return 0;
    }

    /**
     * Create a MudReportDTO for a specific sample
     */
    private MudReportDTO createDTOForSample(PdfExtractionResult result, int sampleNumber) {
        MudReportDTO dto = new MudReportDTO();

        // Set sample number
        // dto.setSampleNumber(sampleNumber > 0 ? sampleNumber : null);

        // Map Well Header fields
        if (result.getWellHeader() != null) {
            mapWellHeaderFields(dto, result.getWellHeader());
        }

        // Map Mud Properties for this sample
        if (sampleNumber > 0 && result.getMudProperties() != null) {
            mapMudPropertiesForSample(dto, result.getMudProperties(), sampleNumber);
        }

        // Map Remarks
        if (result.getRemark() != null && result.getRemark().getRemarkText() != null) {
            dto.setRemarks(result.getRemark().getRemarkText());
        }

        // Set file path
        if (result.getSourceFileName() != null) {
            dto.setFilePath(result.getSourceFileName());
        }

        // Set default values for fields not extracted from PDF
        setDefaultValues(dto);

        return dto;
    }

    /**
     * Map Well Header fields to DTO
     */
    private void mapWellHeaderFields(MudReportDTO dto, WellHeader wellHeader) {
        // dto.setWellName(wellHeader.getWellName());
        // dto.setReportNo(wellHeader.getReportNo());
        // dto.setReportTime(wellHeader.getReportTime());
        // dto.setSpudDate(wellHeader.getSpudDate());
        // dto.setRig(wellHeader.getRig());
        // dto.setActivity(wellHeader.getActivity());
        // dto.setMd(wellHeader.getMd());
        // dto.setTvd(wellHeader.getTvd());
        // dto.setInc(wellHeader.getInc());
        // dto.setAzi(wellHeader.getAzi());
        // dto.setApiWellNo(wellHeader.getApiWellNo());

        // Convert reportDate to epoch timestamp
        if (wellHeader.getReportDate() != null && wellHeader.getReportTime() != null) {
            Long epochTime = convertToEpoch(wellHeader.getReportDate(), wellHeader.getReportTime());
            dto.setReportDate(epochTime);
        }

        // Set checkDate from reportDate string
        if (wellHeader.getReportDate() != null) {
            dto.setCheckDate(wellHeader.getReportDate());
        }
    }

    /**
     * Map Mud Properties for a specific sample to DTO
     */
    private void mapMudPropertiesForSample(MudReportDTO dto, List<MudProperty> mudProperties, int sampleNumber) {
        for (MudProperty property : mudProperties) {
            String value = getSampleValue(property, sampleNumber);

            if (value == null || value.trim().isEmpty()) {
                continue;
            }

            String propertyName = property.getPropertyName().trim();

            // Map based on property name
            try {
                if (propertyName.contains("MW") && propertyName.contains("ppg")) {
                    dto.setMudWeight(parseFloat(value));
                } else if (propertyName.contains("Depth") && propertyName.contains("ft")) {
                    dto.setDepth(parseFloat(value));
                } else if (propertyName.contains("Gel str.") && propertyName.contains("10min")) {
                    dto.setGels10Min(parseFloat(value));
                } else if (propertyName.contains("Gel str.") && propertyName.contains("10sec")) {
                    dto.setGels10Sec(parseFloat(value));
                } else if (propertyName.contains("Gel str.") && propertyName.contains("30min")) {
                    dto.setGels30Min(parseFloat(value));
                } else if (propertyName.contains("Water") && propertyName.contains("%")) {
                    dto.setPercentWater(parseFloat(value));
                } else if (propertyName.contains("Oil") && propertyName.contains("%")) {
                    dto.setPercentOil(parseFloat(value));
                } else if (propertyName.contains("PV") && propertyName.contains("cP")) {
                    dto.setPlasticViscosity(parseFloat(value));
                } else if (propertyName.contains("Funnel visc")) {
                    dto.setViscosityFunnel(parseFloat(value));
                } else if (propertyName.contains("YP") && propertyName.contains("lbf")) {
                    dto.setYieldPoint(parseFloat(value));
                } else if (propertyName.contains("High gravity solids")) {
                    dto.setPercentHighGravitySolids(parseFloat(value));
                } else if (propertyName.contains("Low gravity solids") ||
                        (propertyName.contains("Solids") && propertyName.contains("adjusted"))) {
                    dto.setPercentLowGravitySolids(parseFloat(value));
                } else if (propertyName.contains("pH") ||
                        (propertyName.contains("Alkalinity") && propertyName.contains("mud"))) {
                    dto.setPhValue(parseFloat(value));
                } else if (propertyName.contains("Chlorides")) {
                    dto.setChloridesConc(parseFloat(value));
                } else if (propertyName.contains("Electrostatic") || propertyName.contains("ESS") ||
                        propertyName.contains("Electrical")) {
                    dto.setElectroStaticStability(parseFloat(value));
                } else if (propertyName.contains("HTHP") && propertyName.contains("filtrate")) {
                    // Set both apiWaterLoss and hthpWaterLoss from HTHP filtrate
                    Float filtrateValue = parseFloat(value);
                    dto.setApiWaterLoss(filtrateValue);
                    dto.setHthpWaterLoss(filtrateValue);
                } else if (propertyName.contains("Filter cake") && propertyName.contains("HTHP")) {
                    dto.setFilterCakeHthp(parseFloat(value));
                } else if (propertyName.contains("Filter cake") && propertyName.contains("LTLP")) {
                    dto.setFilterCakeLtlp(parseFloat(value));
                }
            } catch (Exception e) {
                log.warn("Error mapping property '{}' with value '{}': {}",
                        propertyName, value, e.getMessage());
            }
        }
    }

    /**
     * Get sample value based on sample index
     */
    private String getSampleValue(MudProperty property, int sampleIdx) {
        switch (sampleIdx) {
            case 1:
                return property.getSample1();
            case 2:
                return property.getSample2();
            case 3:
                return property.getSample3();
            case 4:
                return property.getSample4();
            default:
                return null;
        }
    }

    /**
     * Check if a sample has any data
     */
    private boolean hasSampleData(List<MudProperty> mudProperties, int sampleIdx) {
        for (MudProperty property : mudProperties) {
            String value = getSampleValue(property, sampleIdx);
            if (value != null && !value.trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Set default values for fields not extracted from PDF
     */
    private void setDefaultValues(MudReportDTO dto) {
        if (dto.getCompanyName() == null) {
            dto.setCompanyName("NA");
        }
        if (dto.getFluidName() == null) {
            dto.setFluidName("NA");
        }
        if (dto.getPhase() == null) {
            dto.setPhase("NA");
        }
    }

    /**
     * Parse string to Float, return null if parsing fails
     */
    private Float parseFloat(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            // Remove commas and extra spaces
            String cleaned = value.trim().replace(",", "");
            return Float.parseFloat(cleaned);
        } catch (NumberFormatException e) {
            log.debug("Could not parse '{}' as Float", value);
            return null;
        }
    }

    /**
     * Convert date and time strings to epoch timestamp
     * Handles both single and double digit dates (e.g., "9/26/2025" and
     * "09/26/2025")
     */
    private Long convertToEpoch(String dateStr, String timeStr) {
        try {
            // Try flexible date format first (handles both M/d/yyyy and MM/dd/yyyy)
            DateTimeFormatter flexibleDateFormatter = DateTimeFormatter.ofPattern("[M/d/yyyy][MM/dd/yyyy]");
            LocalDate date = LocalDate.parse(dateStr.trim(), flexibleDateFormatter);

            // Try flexible time format (handles both H:mm and HH:mm)
            DateTimeFormatter flexibleTimeFormatter = DateTimeFormatter.ofPattern("[H:mm][HH:mm]");
            LocalTime time = LocalTime.parse(timeStr.trim(), flexibleTimeFormatter);

            LocalDateTime dateTime = LocalDateTime.of(date, time);
            return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            log.warn("Could not parse date/time: {} {}", dateStr, timeStr, e);
            return null;
        }
    }

    /**
     * Export MudReportDTO list to JSON file
     */
    public void exportToJson(List<MudReportDTO> dtoList, String outputPath) throws IOException {
        objectMapper.writeValue(new File(outputPath), dtoList);
        log.info("Exported {} MudReportDTO objects to: {}", dtoList.size(), outputPath);
    }

    /**
     * Export MudReportDTO list to JSON file with custom filename
     */
    public void exportMudReportJson(PdfExtractionResult result, String outputDirectory, String baseFileName)
            throws IOException {
        List<MudReportDTO> dtoList = transformToMudReportDTOs(result);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String jsonFileName = String.format("%s_%s_mud_report.json", baseFileName, timestamp);
        String fullPath = new File(outputDirectory, jsonFileName).getAbsolutePath();

        exportToJson(dtoList, fullPath);
    }
}
