package com.example.dataExtractionTool.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model class representing a single row from the INVENTORY table
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItem {

    private String product;
    private String initial;
    private String received;
    private String finalValue;
    private String used;
    private String cumulative;
    private String cost;

    /**
     * Convert to tilde-separated string format for file export
     */
    public String toTildeSeparated() {
        return String.join("~",
                product != null ? product : "",
                initial != null ? initial : "",
                received != null ? received : "",
                finalValue != null ? finalValue : "",
                used != null ? used : "",
                cumulative != null ? cumulative : "",
                cost != null ? cost : "");
    }
}
