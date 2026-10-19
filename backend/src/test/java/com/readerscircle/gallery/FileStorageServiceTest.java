package com.readerscircle.gallery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

class FileStorageServiceTest {

  @TempDir
  Path tempDir;

  private FileStorageService fileStorageService;

  // Canonical valid byte sequences for testing
  private static final byte[] VALID_JPEG = new byte[] {
      (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1
  };

  private static final byte[] VALID_PNG = new byte[] {
      (byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47,
      (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A,
      0, 0, 0, 13, 'I', 'H', 'D', 'R'
  };

  private static final byte[] VALID_WEBP = new byte[] {
      'R', 'I', 'F', 'F',
      36, 0, 0, 0,
      'W', 'E', 'B', 'P',
      'V', 'P', '8', ' '
  };

  private static final byte[] VALID_MP4 = new byte[] {
      0, 0, 0, 24,
      'f', 't', 'y', 'p',
      'i', 's', 'o', 'm',
      0, 0, 2, 0,
      'm', 'p', '4', '1'
  };

  private static final byte[] VALID_MOV = new byte[] {
      0, 0, 0, 24,
      'f', 't', 'y', 'p',
      'q', 't', ' ', ' ',
      0, 0, 0, 0
  };

  private static final byte[] VALID_WEBM = new byte[] {
      (byte) 0x1A, (byte) 0x45, (byte) 0xDF, (byte) 0xA3,
      (byte) 0x9F, (byte) 0x42, (byte) 0x86, (byte) 0x81
  };

  private static final byte[] VALID_MKV = new byte[] {
      (byte) 0x1A, (byte) 0x45, (byte) 0xDF, (byte) 0xA3,
      (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x1F
  };

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
    fileStorageService = new FileStorageService(tempDir.toString(), "50MB", 30);
    fileStorageService.init();
    fileStorageService.resetRateLimit(null);
  }

  // --- 1. Valid Uploads ---

  @Test
  void storesValidJpegPhoto() {
    MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", VALID_JPEG);
    String url = fileStorageService.storeFile(file, MediaType.PHOTO);

    assertThat(url).startsWith("/api/media/");
    assertThat(url).endsWith(".jpg");

    String storedFilename = url.substring("/api/media/".length());
    Resource res = fileStorageService.loadAsResource(storedFilename);
    assertThat(res.exists()).isTrue();
  }

  @Test
  void storesValidJpegWithAlternativeExtension() {
    MockMultipartFile file = new MockMultipartFile("file", "portrait.jpeg", "image/jpeg", VALID_JPEG);
    String url = fileStorageService.storeFile(file, MediaType.PHOTO);

    assertThat(url).startsWith("/api/media/");
    assertThat(url).endsWith(".jpeg");
  }

  @Test
  void storesValidPngPhoto() {
    MockMultipartFile file = new MockMultipartFile("file", "graphic.png", "image/png", VALID_PNG);
    String url = fileStorageService.storeFile(file, MediaType.PHOTO);

    assertThat(url).startsWith("/api/media/");
    assertThat(url).endsWith(".png");
  }

  @Test
  void storesValidWebpPhoto() {
    MockMultipartFile file = new MockMultipartFile("file", "image.webp", "image/webp", VALID_WEBP);
    String url = fileStorageService.storeFile(file, MediaType.PHOTO);

    assertThat(url).startsWith("/api/media/");
    assertThat(url).endsWith(".webp");
  }

  @Test
  void storesValidMp4Video() {
    MockMultipartFile file = new MockMultipartFile("file", "clip.mp4", "video/mp4", VALID_MP4);
    String url = fileStorageService.storeFile(file, MediaType.VIDEO);

    assertThat(url).startsWith("/api/media/");
    assertThat(url).endsWith(".mp4");
  }

  @Test
  void storesValidMovVideo() {
    MockMultipartFile file = new MockMultipartFile("file", "movie.mov", "video/quicktime", VALID_MOV);
    String url = fileStorageService.storeFile(file, MediaType.VIDEO);

    assertThat(url).startsWith("/api/media/");
    assertThat(url).endsWith(".mov");
  }

  @Test
  void storesValidWebmVideo() {
    MockMultipartFile file = new MockMultipartFile("file", "recording.webm", "video/webm", VALID_WEBM);
    String url = fileStorageService.storeFile(file, MediaType.VIDEO);

    assertThat(url).startsWith("/api/media/");
    assertThat(url).endsWith(".webm");
  }

  @Test
  void storesValidMkvVideo() {
    MockMultipartFile file = new MockMultipartFile("file", "recording.mkv", "video/x-matroska", VALID_MKV);
    String url = fileStorageService.storeFile(file, MediaType.VIDEO);

    assertThat(url).startsWith("/api/media/");
    assertThat(url).endsWith(".mkv");
  }

  // --- 2. Invalid Signatures (Disguised & Corrupted Files) ---

  @Test
  void rejectsTextFileDisguisedAsJpg() {
    byte[] textBytes = "This is definitely not a JPEG image".getBytes();
    MockMultipartFile file = new MockMultipartFile("file", "fake.jpg", "image/jpeg", textBytes);

    assertThatThrownBy(() -> fileStorageService.storeFile(file, MediaType.PHOTO))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> {
          ResponseStatusException rse = (ResponseStatusException) e;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(rse.getReason()).contains("File signature does not match any supported");
        });
  }

  @Test
  void rejectsHtmlScriptDisguisedAsPng() {
    byte[] htmlBytes = "<script>alert('pwned')</script>".getBytes();
    MockMultipartFile file = new MockMultipartFile("file", "malicious.png", "image/png", htmlBytes);

    assertThatThrownBy(() -> fileStorageService.storeFile(file, MediaType.PHOTO))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> {
          ResponseStatusException rse = (ResponseStatusException) e;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(rse.getReason()).contains("File signature does not match");
        });
  }

  @Test
  void rejectsExecutableDisguisedAsWebp() {
    byte[] exeBytes = new byte[] {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0};
    MockMultipartFile file = new MockMultipartFile("file", "program.webp", "image/webp", exeBytes);

    assertThatThrownBy(() -> fileStorageService.storeFile(file, MediaType.PHOTO))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> {
          ResponseStatusException rse = (ResponseStatusException) e;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        });
  }

  @Test
  void rejectsTruncatedHeader() {
    byte[] truncatedBytes = new byte[] {(byte) 0xFF, (byte) 0xD8};
    MockMultipartFile file = new MockMultipartFile("file", "truncated.jpg", "image/jpeg", truncatedBytes);

    assertThatThrownBy(() -> fileStorageService.storeFile(file, MediaType.PHOTO))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> {
          ResponseStatusException rse = (ResponseStatusException) e;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(rse.getReason()).contains("malformed or truncated");
        });
  }

  // --- 3. Mismatched Extensions and Content Types ---

  @Test
  void rejectsPngBytesWithJpgExtension() {
    MockMultipartFile file = new MockMultipartFile("file", "mismatch.jpg", "image/jpeg", VALID_PNG);

    assertThatThrownBy(() -> fileStorageService.storeFile(file, MediaType.PHOTO))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> {
          ResponseStatusException rse = (ResponseStatusException) e;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(rse.getReason()).contains("does not match detected file content type");
        });
  }

  @Test
  void rejectsJpegBytesWithPngExtension() {
    MockMultipartFile file = new MockMultipartFile("file", "mismatch.png", "image/png", VALID_JPEG);

    assertThatThrownBy(() -> fileStorageService.storeFile(file, MediaType.PHOTO))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> {
          ResponseStatusException rse = (ResponseStatusException) e;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(rse.getReason()).contains("does not match detected file content type");
        });
  }

  @Test
  void rejectsVideoBytesUploadedAsPhoto() {
    MockMultipartFile file = new MockMultipartFile("file", "video.mp4", "video/mp4", VALID_MP4);

    assertThatThrownBy(() -> fileStorageService.storeFile(file, MediaType.PHOTO))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> {
          ResponseStatusException rse = (ResponseStatusException) e;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(rse.getReason()).contains("Invalid photo format");
        });
  }

  @Test
  void rejectsPhotoBytesUploadedAsVideo() {
    MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", VALID_JPEG);

    assertThatThrownBy(() -> fileStorageService.storeFile(file, MediaType.VIDEO))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> {
          ResponseStatusException rse = (ResponseStatusException) e;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(rse.getReason()).contains("Invalid video format");
        });
  }

  // --- 4. Empty and Oversized Files ---

  @Test
  void rejectsEmptyFile() {
    MockMultipartFile file = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

    assertThatThrownBy(() -> fileStorageService.storeFile(file, MediaType.PHOTO))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> {
          ResponseStatusException rse = (ResponseStatusException) e;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(rse.getReason()).isEqualTo("File must not be empty");
        });
  }

  @Test
  void rejectsNullFile() {
    assertThatThrownBy(() -> fileStorageService.storeFile(null, MediaType.PHOTO))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> {
          ResponseStatusException rse = (ResponseStatusException) e;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(rse.getReason()).isEqualTo("File must not be empty");
        });
  }

  @Test
  void rejectsOversizedFile() {
    FileStorageService strictService = new FileStorageService(tempDir.toString(), "100B", 30);
    strictService.init();

    byte[] oversized = new byte[200];
    System.arraycopy(VALID_JPEG, 0, oversized, 0, VALID_JPEG.length);
    MockMultipartFile file = new MockMultipartFile("file", "big.jpg", "image/jpeg", oversized);

    assertThatThrownBy(() -> strictService.storeFile(file, MediaType.PHOTO))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> {
          ResponseStatusException rse = (ResponseStatusException) e;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(rse.getReason()).contains("File size exceeds configured limit");
        });
  }

  // --- 5. Filename Sanitization & Path Traversal ---

  @Test
  void preventsPathTraversalInFilename() {
    MockMultipartFile file = new MockMultipartFile("file", "../../etc/passwd.jpg", "image/jpeg", VALID_JPEG);

    assertThatThrownBy(() -> fileStorageService.storeFile(file, MediaType.PHOTO))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> {
          ResponseStatusException rse = (ResponseStatusException) e;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(rse.getReason()).contains("invalid path sequence");
        });
  }

  @Test
  void preventsBackslashPathTraversalInFilename() {
    MockMultipartFile file = new MockMultipartFile("file", "..\\..\\windows\\system32.jpg", "image/jpeg", VALID_JPEG);

    assertThatThrownBy(() -> fileStorageService.storeFile(file, MediaType.PHOTO))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> {
          ResponseStatusException rse = (ResponseStatusException) e;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(rse.getReason()).contains("invalid path sequence");
        });
  }

  @Test
  void generatedFilenameDoesNotContainOriginalFilename() {
    MockMultipartFile file = new MockMultipartFile("file", "user_confidential_report.png", "image/png", VALID_PNG);
    String url = fileStorageService.storeFile(file, MediaType.PHOTO);

    String storedFilename = url.substring("/api/media/".length());
    assertThat(storedFilename).doesNotContain("user_confidential_report");
    assertThat(storedFilename).matches("^[0-9a-fA-F-]{36}\\.png$");
  }

  @Test
  void loadAsResourceRejectsPathTraversal() {
    assertThatThrownBy(() -> fileStorageService.loadAsResource("../../etc/passwd"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> {
          ResponseStatusException rse = (ResponseStatusException) e;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        });
  }

  @Test
  void loadAsResourceReturns404ForNonexistentFile() {
    assertThatThrownBy(() -> fileStorageService.loadAsResource("nonexistent.jpg"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> {
          ResponseStatusException rse = (ResponseStatusException) e;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        });
  }

  // --- 6. Unsupported Formats & Extensions ---

  @Test
  void rejectsExecutableExtension() {
    MockMultipartFile file = new MockMultipartFile("file", "malware.exe", "application/octet-stream", VALID_JPEG);

    assertThatThrownBy(() -> fileStorageService.storeFile(file, MediaType.PHOTO))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> {
          ResponseStatusException rse = (ResponseStatusException) e;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(rse.getReason()).contains("Invalid photo format");
        });
  }

  @Test
  void rejectsMissingExtension() {
    MockMultipartFile file = new MockMultipartFile("file", "noextension", "image/jpeg", VALID_JPEG);

    assertThatThrownBy(() -> fileStorageService.storeFile(file, MediaType.PHOTO))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> {
          ResponseStatusException rse = (ResponseStatusException) e;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(rse.getReason()).isEqualTo("File must have a valid extension");
        });
  }

  // --- 7. Rate Limiting Protection ---

  @Test
  void rateLimiterBlocksAbusiveUploadFloods() {
    FileStorageService rateLimitedService = new FileStorageService(tempDir.toString(), "50MB", 3);
    rateLimitedService.init();

    String userKey = "user:" + UUID.randomUUID();

    // 3 uploads succeed
    rateLimitedService.checkRateLimit(userKey);
    rateLimitedService.checkRateLimit(userKey);
    rateLimitedService.checkRateLimit(userKey);

    // 4th upload fails with HTTP 429
    assertThatThrownBy(() -> rateLimitedService.checkRateLimit(userKey))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> {
          ResponseStatusException rse = (ResponseStatusException) e;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
          assertThat(rse.getReason()).contains("Upload rate limit exceeded");
        });
  }

  @Test
  void rateLimiterIsPerUserIsolated() {
    FileStorageService rateLimitedService = new FileStorageService(tempDir.toString(), "50MB", 2);
    rateLimitedService.init();

    String userA = "user:user-a";
    String userB = "user:user-b";

    // User A uses up quota
    rateLimitedService.checkRateLimit(userA);
    rateLimitedService.checkRateLimit(userA);

    assertThatThrownBy(() -> rateLimitedService.checkRateLimit(userA))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

    // User B is unaffected and can still upload
    rateLimitedService.checkRateLimit(userB);
    rateLimitedService.checkRateLimit(userB);

    assertThatThrownBy(() -> rateLimitedService.checkRateLimit(userB))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
  }
}
