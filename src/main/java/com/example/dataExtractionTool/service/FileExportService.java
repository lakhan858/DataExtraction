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

/**
 * Service for exporting extracted PDF data to text files
 */
@Slf4j
@Service
public class FileExportService {

    @Value("${pdf.export.output.directory:./output}")
    private String outputDirectory;

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
        String mudPropertiesFile = String.format("%s_%s_%s", baseFileName, timestamp, MUD_PROPERTIES_FILENAME);
        String remarksFile = String.format("%s_%s_%s", baseFileName, timestamp, REMARKS_FILENAME);
        String lossFile = String.format("%s_%s_%s", baseFileName, timestamp, LOSS_FILENAME);
        String volumeTrackFile = String.format("%s_%s_%s", baseFileName, timestamp, VOLUME_TRACK_FILENAME);

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

        log.info("Successfully exported data to {}, {}, {}, {}, and raw text file",
                mudPropertiesFile, remarksFile, lossFile, volumeTrackFile);
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
     * Export REMARKS to a TXT file with tilde separator
     */
    public void exportRemarks(Remark remark, String outputPath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            // Write header
            writer.write("Remark Text~OBM on Location/Lease (bbl)~WBM Tanks (bbl)");
            writer.newLine();

            // Write data
            writer.write(remark.toTildeSeparated());
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
}
