package com.readerscircle.gallery;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FileStorageService {

  private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50 MB

  private static final Set<String> ALLOWED_PHOTO_EXTENSIONS =
      Set.of("jpg", "jpeg", "png", "webp");

  private static final Set<String> ALLOWED_VIDEO_EXTENSIONS =
      Set.of("mp4", "webm", "mov", "mkv");

  private final Path storageLocation;

  public FileStorageService(@Value("${app.upload.dir:uploads/gallery}") String uploadDir) {
    this.storageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
  }

  @PostConstruct
  public void init() {
    try {
      Files.createDirectories(storageLocation);
    } catch (IOException e) {
      throw new IllegalStateException("Could not initialize storage directory: " + storageLocation, e);
    }
  }

  public String storeFile(MultipartFile file, MediaType mediaType) {
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must not be empty");
    }

    if (file.getSize() > MAX_FILE_SIZE) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "File size exceeds 50MB limit");
    }

    String originalFilename = StringUtils.cleanPath(
        file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload");

    if (originalFilename.contains("..")) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Filename contains invalid path sequence");
    }

    String extension = getFileExtension(originalFilename).toLowerCase(Locale.ROOT);
    validateMediaExtension(extension, mediaType);

    String storedFilename = UUID.randomUUID() + "-" + originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
    Path targetPath = storageLocation.resolve(storedFilename).normalize();

    if (!targetPath.startsWith(storageLocation)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file destination path");
    }

    try {
      Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file", e);
    }

    return "/api/media/" + storedFilename;
  }

  public Resource loadAsResource(String filename) {
    try {
      Path filePath = storageLocation.resolve(filename).normalize();
      if (!filePath.startsWith(storageLocation)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to requested file");
      }
      Resource resource = new UrlResource(filePath.toUri());
      if (resource.exists() && resource.isReadable()) {
        return resource;
      } else {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media file not found: " + filename);
      }
    } catch (MalformedURLException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media file not found: " + filename, e);
    }
  }

  private void validateMediaExtension(String ext, MediaType mediaType) {
    if (mediaType == MediaType.PHOTO) {
      if (!ALLOWED_PHOTO_EXTENSIONS.contains(ext)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Invalid photo format ." + ext + ". Allowed formats: " + ALLOWED_PHOTO_EXTENSIONS);
      }
    } else if (mediaType == MediaType.VIDEO) {
      if (!ALLOWED_VIDEO_EXTENSIONS.contains(ext)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Invalid video format ." + ext + ". Allowed formats: " + ALLOWED_VIDEO_EXTENSIONS);
      }
    }
  }

  private String getFileExtension(String filename) {
    int dotIndex = filename.lastIndexOf('.');
    if (dotIndex == -1 || dotIndex == filename.length() - 1) {
      return "";
    }
    return filename.substring(dotIndex + 1);
  }
}
