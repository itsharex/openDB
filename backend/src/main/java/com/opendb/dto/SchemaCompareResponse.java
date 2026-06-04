package com.opendb.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SchemaCompareResponse {

    private String sourceDatabase;
    private String targetDatabase;
    private List<String> onlyInSource;
    private List<String> onlyInTarget;
    private List<String> modifiedTables;
    private String syncScript;
}
