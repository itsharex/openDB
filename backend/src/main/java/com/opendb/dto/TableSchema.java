package com.opendb.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TableSchema {

    private String name;
    private List<ColumnInfo> columns;
}
