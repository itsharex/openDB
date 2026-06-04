package com.opendb.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DataCompareResponse {

    private String sourceDatabase;
    private String sourceTable;
    private String targetDatabase;
    private String targetTable;
    private long sourceRows;
    private long targetRows;
    private String summary;
}
