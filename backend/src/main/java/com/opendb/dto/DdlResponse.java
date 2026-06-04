package com.opendb.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DdlResponse {

    private String ddl;
    private String objectType;
    private String objectName;
}
