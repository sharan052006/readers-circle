package com.readerscircle.auth;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * TOTP (RFC 6238, SHA-1, 30s step, 6 digits) for two-factor auth.
 * Hand-rolled on {@code javax.crypto} so no new dependency is needed.
 */
@Service
public class TotpService {

  private static final int STEP_SECONDS = 30;
  private static final int DIGITS = 6;
  private static final int WINDOW = 1;
  private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

  private final SecureRandom random = new SecureRandom();

  /** 20 random bytes, Base32-encoded (no padding) — the shared secret. */
  public String generateSecret() {
    byte[] bytes = new byte[20];
    random.nextBytes(bytes);
    StringBuilder sb = new StringBuilder(32);
    int buffer = 0;
    int bits = 0;
    for (byte b : bytes) {
      buffer = (buffer << 8) | (b & 0xFF);
      bits += 8;
      while (bits >= 5) {
        sb.append(BASE32.charAt((buffer >>> (bits - 5)) & 31));
        bits -= 5;
      }
    }
    if (bits > 0) {
      sb.append(BASE32.charAt((buffer << (5 - bits)) & 31));
    }
    return sb.toString();
  }

  public String otpauthUrl(String secret, String email) {
    String label = ("Reader's Circle:" + email).replace(" ", "%20");
    return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=Reader's%20Circle&digits=6&period=30";
  }

  /** Accepts codes from the previous, current, and next step (clock skew). */
  public boolean verify(String base32Secret, String code) {
    if (base32Secret == null || code == null) {
      return false;
    }
    String digits = code.trim();
    if (!digits.matches("\\d{6}")) {
      return false;
    }
    byte[] key;
    try {
      key = decodeBase32(base32Secret);
    } catch (IllegalArgumentException e) {
      return false;
    }
    long step = System.currentTimeMillis() / 1000 / STEP_SECONDS;
    for (long s = step - WINDOW; s <= step + WINDOW; s++) {
      if (digits.equals(generate(key, s))) {
        return true;
      }
    }
    return false;
  }

  private String generate(byte[] key, long step) {
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(key, "HmacSHA1"));
      byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
      int offset = hash[hash.length - 1] & 0x0F;
      int code =
          ((hash[offset] & 0x7F) << 24)
              | ((hash[offset + 1] & 0xFF) << 16)
              | ((hash[offset + 2] & 0xFF) << 8)
              | (hash[offset + 3] & 0xFF);
      int otp = code % (int) Math.pow(10, DIGITS);
      return String.format("%06d", otp);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("TOTP unavailable", e);
    }
  }

  private byte[] decodeBase32(String s) {
    String clean = s.trim().replace("=", "").toUpperCase();
    int len = clean.length();
    byte[] out = new byte[len * 5 / 8];
    int buffer = 0;
    int bits = 0;
    int pos = 0;
    for (int i = 0; i < len; i++) {
      int v = BASE32.indexOf(clean.charAt(i));
      if (v < 0) {
        throw new IllegalArgumentException("Invalid Base32");
      }
      buffer = (buffer << 5) | v;
      bits += 5;
      if (bits >= 8) {
        out[pos++] = (byte) ((buffer >>> (bits - 8)) & 0xFF);
        bits -= 8;
      }
    }
    return out;
  }
}
