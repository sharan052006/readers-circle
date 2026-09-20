package com.readerscircle.circles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record JoinActionRequest(
    @NotBlank
    @Pattern(regexp = "APPROVE|REJECT", message = "Action must be APPROVE or REJECT")
    String action) {}
