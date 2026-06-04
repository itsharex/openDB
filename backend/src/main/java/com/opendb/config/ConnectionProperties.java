package com.opendb.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "opendb.connection")
public class ConnectionProperties {

    private int maxPoolSize = 5;
    private int queryTimeoutSeconds = 30;
}
