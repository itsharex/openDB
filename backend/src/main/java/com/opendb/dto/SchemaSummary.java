package com.opendb.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SchemaSummary {

    private String database;
    private List<TableSchema> tables;
}
