package com.opendb.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateDatabaseRequest {

    @NotBlank
    private String name;

    private String charset = "utf8mb4";

    private String collation = "utf8mb4_unicode_ci";
}
