package com.comp5348.store.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT Utility Class
 * Provides methods for generating, signing, and parsing JWT.
 */
public class JwtUtil {

    //Token validity period
    public static final Long JWT_TTL = 60 * 60 *1000L;// 60 * 60 *1000 equals one hour

    //Secret key used for signing
    public static final String JWT_KEY = signingKey();

    private static String signingKey() {
        String key = System.getenv("JWT_SECRET");
        if (key == null || key.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalStateException("Set JWT_SECRET to at least 32 bytes before logging in");
        return key;
    }

    /**
     * Generates a random UUID string without hyphens.
     */
    public static String getUUID(){
        String token = UUID.randomUUID().toString().replaceAll("-", "");
        return token;
    }

    /**
     * Creates a JWT token with the default expiration time
     * @param subject Data to be stored in the token (JSON format)
     * @return
     */
    public static String createJWT(String subject) {
        JwtBuilder builder = getJwtBuilder(subject, null, getUUID());
        return builder.compact();
    }

    /**
     * Create JWT with custom expiration time
     * @param subject Data to be stored in the token (JSON format)
     * @param ttlMillis Token expiration time in milliseconds
     */
    public static String createJWT(String subject, Long ttlMillis) {
        JwtBuilder builder = getJwtBuilder(subject, ttlMillis, getUUID());
        return builder.compact();
    }

    /**
     * Internal helper method to build a JWT token with all metadata.
     *
     * @param subject Token subject (JSON string)
     * @param ttlMillis Expiration time in milliseconds (null uses default)
     * @param uuid Unique token ID
     * @return Configured JwtBuilder instance
     */
    private static JwtBuilder getJwtBuilder(String subject, Long ttlMillis, String uuid) {
        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;
        SecretKey secretKey = generalKey();
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        if(ttlMillis==null){
            ttlMillis=JwtUtil.JWT_TTL;
        }
        long expMillis = nowMillis + ttlMillis;
        Date expDate = new Date(expMillis);
        return Jwts.builder()
                .setId(uuid)
                .setSubject(subject)
                .setIssuer("comp5348")     // Token issuer
                .setIssuedAt(now)      // Token creation time
                .signWith(signatureAlgorithm, secretKey) //HS256 signing
                .setExpiration(expDate);
    }

    /**
     * Creates a JWT token with a specified ID and expiration time.
     *
     * @param id Token ID
     * @param subject Token payload (JSON string)
     * @param ttlMillis Expiration time in milliseconds
     * @return The generated JWT token string
     */
    public static String createJWT(String id, String subject, Long ttlMillis) {
        JwtBuilder builder = getJwtBuilder(subject, ttlMillis, id);
        return builder.compact();
    }


    /**
     * Generates a SecretKey for HS256 signing using the predefined key string.
     *
     * @return SecretKey for token signing
     */
    public static SecretKey generalKey() {
        return Keys.hmacShaKeyFor(JWT_KEY.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Parses a JWT string and retrieves its claims.
     *
     * @param jwt The JWT token string
     * @return Claims extracted from the token
     * @throws Exception If token parsing or validation fails
     */
    public static Claims parseJWT(String jwt) throws Exception {
        SecretKey secretKey = generalKey();
        return Jwts.parser()
                .setSigningKey(secretKey)
                .parseClaimsJws(jwt)
                .getBody();
    }


}