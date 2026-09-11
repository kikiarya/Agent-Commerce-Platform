package com.comp5348.delivery.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class OrderStatusRepository {
    private final JdbcTemplate jdbcTemplate;

    public OrderStatusRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<String> findStatusByOrderId(Long orderId) {
        String sql = "select status from store.orders where id = ?";
        try {
            String status = jdbcTemplate.query(sql, ps -> ps.setLong(1, orderId), rs -> {
                if (rs.next()) return rs.getString(1);
                return null;
            });
            return Optional.ofNullable(status);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    public boolean isPaid(Long orderId) {
        return findStatusByOrderId(orderId).map("PAID"::equals).orElse(false);
    }

    public void updateOrderStatus(Long orderId, String status) {
        String sql = "update store.orders set status = ? where id = ?";
        jdbcTemplate.update(sql, status, orderId);
    }
}


