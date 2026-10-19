package com.readerscircle.gallery;

import java.io.IOException;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MediaController {

  private final FileStorageService fileStorageService;

  public MediaController(FileStorageService fileStorageService) {
    this.fileStorageService = fileStorageService;
  }

  @GetMapping("/api/media/{filename:.+}")
  public ResponseEntity<Resource> serveMedia(
      @PathVariable String filename,
      @RequestParam(value = "download", defaultValue = "false") boolean download) {
    Resource file = fileStorageService.loadAsResource(filename);
    String contentType =
        MediaTypeFactory.getMediaType(file)
            .map(org.springframework.http.MediaType::toString)
            .orElse("application/octet-stream");

    ResponseEntity.BodyBuilder response =
        ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, contentType)
            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400");

    if (download) {
      response.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
    }

    return response.body(file);
  }

  /**
   * Generic image upload for create/edit forms (event covers, circle covers).
   * Returns the public URL to send back as coverImageUrl. PHOTO only.
   * Restricted to Organizers and Admins who manage circles and events.
   */
  @PostMapping(value = "/api/media", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
  public Map<String, String> uploadMedia(@RequestParam("file") MultipartFile file) {
    String url = fileStorageService.storeFile(file, MediaType.PHOTO);
    return Map.of("url", url);
  }
}
