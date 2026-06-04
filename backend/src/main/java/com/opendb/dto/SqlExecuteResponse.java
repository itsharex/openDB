package com.opendb.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder(toBuilder = true)
public class SqlExecuteResponse {

    private String type;
    private List<String> columns;
    private List<Map<String, Object>> rows;
    private int rowCount;
    private long executionTimeMs;
    private String message;
    private boolean editable;
    private String sourceDatabase;
    private String sourceTable;
    private List<String> primaryKeys;
    private String editableReason;
}
