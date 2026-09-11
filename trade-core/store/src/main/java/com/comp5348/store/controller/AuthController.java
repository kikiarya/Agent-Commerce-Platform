package com.comp5348.store.controller;

import com.comp5348.store.dto.LoginRequest;
import com.comp5348.store.dto.LoginResponse;
import com.comp5348.store.dto.RegisterRequest;
import com.comp5348.store.service.AuthService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@AllArgsConstructor
public class AuthController {
    
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.status(401).build());
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        var result = authService.register(request.username(), request.password(), request.email());
        if (result.isPresent()) {
            return ResponseEntity.ok(result.get());
        } else {
            return ResponseEntity.status(400).body(
                Map.of("error", "Username already exists")
            );
        }
    }

    @GetMapping("/check-username")
    public ResponseEntity<Map<String, Boolean>> checkUsername(@RequestParam String username) {
        boolean exists = authService.findByUsername(username).isPresent();
        return ResponseEntity.ok(Map.of("exists", exists));
    }
}




