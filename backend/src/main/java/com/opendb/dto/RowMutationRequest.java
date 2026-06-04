package com.opendb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Map;

@Data
public class RowMutationRequest {

    @NotBlank
    private String database;

    @NotBlank
    private String table;

    @NotEmpty
    private Map<String, Object> data;

    private Map<String, Object> primaryKey;
}
