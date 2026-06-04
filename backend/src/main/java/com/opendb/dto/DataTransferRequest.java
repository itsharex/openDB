package com.opendb.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DataTransferRequest {

    @NotBlank
    private String sourceDatabase;

    @NotBlank
    private String sourceTable;

    @NotBlank
    private String targetDatabase;

    @NotBlank
    private String targetTable;

    private boolean truncateTarget;
}
