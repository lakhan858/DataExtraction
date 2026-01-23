package com.example.dataExtractionTool.service;

import com.example.dataExtractionTool.model.MudProperty;
import com.example.dataExtractionTool.model.PdfExtractionResult;
import com.example.dataExtractionTool.model.WellHeader;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MudReportMappingServiceTest {

    @Test
    void prefersLatestSample4WhenPresent() {
        PdfExtractionResult result = new PdfExtractionResult("test.pdf");
        result.setWellHeader(new WellHeader());
        result.setMudProperties(Collections.singletonList(
                MudProperty.builder()
                        .propertyName("MW (ppg)")
                        .sample1("8.5")
                        .sample2("9.0")
                        .sample3("9.5")
                        .sample4("10.0")
                        .build()
        ));

        MudReportMappingService service = new MudReportMappingService();
        List<?> dtos = service.transformToMudReportDTOs(result);

        assertEquals(1, dtos.size());
        assertEquals(10.0f, ((com.example.dataExtractionTool.dto.MudReportDTO) dtos.get(0)).getMudWeight());
    }

    @Test
    void prefersSample3WhenSample4Empty() {
        PdfExtractionResult result = new PdfExtractionResult("test.pdf");
        result.setWellHeader(new WellHeader());
        result.setMudProperties(Collections.singletonList(
                MudProperty.builder()
                        .propertyName("MW (ppg)")
                        .sample1("8.5")
                        .sample2("9.0")
                        .sample3("9.5")
                        .sample4("")
                        .build()
        ));

        MudReportMappingService service = new MudReportMappingService();
        List<?> dtos = service.transformToMudReportDTOs(result);

        assertEquals(1, dtos.size());
        assertEquals(9.5f, ((com.example.dataExtractionTool.dto.MudReportDTO) dtos.get(0)).getMudWeight());
    }

    @Test
    void fallsBackToSample1WhenOnlySample1HasData() {
        PdfExtractionResult result = new PdfExtractionResult("test.pdf");
        result.setWellHeader(new WellHeader());
        result.setMudProperties(Collections.singletonList(
                MudProperty.builder()
                        .propertyName("MW (ppg)")
                        .sample1("8.5")
                        .sample2("")
                        .sample3(null)
                        .sample4("   ")
                        .build()
        ));

        MudReportMappingService service = new MudReportMappingService();
        List<?> dtos = service.transformToMudReportDTOs(result);

        assertEquals(1, dtos.size());
        assertEquals(8.5f, ((com.example.dataExtractionTool.dto.MudReportDTO) dtos.get(0)).getMudWeight());
    }

    @Test
    void whenAllSamplesEmpty_returnsHeaderOnlyDtoWithNullMudProperties() {
        PdfExtractionResult result = new PdfExtractionResult("test.pdf");
        WellHeader header = new WellHeader();
        header.setReportDate("01/01/2026");
        header.setReportTime("10:00");
        result.setWellHeader(header);
        result.setMudProperties(Collections.singletonList(
                MudProperty.builder()
                        .propertyName("MW (ppg)")
                        .sample1("")
                        .sample2(" ")
                        .sample3(null)
                        .sample4("")
                        .build()
        ));

        MudReportMappingService service = new MudReportMappingService();
        List<?> dtos = service.transformToMudReportDTOs(result);

        assertEquals(1, dtos.size());
        com.example.dataExtractionTool.dto.MudReportDTO dto =
                (com.example.dataExtractionTool.dto.MudReportDTO) dtos.get(0);

        // Header info should still be mapped
        assertNotNull(dto.getReportDate());
        // Mud fields should remain null because we couldn't pick a sample
        assertNull(dto.getMudWeight());
    }
}

