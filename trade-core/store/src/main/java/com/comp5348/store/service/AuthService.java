package com.comp5348.store.service;

import com.comp5348.store.dto.LoginRequest;
import com.comp5348.store.dto.LoginResponse;
import com.comp5348.store.model.LoginUser;
import com.comp5348.store.model.User;
import com.comp5348.store.repository.UserRepository;
import com.comp5348.store.config.SecurityConfig.PasswordEncoder;
import com.comp5348.store.util.JwtUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@AllArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
//    private final PasswordEncoder passwordEncoder;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final BankClient bankClient;

    public Optional<LoginResponse> login(LoginRequest request) {
        System.out.println("=== Login Attempt ===");
        System.out.println("Username: " + request.username());

        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(request.username(), request.password());
        Authentication authentication = authenticationManager.authenticate(authenticationToken);

        if (Objects.isNull(authentication)) {
            throw new RuntimeException("Invalid username or password");
        }

        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        User user = loginUser.getUser();
        String userId = user.getId().toString();
        String jwt = JwtUtil.createJWT(userId);

        var response = new LoginResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                jwt
        );
        log.info("User logged in: {}", userId);
        return Optional.of(response);

    }

    public User createUser(String username, String password, String email, String role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setRole(role);
        User savedUser = userRepository.save(user);
        
        // Create bank account - grant initial balance of $10,000
        try {
            boolean accountCreated = bankClient.createBankAccount(
                savedUser.getId(), 
                username, 
                new java.math.BigDecimal("10000.00")
            );
            if (accountCreated) {
                log.info("Bank account created for user: {} (ID: {})", username, savedUser.getId());
            } else {
                log.warn("Failed to create bank account for user: {} (ID: {})", username, savedUser.getId());
            }
        } catch (Exception e) {
            log.error("Error creating bank account for user {}: {}", username, e.getMessage(), e);
        }
        
        return savedUser;
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<LoginResponse> register(String username, String password, String email) {
        // Check if username already exists
        if (userRepository.findByUsername(username).isPresent()) {
            log.warn("Registration failed: Username already exists: {}", username);
            return Optional.empty();
        }

        // Create new customer user (with automatic bank account creation)
        User user = createUser(username, password, email, "CUSTOMER");

        String userId = user.getId().toString();
        String jwt = JwtUtil.createJWT(userId);

        log.info("Registered user: {} (ID: {}), jwt: {}", username, userId, jwt);


        return Optional.of(new LoginResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getRole(),
            jwt
        ));
    }
}

