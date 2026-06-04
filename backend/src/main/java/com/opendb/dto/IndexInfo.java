package com.opendb.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IndexInfo {

    private String name;
    private String columnName;
    private boolean unique;
    private String type;
}
