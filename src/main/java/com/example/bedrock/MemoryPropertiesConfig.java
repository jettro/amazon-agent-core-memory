package com.example.bedrock;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "memory")
public record MemoryPropertiesConfig(
        String identifier
) {
}
