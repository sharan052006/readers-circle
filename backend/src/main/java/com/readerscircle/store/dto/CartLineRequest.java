package com.readerscircle.store.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Add-to-cart / quantity payloads. */
public record CartLineRequest(@NotNull UUID bookId, @Min(1) Integer qty) {}
