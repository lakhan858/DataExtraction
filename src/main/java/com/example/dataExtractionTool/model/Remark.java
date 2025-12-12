package com.example.dataExtractionTool.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model class representing the REMARKS section
 * Contains narrative text and summary fields
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Remark {

    private String remarkText;


    /**
     * Convert to tilde-separated string format for file export
     */
    public String toTildeSeparated() {
        return String.join("~",
                remarkText != null ? remarkText : "");
    }
}
