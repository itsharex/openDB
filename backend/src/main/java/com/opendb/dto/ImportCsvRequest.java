package com.opendb.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ImportCsvRequest {

    @NotBlank
    private String database;

    @NotBlank
    private String table;

    @NotBlank
    private String csvContent;

    private boolean hasHeader = true;
}
