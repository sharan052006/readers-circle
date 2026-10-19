package com.readerscircle.store.dto;

import jakarta.validation.constraints.NotBlank;

/** Update Order Status modal payload. */
public record UpdateOrderStatusRequest(
    @NotBlank String status,
    String note,
    String trackingId) {}
