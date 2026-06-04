package com.opendb.model;

import com.opendb.dto.ConnectionProfileRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class ConnectionProfile {

    private String id;
    private ConnectionProfileRequest config;
    private long updatedAt;

    public static ConnectionProfile fromRequest(ConnectionProfileRequest request) {
        ConnectionProfile profile = new ConnectionProfile();
        profile.setId(UUID.randomUUID().toString());
        profile.setConfig(request);
        profile.setUpdatedAt(System.currentTimeMillis());
        return profile;
    }
}
