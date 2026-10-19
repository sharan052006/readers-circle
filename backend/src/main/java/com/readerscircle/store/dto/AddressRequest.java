package com.readerscircle.store.dto;

import jakarta.validation.constraints.NotBlank;

/** Add/Edit address payload for checkout. */
public record AddressRequest(
    String label,
    @NotBlank String fullName,
    @NotBlank String phone,
    @NotBlank String line1,
    String line2,
    @NotBlank String city,
    @NotBlank String state,
    @NotBlank String pincode,
    Boolean isDefault) {}
