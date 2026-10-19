package com.readerscircle.home;

public record PublicStatsDto(
    long readerCount,
    long circleCount,
    long eventCount,
    long citationCount
) {}
