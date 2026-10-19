package com.readerscircle.gallery;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Deque;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FileStorageService {

  private static final Set<String> ALLOWED_PHOTO_EXTENSIONS =
      Set.of("jpg", "jpeg", "png", "webp");

  private static final Set<String> ALLOWED_VIDEO_EXTENSIONS =
      Set.of("mp4", "webm", "mov", "mkv");

  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FileStorageService.class);

  private Path storageLocation;
  private final String maxFileSizeStr;
  private final long maxFileSizeBytes;
  private final int maxUploadsPerMinute;
  private final ConcurrentHashMap<String, Deque<Long>> uploadTimestamps = new ConcurrentHashMap<>();

  @org.springframework.beans.factory.annotation.Autowired
  public FileStorageService(
      @Value("${app.upload.dir:uploads/gallery}") String uploadDir,
      @Value("${spring.servlet.multipart.max-file-size:50MB}") String maxFileSizeStr,
      @Value("${app.upload.max-per-minute:30}") int maxUploadsPerMinute) {
    this.storageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
    this.maxFileSizeStr = maxFileSizeStr;
    this.maxFileSizeBytes = DataSize.parse(maxFileSizeStr).toBytes();
    this.maxUploadsPerMinute = maxUploadsPerMinute;
  }

  public FileStorageService(String uploadDir) {
    this(uploadDir, "50MB", 30);
  }

  @PostConstruct
  public void init() {
    try {
      Files.createDirectories(storageLocation);
      log.info("Initialized storage directory: {}", storageLocation);
    } catch (IOException e) {
      Path fallback = Paths.get(System.getProperty("java.io.tmpdir", "/tmp"), "readers-circle", "uploads", "gallery")
          .toAbsolutePath().normalize();
      if (!storageLocation.equals(fallback)) {
        log.warn("Could not initialize storage directory: {}. Attempting fallback to: {}. "
            + "Note: /tmp storage is ephemeral and will not persist across container restarts. "
            + "Configure UPLOAD_DIR or a Render Persistent Disk for production storage.", storageLocation, fallback);
        try {
          Files.createDirectories(fallback);
          this.storageLocation = fallback;
          log.info("Successfully initialized fallback storage directory: {}", this.storageLocation);
          return;
        } catch (IOException fallbackError) {
          log.error("Failed to initialize fallback storage directory: {}", fallback, fallbackError);
        }
      }
      throw new IllegalStateException("Could not initialize storage directory: " + storageLocation
          + ". Ensure the directory exists and is writable, or configure UPLOAD_DIR environment variable.", e);
    }
  }

  public Path getStorageLocation() {
    return storageLocation;
  }

  public String storeFile(MultipartFile file, MediaType mediaType) {
    if (file == null || file.isEmpty() || file.getSize() == 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must not be empty");
    }

    if (file.getSize() > maxFileSizeBytes) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "File size exceeds configured limit of " + maxFileSizeStr);
    }

    // Rate limiting per user / client
    checkRateLimit(resolveClientKey());

    String originalFilename = StringUtils.cleanPath(
        file.getOriginalFilename() != null ? file.getOriginalFilename() : "");

    if (originalFilename.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filename must not be blank");
    }

    if (originalFilename.contains("..") || originalFilename.contains("/") || originalFilename.contains("\\")) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Filename contains invalid path sequence");
    }

    String extension = getFileExtension(originalFilename).toLowerCase(Locale.ROOT);
    if (extension.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must have a valid extension");
    }
    validateMediaExtension(extension, mediaType);

    // Read magic bytes for content validation (do not trust client Content-Type)
    byte[] header = new byte[64];
    int bytesRead;
    try (InputStream is = file.getInputStream()) {
      bytesRead = is.readNBytes(header, 0, 64);
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read file content", e);
    }

    if (bytesRead < 3) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File content is malformed or truncated");
    }

    DetectedFormat detected = detectFormat(header, bytesRead);
    if (detected == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "File signature does not match any supported image or video format");
    }

    if (detected.getMediaType() != mediaType) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "File content is " + detected.getMediaType() + " but expected " + mediaType);
    }

    if (!detected.matchesExtension(extension)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "File extension '." + extension + "' does not match detected file content type (" + detected.getMimeType() + ")");
    }

    // Generate safe, collision-resistant server-side filename using verified extension
    String canonicalExt = detected.getCanonicalExtension(extension);
    String storedFilename = UUID.randomUUID() + "." + canonicalExt;
    Path targetPath = storageLocation.resolve(storedFilename).normalize();

    if (!targetPath.startsWith(storageLocation)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file destination path");
    }

    try (InputStream is = file.getInputStream()) {
      Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file", e);
    }

    return "/api/media/" + storedFilename;
  }

  public Resource loadAsResource(String filename) {
    if (filename == null
        || filename.isBlank()
        || filename.contains("..")
        || filename.contains("/")
        || filename.contains("\\")
        || filename.contains("%")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid filename");
    }

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

  public void checkRateLimit(String clientKey) {
    long now = System.currentTimeMillis();
    long windowStart = now - 60_000L;

    uploadTimestamps.compute(clientKey, (k, deque) -> {
      if (deque == null) {
        deque = new ConcurrentLinkedDeque<>();
      }
      while (!deque.isEmpty() && deque.peekFirst() < windowStart) {
        deque.pollFirst();
      }
      if (deque.size() >= maxUploadsPerMinute) {
        throw new ResponseStatusException(
            HttpStatus.TOO_MANY_REQUESTS,
            "Upload rate limit exceeded (" + maxUploadsPerMinute + " uploads per minute). Please try again shortly.");
      }
      deque.addLast(now);
      return deque;
    });

    if (uploadTimestamps.size() > 500) {
      cleanupStaleRateLimitEntries(windowStart);
    }
  }

  public void resetRateLimit(String clientKey) {
    if (clientKey == null) {
      uploadTimestamps.clear();
    } else {
      uploadTimestamps.remove(clientKey);
    }
  }

  public long getMaxFileSizeBytes() {
    return maxFileSizeBytes;
  }

  public String getMaxFileSizeStr() {
    return maxFileSizeStr;
  }

  public int getMaxUploadsPerMinute() {
    return maxUploadsPerMinute;
  }

  private void cleanupStaleRateLimitEntries(long cutoff) {
    uploadTimestamps.entrySet().removeIf(entry -> {
      Deque<Long> deque = entry.getValue();
      return deque == null || deque.isEmpty() || deque.peekLast() < cutoff;
    });
  }

  private String resolveClientKey() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.isAuthenticated() && auth.getName() != null && !auth.getName().equals("anonymousUser")) {
      return "user:" + auth.getName();
    }
    RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
    if (attrs instanceof ServletRequestAttributes sra) {
      HttpServletRequest req = sra.getRequest();
      String forwarded = req.getHeader("X-Forwarded-For");
      if (StringUtils.hasText(forwarded)) {
        return "ip:" + forwarded.split(",")[0].trim();
      }
      return "ip:" + req.getRemoteAddr();
    }
    return "client:default";
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

  public enum DetectedFormat {
    JPEG(MediaType.PHOTO, "image/jpeg", Set.of("jpg", "jpeg")),
    PNG(MediaType.PHOTO, "image/png", Set.of("png")),
    WEBP(MediaType.PHOTO, "image/webp", Set.of("webp")),
    MP4_OR_MOV(MediaType.VIDEO, "video/mp4", Set.of("mp4", "mov")),
    WEBM_OR_MKV(MediaType.VIDEO, "video/webm", Set.of("webm", "mkv"));

    private final MediaType mediaType;
    private final String mimeType;
    private final Set<String> matchingExtensions;

    DetectedFormat(MediaType mediaType, String mimeType, Set<String> matchingExtensions) {
      this.mediaType = mediaType;
      this.mimeType = mimeType;
      this.matchingExtensions = matchingExtensions;
    }

    public MediaType getMediaType() {
      return mediaType;
    }

    public String getMimeType() {
      return mimeType;
    }

    public boolean matchesExtension(String ext) {
      return matchingExtensions.contains(ext);
    }

    public String getCanonicalExtension(String ext) {
      if (matchingExtensions.contains(ext)) {
        return ext;
      }
      return matchingExtensions.iterator().next();
    }
  }

  public static DetectedFormat detectFormat(byte[] header, int bytesRead) {
    if (header == null || bytesRead < 3) {
      return null;
    }

    // 1. JPEG: FF D8 FF
    if (bytesRead >= 3
        && (header[0] & 0xFF) == 0xFF
        && (header[1] & 0xFF) == 0xD8
        && (header[2] & 0xFF) == 0xFF) {
      return DetectedFormat.JPEG;
    }

    // 2. PNG: 89 50 4E 47 0D 0A 1A 0A
    if (bytesRead >= 8
        && (header[0] & 0xFF) == 0x89
        && (header[1] & 0xFF) == 0x50
        && (header[2] & 0xFF) == 0x4E
        && (header[3] & 0xFF) == 0x47
        && (header[4] & 0xFF) == 0x0D
        && (header[5] & 0xFF) == 0x0A
        && (header[6] & 0xFF) == 0x1A
        && (header[7] & 0xFF) == 0x0A) {
      return DetectedFormat.PNG;
    }

    // 3. WEBP: RIFF....WEBP (12 bytes)
    if (bytesRead >= 12
        && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
        && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
      return DetectedFormat.WEBP;
    }

    // 4. MP4 / MOV: ISO-BMFF / QuickTime box types at offset 4..7
    if (bytesRead >= 8) {
      boolean isFtyp = header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p';
      boolean isMoov = header[4] == 'm' && header[5] == 'o' && header[6] == 'o' && header[7] == 'v';
      boolean isMdat = header[4] == 'm' && header[5] == 'd' && header[6] == 'a' && header[7] == 't';
      boolean isWide = header[4] == 'w' && header[5] == 'i' && header[6] == 'd' && header[7] == 'e';
      if (isFtyp || isMoov || isMdat || isWide) {
        return DetectedFormat.MP4_OR_MOV;
      }
    }

    // 5. WEBM / MKV: EBML header 1A 45 DF A3
    if (bytesRead >= 4
        && (header[0] & 0xFF) == 0x1A
        && (header[1] & 0xFF) == 0x45
        && (header[2] & 0xFF) == 0xDF
        && (header[3] & 0xFF) == 0xA3) {
      return DetectedFormat.WEBM_OR_MKV;
    }

    return null;
  }
}
