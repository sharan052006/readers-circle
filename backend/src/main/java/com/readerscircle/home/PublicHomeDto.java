package com.readerscircle.home;

import com.readerscircle.testimonials.TestimonialDto;
import java.util.List;

public record PublicHomeDto(
    PublicStatsDto stats,
    List<LandingEventDto> upcomingEvents,
    List<TestimonialDto> testimonials
) {}
