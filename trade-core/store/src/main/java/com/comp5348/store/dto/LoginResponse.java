package com.comp5348.store.dto;

public record LoginResponse(
    Long userId,
    String username,
    String email,
    String role,
    String token
) {}




