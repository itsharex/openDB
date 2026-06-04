package com.opendb.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SqlExecuteRequest {

    @NotBlank
    private String sql;

    private String database;

    @Min(1)
    @Max(10000)
    private int limit = 200;
}
