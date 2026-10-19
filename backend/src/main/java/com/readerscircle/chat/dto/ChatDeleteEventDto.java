package com.readerscircle.chat.dto;

import java.util.UUID;

public record ChatDeleteEventDto(
    UUID messageId,
    UUID circleId,
    boolean deleted
) {}
