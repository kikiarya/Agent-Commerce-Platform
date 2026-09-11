package com.comp5348.store.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Configuration
public class SecurityConfig {
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new PasswordEncoder();
    }
    
    public static class PasswordEncoder {
        private final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder delegate =
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        public String encode(String password) {
            return delegate.encode(password);
        }
        
        public boolean matches(String raw, String encoded) {
            return delegate.matches(raw, encoded);
        }
    }
}

