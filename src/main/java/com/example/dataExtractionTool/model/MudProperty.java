package com.example.dataExtractionTool.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model class representing a single row from the MUD PROPERTIES table
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MudProperty {

    private String propertyName;
    private String sample1;
    private String sample2;
    private String sample3;
    private String sample4;
    private String unit;

    /**
     * Convert to tilde-separated string format for file export
     */
    public String toTildeSeparated() {
        return String.join("~",
                propertyName != null ? propertyName : "",
                sample1 != null ? sample1 : "",
                sample2 != null ? sample2 : "",
                sample3 != null ? sample3 : "",
                sample4 != null ? sample4 : "",
                unit != null ? unit : "");
    }
}
