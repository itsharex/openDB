package com.opendb.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DatabaseObjectInfo {

    private String name;
    private String type;
    private String comment;
}
