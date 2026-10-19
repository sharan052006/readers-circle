package com.readerscircle.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Checkout payload: delivery address + payment choice. */
public record PlaceOrderRequest(
    @NotNull UUID addressId,
    @NotBlank String paymentMethod,
    String note) {}
