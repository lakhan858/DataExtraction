package com.example.dataExtractionTool.service;

import com.example.dataExtractionTool.model.InventoryItem;
import com.example.dataExtractionTool.model.MudProperty;
import com.example.dataExtractionTool.model.PdfExtractionResult;
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
    private static final String INVENTORY_FILENAME = "inventory.txt";

    /**
     * Export both MUD PROPERTIES and INVENTORY tables to separate TXT files
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
        String inventoryFile = String.format("%s_%s_%s", baseFileName, timestamp, INVENTORY_FILENAME);

        // Export MUD PROPERTIES
        exportMudProperties(result.getMudProperties(),
                Paths.get(outputDirectory, mudPropertiesFile).toString());

        // Export INVENTORY
        exportInventory(result.getInventoryItems(),
                Paths.get(outputDirectory, inventoryFile).toString());

        // Export Raw Text for debugging
        if (result.getRawText() != null) {
            String rawTextFile = String.format("%s_%s_raw_text.txt", baseFileName, timestamp);
            exportRawText(result.getRawText(),
                    Paths.get(outputDirectory, rawTextFile).toString());
        }

        log.info("Successfully exported data to {}, {}, and raw text file", mudPropertiesFile, inventoryFile);
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
            // Write header
            writer.write("Property Name~Sample 1~Sample 2~Sample 3~Sample 4~Unit");
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
     * Export INVENTORY to a TXT file with tilde separator
     */
    public void exportInventory(List<InventoryItem> items, String outputPath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            // Write header
            writer.write("Product~Initial~Received~Final~Used~Cumulative~Cost");
            writer.newLine();

            // Write data rows
            for (InventoryItem item : items) {
                writer.write(item.toTildeSeparated());
                writer.newLine();
            }

            log.info("Exported {} INVENTORY items to: {}", items.size(), outputPath);
        }
    }

    /**
     * Get the configured output directory
     */
    public String getOutputDirectory() {
        return outputDirectory;
    }
}
