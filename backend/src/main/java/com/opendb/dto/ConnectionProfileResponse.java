package com.opendb.dto;

import com.opendb.model.DatabaseType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConnectionProfileResponse {

    private String id;
    private String name;
    private DatabaseType type;
    private String host;
    private int port;
    private String username;
    private String database;
    private long updatedAt;
}
