package com.opendb.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TableInfo {

    private String name;
    private String engine;
    private long rows;
    private String comment;
}
