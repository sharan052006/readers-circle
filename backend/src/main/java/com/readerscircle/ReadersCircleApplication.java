package com.readerscircle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableScheduling
public class ReadersCircleApplication {
  public static void main(String[] args) {
    SpringApplication.run(ReadersCircleApplication.class, args);
  }

  // Server-local wall time: event dates/times are entered and displayed as
  // local wall-clock values (deadlines too), so "now" must be local as well.
  @Bean
  public Clock clock() {
    return Clock.systemDefaultZone();
  }
}
