package com.readerscircle.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Size(max = 100) String name,
    @Email @NotBlank String email,
    @Size(min = 8, max = 72) String password) {}
