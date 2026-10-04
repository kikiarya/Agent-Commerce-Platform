package com.comp5348.store;

import com.comp5348.store.config.SpringSecurityConfig;
import com.comp5348.store.controller.OrderController;
import com.comp5348.store.model.User;
import com.comp5348.store.repository.UserRepository;
import com.comp5348.store.service.OrderService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = OrderController.class,
        properties = "JWT_SECRET=testing-only-secret-at-least-32-bytes")
@Import(SpringSecurityConfig.class)
class CommerceSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean OrderService orders;
    @MockitoBean UserRepository users;

    String token(long expiresIn, String issuer) {
        return Jwts.builder().setSubject("2").setIssuer(issuer)
                .setExpiration(new Date(System.currentTimeMillis() + expiresIn))
                .signWith(Keys.hmacShaKeyFor("testing-only-secret-at-least-32-bytes".getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Test void userHeaderAloneCannotAuthenticate() throws Exception {
        mvc.perform(get("/api/orders").header("X-User-Id", "2")).andExpect(status().isUnauthorized());
        verifyNoInteractions(orders);
    }

    @Test void signedIdentityOverridesForgedHeaderAndQuery() throws Exception {
        User user = new User(); user.setId(2L); user.setRole("CUSTOMER");
        when(users.findById(2L)).thenReturn(Optional.of(user));
        when(orders.getAllOrders(2L)).thenReturn(List.of());
        mvc.perform(get("/api/orders?userId=999").header("Authorization", "Bearer " + token(60000, "comp5348"))
                .header("X-User-Id", "999")).andExpect(status().isOk());
        verify(orders).getAllOrders(2L);
        verify(orders, never()).getAllOrders(999L);
    }

    @Test void expiredInvalidAndWrongIssuerTokensAreRejected() throws Exception {
        for (String value : List.of("invalid", token(-60000, "comp5348"), token(60000, "other"))) {
            mvc.perform(get("/api/orders").header("Authorization", "Bearer " + value))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(orders);
    }

    @Test void deletedUserCannotUseOldToken() throws Exception {
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + token(60000, "comp5348")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(orders);
    }

    @Test void customerCannotMutateCatalog() throws Exception {
        User user = new User(); user.setId(2L); user.setRole("CUSTOMER");
        when(users.findById(2L)).thenReturn(Optional.of(user));
        mvc.perform(delete("/api/products/1").header("Authorization", "Bearer " + token(60000, "comp5348")))
                .andExpect(status().isForbidden());
    }

    @Test void crossUserOrderAccessReturnsForbidden() throws Exception {
        User user = new User(); user.setId(2L); user.setRole("CUSTOMER");
        when(users.findById(2L)).thenReturn(Optional.of(user));
        when(orders.getForUser(9L, 2L)).thenThrow(new org.springframework.security.access.AccessDeniedException("owner"));
        mvc.perform(get("/api/orders/9").header("Authorization", "Bearer " + token(60000, "comp5348")))
                .andExpect(status().isForbidden());
    }
}
