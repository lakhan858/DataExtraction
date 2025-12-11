package com.example.dataExtractionTool.service;

import com.example.dataExtractionTool.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Service for exporting extracted PDF data to text files
 */
@Slf4j
@Service
public class FileExportService {

    @Value("${pdf.export.output.directory:./output}")
    private String outputDirectory;

    private static final String WELL_HEADER_FILENAME = "well_header.txt";
    private static final String MUD_PROPERTIES_FILENAME = "mud_properties.txt";
    private static final String REMARKS_FILENAME = "remarks.txt";
    private static final String LOSS_FILENAME = "loss.txt";
    private static final String VOLUME_TRACK_FILENAME = "volume_track.txt";

    /**
     * Export all tables to separate TXT files
     */
    public void exportAll(PdfExtractionResult result, String baseFileName) throws IOException {
        // Create output directory if it doesn't exist
        Path outputPath = Paths.get(outputDirectory);
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
            log.info("Created output directory: {}", outputPath.toAbsolutePath());
        }

        // Generate timestamped filenames
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String wellHeaderFile = String.format("%s_%s_%s", baseFileName, timestamp, WELL_HEADER_FILENAME);
        String mudPropertiesFile = String.format("%s_%s_%s", baseFileName, timestamp, MUD_PROPERTIES_FILENAME);
        String remarksFile = String.format("%s_%s_%s", baseFileName, timestamp, REMARKS_FILENAME);
        String lossFile = String.format("%s_%s_%s", baseFileName, timestamp, LOSS_FILENAME);
        String volumeTrackFile = String.format("%s_%s_%s", baseFileName, timestamp, VOLUME_TRACK_FILENAME);

        // Export WELL HEADER
        exportWellHeader(result.getWellHeader(),
                Paths.get(outputDirectory, wellHeaderFile).toString());

        // Export MUD PROPERTIES
        exportMudProperties(result.getMudProperties(),
                Paths.get(outputDirectory, mudPropertiesFile).toString());

        // Export REMARKS
        exportRemarks(result.getRemark(),
                Paths.get(outputDirectory, remarksFile).toString());

        // Export LOSS
        exportLoss(result.getLosses(),
                Paths.get(outputDirectory, lossFile).toString());

        // Export VOL.TRACK
        exportVolumeTrack(result.getVolumeTracks(),
                Paths.get(outputDirectory, volumeTrackFile).toString());

        // Export Raw Text for debugging
        if (result.getRawText() != null) {
            String rawTextFile = String.format("%s_%s_raw_text.txt", baseFileName, timestamp);
            exportRawText(result.getRawText(),
                    Paths.get(outputDirectory, rawTextFile).toString());
        }

        // Export JSON
        String jsonFile = String.format("%s_%s_all_data.json", baseFileName, timestamp);
        exportJson(result, Paths.get(outputDirectory, jsonFile).toString());

        log.info("Successfully exported data to {}, {}, {}, {}, {}, raw text file, and JSON file",
                wellHeaderFile, mudPropertiesFile, remarksFile, lossFile, volumeTrackFile);
    }

    /**
     * Export raw text to a TXT file
     */
    public void exportRawText(String text, String outputPath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            writer.write(text);
        }
    }

    /**
     * Export WELL HEADER to a TXT file with tilde separator (vertical format)
     */
    public void exportWellHeader(WellHeader header, String outputPath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            // Write each field on a separate line in "Label~Value" format
            writer.write("Well Name/No.~" + (header.getWellName() != null ? header.getWellName() : ""));
            writer.newLine();

            writer.write("Report No.~" + (header.getReportNo() != null ? header.getReportNo() : ""));
            writer.newLine();

            writer.write("Report Date~" + (header.getReportDate() != null ? header.getReportDate() : ""));
            writer.newLine();

            writer.write("Report Time~" + (header.getReportTime() != null ? header.getReportTime() : ""));
            writer.newLine();

            writer.write("Spud Date~" + (header.getSpudDate() != null ? header.getSpudDate() : ""));
            writer.newLine();

            writer.write("Rig~" + (header.getRig() != null ? header.getRig() : ""));
            writer.newLine();

            writer.write("Activity~" + (header.getActivity() != null ? header.getActivity() : ""));
            writer.newLine();

            writer.write("MD(ft)~" + (header.getMd() != null ? header.getMd() : ""));
            writer.newLine();

            writer.write("TVD(ft)~" + (header.getTvd() != null ? header.getTvd() : ""));
            writer.newLine();

            writer.write("Inc (deg)~" + (header.getInc() != null ? header.getInc() : ""));
            writer.newLine();

            writer.write("AZI (deg)~" + (header.getAzi() != null ? header.getAzi() : ""));
            writer.newLine();

            writer.write("API well No.~" + (header.getApiWellNo() != null ? header.getApiWellNo() : ""));
            writer.newLine();

            log.info("Exported WELL HEADER to: {}", outputPath);
        }
    }

    /**
     * Export MUD PROPERTIES to a TXT file with tilde separator
     */
    public void exportMudProperties(List<MudProperty> properties, String outputPath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            // Write header (removed Unit column)
            writer.write("Property Name~Sample 1~Sample 2~Sample 3~Sample 4");
            writer.newLine();

            // Write data rows
            for (MudProperty property : properties) {
                writer.write(property.toTildeSeparated());
                writer.newLine();
            }

            log.info("Exported {} MUD PROPERTIES to: {}", properties.size(), outputPath);
        }
    }

    /**
     * Export REMARKS to a TXT file with custom formatting (Paragraph + Key-Value
     * pairs)
     */
    public void exportRemarks(Remark remark, String outputPath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            // Write Remark Text (Narrative) first
            if (remark.getRemarkText() != null && !remark.getRemarkText().isEmpty()) {
                writer.write(remark.getRemarkText());
                writer.newLine();
                writer.newLine(); // Add spacing between text and data
            }

            // Write OBM
            writer.write("OBM on Location/Lease (bbl)~"
                    + (remark.getObmOnLocationLease() != null ? remark.getObmOnLocationLease() : ""));
            writer.newLine();

            // Write WBM
            writer.write("WBM Tanks (bbl)~" + (remark.getWbmTanks() != null ? remark.getWbmTanks() : ""));
            writer.newLine();

            log.info("Exported REMARKS to: {}", outputPath);
        }
    }

    /**
     * Export LOSS to a TXT file with tilde separator
     */
    public void exportLoss(List<Loss> losses, String outputPath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            // Write header
            writer.write("Category~Value (bbl)");
            writer.newLine();

            // Write data rows
            for (Loss loss : losses) {
                writer.write(loss.toTildeSeparated());
                writer.newLine();
            }

            log.info("Exported {} LOSS entries to: {}", losses.size(), outputPath);
        }
    }

    /**
     * Export VOL.TRACK to a TXT file with tilde separator
     */
    public void exportVolumeTrack(List<VolumeTrack> volumeTracks, String outputPath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            // Write header
            writer.write("Category~Value (bbl)");
            writer.newLine();

            // Write data rows
            for (VolumeTrack volumeTrack : volumeTracks) {
                writer.write(volumeTrack.toTildeSeparated());
                writer.newLine();
            }

            log.info("Exported {} VOL.TRACK entries to: {}", volumeTracks.size(), outputPath);
        }
    }

    /**
     * Get the configured output directory
     */
    public String getOutputDirectory() {
        return outputDirectory;
    }

    /**
     * Export all data to a single JSON file
     */
    /**
     * Export all data to a single JSON file
     */
    public void exportJson(PdfExtractionResult result, String outputPath) throws IOException {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
        // Configure to not fail on empty beans if necessary
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS);

        List<Map<String, Object>> unifiedData = transformToUnifiedFormat(result);

        mapper.writeValue(new java.io.File(outputPath), unifiedData);
        log.info("Exported JSON data to: {}", outputPath);
    }

    /**
     * Transform PdfExtractionResult into a unified flat format (List of objects per
     * sample)
     */
    public List<Map<String, Object>> transformToUnifiedFormat(PdfExtractionResult result) {
        List<Map<String, Object>> outputList = new java.util.ArrayList<>();

        // Base fields from WellHeader and Remarks
        java.util.LinkedHashMap<String, Object> baseMap = new java.util.LinkedHashMap<>();

        // Flatten WellHeader fields using camelCase
        if (result.getWellHeader() != null) {
            WellHeader wh = result.getWellHeader();
            addIfPresent(baseMap, "wellName", wh.getWellName());
            addIfPresent(baseMap, "reportNo", wh.getReportNo());
            addIfPresent(baseMap, "reportDate", wh.getReportDate());
            addIfPresent(baseMap, "reportTime", wh.getReportTime());
            addIfPresent(baseMap, "spudDate", wh.getSpudDate());
            addIfPresent(baseMap, "rig", wh.getRig());
            addIfPresent(baseMap, "activity", wh.getActivity());
            addIfPresent(baseMap, "md", wh.getMd());
            addIfPresent(baseMap, "tvd", wh.getTvd());
            addIfPresent(baseMap, "inc", wh.getInc());
            addIfPresent(baseMap, "azi", wh.getAzi());
            addIfPresent(baseMap, "apiWellNo", wh.getApiWellNo());

            // Calculate and add systemDefaultTimeZone (Process Date & Time to Epoch)
            if (wh.getReportDate() != null && wh.getReportTime() != null) {
                try {
                    // formats: 10/24/2025 and 16:00
                    java.time.LocalDate date = java.time.LocalDate.parse(wh.getReportDate().trim(),
                            java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy"));
                    java.time.LocalTime time = java.time.LocalTime.parse(wh.getReportTime().trim(),
                            java.time.format.DateTimeFormatter.ofPattern("HH:mm"));

                    java.time.LocalDateTime ldt = java.time.LocalDateTime.of(date, time);
                    long epoch = ldt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();

                    baseMap.put("systemDefaultTimeZone", epoch);
                } catch (Exception e) {
                    log.warn("Could not parse date/time for systemDefaultTimeZone: {} {}", wh.getReportDate(),
                            wh.getReportTime());
                }
            }
        }

        if (result.getRemark() != null) {
            addIfPresent(baseMap, "remarks", result.getRemark().getRemarkText());
        }

        // Add defaults for missing fields as per IDL
        baseMap.putIfAbsent("companyName", "NA");
        baseMap.putIfAbsent("fluidName", "NA");
        baseMap.putIfAbsent("phase", "NA");

        // Iterate samples 1 to 4
        boolean foundAnySample = false;
        for (int i = 1; i <= 4; i++) {
            java.util.LinkedHashMap<String, Object> sampleMap = new java.util.LinkedHashMap<>(baseMap);
            boolean hasData = false;

            for (MudProperty prop : result.getMudProperties()) {
                String val = getSampleValue(prop, i);
                if (val != null && !val.trim().isEmpty()) {
                    hasData = true;
                    String key = mapKey(prop.getPropertyName());
                    Object typedVal = parseValue(val);
                    sampleMap.put(key, typedVal);
                }
            }

            if (hasData) {
                foundAnySample = true;
                outputList.add(sampleMap);
            }
        }

        // If no samples found (empty table?), just return the base headers
        if (!foundAnySample) {
            outputList.add(baseMap);
        }

        return outputList;
    }

    private String getSampleValue(MudProperty prop, int sampleIdx) {
        switch (sampleIdx) {
            case 1:
                return prop.getSample1();
            case 2:
                return prop.getSample2();
            case 3:
                return prop.getSample3();
            case 4:
                return prop.getSample4();
            default:
                return null;
        }
    }

    private void addIfPresent(java.util.Map<String, Object> map, String key, String value) {
        if (value != null && !value.isEmpty()) {
            map.put(key, parseValue(value));
        }
    }

    // Mapping of Field Labels to Desired JSON Keys
    private static final java.util.Map<String, String> FIELD_MAPPINGS = new java.util.HashMap<>();
    static {
        FIELD_MAPPINGS.put("HTHP filtrate (ml/30min)", "hthpWaterLoss");
        FIELD_MAPPINGS.put("HTHP filtrate (ml/30min)", "apiWaterLoss");
        FIELD_MAPPINGS.put("Chlorides whole mud (mg/L)", "Chlorides");
        FIELD_MAPPINGS.put("Chlorides (mg/L)", "chlorides");
        FIELD_MAPPINGS.put("Funnel visc. (sec/qt)", "funnelViscosity");
        FIELD_MAPPINGS.put("Solids adjusted for salt (%)", "lowGravitySolids");
        FIELD_MAPPINGS.put("MW (ppg)", "mudWeight");
        FIELD_MAPPINGS.put("Alkalinity mud (pom) (cc/cc)", "phValue");
        FIELD_MAPPINGS.put("Oil (%)", "percentOil");
        FIELD_MAPPINGS.put("Water (%)", "percentWater");
        FIELD_MAPPINGS.put("PV (cP)", "plasticViscosity");
        FIELD_MAPPINGS.put("YP (lbf/100ft2)", "yieldPoint");
        FIELD_MAPPINGS.put("Gel str. (10sec) (lbf/100ft2)", "gels10Sec");
        FIELD_MAPPINGS.put("Gel str. (10min) (lbf/100ft2)", "gels10Min");
        FIELD_MAPPINGS.put("Gel str. (30min) (lbf/100ft2)", "gels30Min");
        FIELD_MAPPINGS.put("Depth (ft)", "depth");
    }

    private String mapKey(String propertyName) {
        if (propertyName == null)
            return "unknown";
        String trimName = propertyName.trim();

        // Check exact mapping
        if (FIELD_MAPPINGS.containsKey(trimName)) {
            return FIELD_MAPPINGS.get(trimName);
        }

        // Check partial match for Gel strength if exact failed
        if (trimName.startsWith("Gel str. (10sec)"))
            return "gels10Sec";
        if (trimName.startsWith("Gel str. (10min)"))
            return "gels10Min";
        if (trimName.startsWith("Gel str. (30min)"))
            return "gels30Min";

        // Fallback: Convert to camelCase
        return toCamelCase(trimName);
    }

    private String toCamelCase(String input) {
        StringBuilder result = new StringBuilder();
        boolean nextUpper = false;
        boolean first = true;

        for (char c : input.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                if (first) {
                    result.append(Character.toLowerCase(c));
                    first = false;
                } else if (nextUpper) {
                    result.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    result.append(c);
                }
            } else {
                nextUpper = true;
            }
        }
        return result.toString();
    }

    private Object parseValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "NA";
        }
        try {
            // Try parsing as double
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            // Return valid string
            return value.trim();
        }
    }
}
