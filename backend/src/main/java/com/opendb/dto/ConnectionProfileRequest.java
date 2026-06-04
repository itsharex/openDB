package com.opendb.dto;

import com.opendb.model.DatabaseType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConnectionProfileRequest {

    @NotNull
    private DatabaseType type = DatabaseType.MYSQL;

    @NotBlank
    private String name;

    @NotBlank
    private String host;

    @Min(1)
    @Max(65535)
    private int port = 3306;

    @NotBlank
    private String username;

    private String password;

    private String database;
}
