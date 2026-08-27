package org.example.aispingboot.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix="jwt")
public class JwtConfig {
    private String secret;
    private long expiration;
    private long refreshExpiration;
    private String header;
    private String tokenPrefix;
}
