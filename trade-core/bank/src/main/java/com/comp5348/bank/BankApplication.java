package com.comp5348.bank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.CommandLineRunner;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@SpringBootApplication
public class BankApplication {
    public static void main(String[] args) {
        SpringApplication.run(BankApplication.class, args);
    }

    /** Ensure schema exists on startup (idempotent) */
    @Bean
    public CommandLineRunner ensureSchema(DataSource ds) {
        return args -> {
            try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                s.execute("CREATE SCHEMA IF NOT EXISTS bank");
            } catch (Exception ignored) {}
        };
    }
}

