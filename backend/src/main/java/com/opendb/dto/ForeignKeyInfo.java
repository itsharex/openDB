package com.opendb.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ForeignKeyInfo {

    private String fromTable;
    private String fromColumn;
    private String toTable;
    private String toColumn;
    private String constraintName;
}
