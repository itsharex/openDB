package com.opendb.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateTableRequest {

    @NotBlank
    private String ddl;
}
