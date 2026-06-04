package com.opendb.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class TableMetaResponse {

    private String table;
    private String database;
    private List<String> primaryKeys;
    private List<ColumnInfo> columns;
}
