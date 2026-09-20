package com.readerscircle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.time.Clock;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ReadersCircleApplication {
  public static void main(String[] args) {
    SpringApplication.run(ReadersCircleApplication.class, args);
  }

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
