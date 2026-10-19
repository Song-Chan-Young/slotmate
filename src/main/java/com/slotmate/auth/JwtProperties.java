package com.slotmate.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** slotmate.jwt.* — secret 은 .env 의 JWT_SECRET, expiresIn 은 초 */
@ConfigurationProperties(prefix = "slotmate.jwt")
public record JwtProperties(String secret, long expiresIn) {
}
