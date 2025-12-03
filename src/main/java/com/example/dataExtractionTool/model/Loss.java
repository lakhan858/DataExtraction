package com.example.dataExtractionTool.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model class representing a single category-value pair from the LOSS(bbl)
 * table
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Loss {

    private String category; // e.g., "Cuttings/retention", "Seepage", "Dump", etc.
    private String value; // The barrel value

    /**
     * Convert to tilde-separated string format for file export
     */
    public String toTildeSeparated() {
        return String.join("~",
                category != null ? category : "",
                value != null ? value : "");
    }
}
