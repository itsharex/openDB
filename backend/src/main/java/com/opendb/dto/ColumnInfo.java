package com.opendb.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ColumnInfo {

    private String name;
    private String type;
    private String nullable;
    private String key;
    private String defaultValue;
    private String extra;
}
