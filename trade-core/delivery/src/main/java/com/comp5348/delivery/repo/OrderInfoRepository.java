package com.comp5348.delivery.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public class OrderInfoRepository {
    private final JdbcTemplate jdbcTemplate;

    public OrderInfoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record OrderInfo(Long userId, BigDecimal totalAmount, String email) {}

    /**
     * Get order and user information by orderId
     */
    public Optional<OrderInfo> findOrderInfo(Long orderId) {
        String sql = "SELECT o.user_id, o.total_amount, u.email " +
                     "FROM store.orders o " +
                     "JOIN store.users u ON u.id = o.user_id " +
                     "WHERE o.id = ?";
        try {
            OrderInfo info = jdbcTemplate.query(sql, ps -> ps.setLong(1, orderId), rs -> {
                if (rs.next()) {
                    return new OrderInfo(
                        rs.getLong("user_id"),
                        rs.getBigDecimal("total_amount"),
                        rs.getString("email")
                    );
                }
                return null;
            });
            return Optional.ofNullable(info);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
}
