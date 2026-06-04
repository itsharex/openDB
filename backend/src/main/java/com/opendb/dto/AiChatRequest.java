package com.opendb.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiChatRequest {

    @NotBlank
    private String prompt;

    private String database;

    private String contextSql;
}
