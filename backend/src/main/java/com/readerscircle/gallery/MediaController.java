package com.readerscircle.gallery;

import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MediaController {

  private final FileStorageService fileStorageService;

  public MediaController(FileStorageService fileStorageService) {
    this.fileStorageService = fileStorageService;
  }

  @GetMapping("/api/media/{filename:.+}")
  public ResponseEntity<Resource> serveMedia(@PathVariable String filename) {
    Resource file = fileStorageService.loadAsResource(filename);
    String contentType =
        MediaTypeFactory.getMediaType(file)
            .map(org.springframework.http.MediaType::toString)
            .orElse("application/octet-stream");

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, contentType)
        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
        .body(file);
  }
}
