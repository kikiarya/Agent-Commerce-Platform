package com.comp5348.bank.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserEmailRepository {
    private final JdbcTemplate jdbcTemplate;

    public UserEmailRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Cross-schema lookup: read customer email from Store schema by order id.
     * Assumes Store schema name is "store" and tables are "orders" and "users".
     */
    public Optional<String> findEmailByOrderId(Long orderId) {
        String sql = "select u.email from store.orders o join store.users u on u.id = o.user_id where o.id = ?";
        try {
            String email = jdbcTemplate.query(sql, ps -> ps.setLong(1, orderId), rs -> {
                if (rs.next()) return rs.getString(1);
                return null;
            });
            return Optional.ofNullable(email);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
}


