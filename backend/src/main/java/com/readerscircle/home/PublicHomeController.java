package com.readerscircle.home;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicHomeController {

  private final PublicHomeService publicHomeService;

  public PublicHomeController(PublicHomeService publicHomeService) {
    this.publicHomeService = publicHomeService;
  }

  @GetMapping("/home")
  public PublicHomeDto getHomeData() {
    return publicHomeService.getHomeData();
  }

  @GetMapping("/stats")
  public PublicStatsDto getStats() {
    return publicHomeService.getStats();
  }
}
