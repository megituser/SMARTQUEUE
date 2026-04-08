package com.smartqueue.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        if (!StringUtils.hasText(jwtSecret)) {
            // Defensive: Fail-fast early during Spring boot up if env vars are missing
            throw new IllegalStateException("JWT Secret is not configured. Security context cannot be initialized.");
        }

        try {
            byte[] keyBytes = Base64.getDecoder().decode(jwtSecret);
            this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        } catch (IllegalArgumentException e) {
            log.error("Failed to decode JWT secret. Ensure it is a valid Base64 encoded string.", e);
            throw new IllegalStateException("Misconfigured JWT secret in properties", e);
        }
    }

    public String generateAccessToken(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails)) {
            throw new IllegalArgumentException("Cannot generate token from malformed authentication context");
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return generateAccessToken(userDetails.getUsername());
    }

    public String generateAccessToken(String email) {
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("Cannot generate a JWT token for a blank email");
        }

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .subject(email)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey)
                .compact();
    }

    public String getEmailFromToken(String token) {
        // We assume token structure validity has already been checked by
        // validateToken()
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getSubject();
    }

    public boolean validateToken(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }

        try {
            Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token);
            return true;

        } catch (ExpiredJwtException ex) {
            // This is an expected, normal application flow. DO NOT use log.error()
            // otherwise your monitoring tools (Datadog/Sentry) will trigger Sev-1 alerts
            // every time a user leaves their tab open overnight.
            log.debug("JWT token naturally expired: {}", ex.getMessage());

        } catch (MalformedJwtException ex) {
            log.debug("Invalid JWT token signature/structure detected: {}", ex.getMessage());

        } catch (UnsupportedJwtException ex) {
            log.warn("Unsupported JWT token format intercepted: {}", ex.getMessage());

        } catch (IllegalArgumentException ex) {
            log.debug("JWT claims string is completely empty: {}", ex.getMessage());

        } catch (Exception ex) {
            // Defensive catch-all for unknown cryptographic failures that JJWT might throw
            log.warn("JWT validation failed unexpectedly: {}", ex.getMessage());
        }

        return false;
    }

    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    public long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }
}
