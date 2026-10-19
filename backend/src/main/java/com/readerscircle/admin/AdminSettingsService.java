package com.readerscircle.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing platform settings with database persistence.
 * Replaces previous volatile in-memory storage, preserving exact keys, types, and defaults.
 */
@Service
public class AdminSettingsService {

  private static final Set<String> CORE_KEYS = Set.of(
      "platformName",
      "tagline",
      "communityGuidelines",
      "registrationOpen",
      "allowMemberCircleCreation",
      "maxCirclesPerMember",
      "emailNotificationsEnabled"
  );

  private final AdminSettingsRepository repository;
  private final ObjectMapper objectMapper;

  @Autowired
  public AdminSettingsService(
      AdminSettingsRepository repository,
      @Autowired(required = false) ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
  }

  public AdminSettingsService(AdminSettingsRepository repository) {
    this(repository, new ObjectMapper());
  }

  /**
   * Initializes default settings if not already present in the database.
   * If settings already exist (e.g. from previous runs or Flyway), existing values are preserved.
   */
  @PostConstruct
  @Transactional
  public void init() {
    if (!repository.existsById(AdminSettings.DEFAULT_ID)) {
      repository.save(new AdminSettings(AdminSettings.DEFAULT_ID));
    }
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getSettings() {
    AdminSettings entity = getOrCreateSettings();
    return toMap(entity);
  }

  @Transactional
  public Map<String, Object> updateSettings(Map<String, Object> patch) {
    AdminSettings entity = getOrCreateSettings();
    if (patch != null && !patch.isEmpty()) {
      applyPatch(entity, patch);
      entity.setUpdatedAt(Instant.now());
      entity = repository.save(entity);
    }
    return toMap(entity);
  }

  @Transactional
  public AdminSettings getOrCreateSettings() {
    return repository
        .findById(AdminSettings.DEFAULT_ID)
        .orElseGet(() -> repository.save(new AdminSettings(AdminSettings.DEFAULT_ID)));
  }

  private void applyPatch(AdminSettings entity, Map<String, Object> patch) {
    if (patch.containsKey("platformName") && patch.get("platformName") != null) {
      String val = String.valueOf(patch.get("platformName")).trim();
      if (!val.isEmpty()) {
        entity.setPlatformName(val);
      }
    }
    if (patch.containsKey("tagline") && patch.get("tagline") != null) {
      entity.setTagline(String.valueOf(patch.get("tagline")).trim());
    }
    if (patch.containsKey("communityGuidelines") && patch.get("communityGuidelines") != null) {
      entity.setCommunityGuidelines(String.valueOf(patch.get("communityGuidelines")).trim());
    }
    if (patch.containsKey("registrationOpen") && patch.get("registrationOpen") != null) {
      entity.setRegistrationOpen(parseBoolean(patch.get("registrationOpen")));
    }
    if (patch.containsKey("allowMemberCircleCreation") && patch.get("allowMemberCircleCreation") != null) {
      entity.setAllowMemberCircleCreation(parseBoolean(patch.get("allowMemberCircleCreation")));
    }
    if (patch.containsKey("maxCirclesPerMember") && patch.get("maxCirclesPerMember") != null) {
      entity.setMaxCirclesPerMember(parseInteger(patch.get("maxCirclesPerMember"), entity.getMaxCirclesPerMember()));
    }
    if (patch.containsKey("emailNotificationsEnabled") && patch.get("emailNotificationsEnabled") != null) {
      entity.setEmailNotificationsEnabled(parseBoolean(patch.get("emailNotificationsEnabled")));
    }

    // Handle any extra custom keys
    Map<String, Object> extra = new LinkedHashMap<>();
    if (entity.getCustomSettingsJson() != null && !entity.getCustomSettingsJson().isBlank()) {
      try {
        extra = objectMapper.readValue(entity.getCustomSettingsJson(), new TypeReference<Map<String, Object>>() {});
      } catch (Exception ignored) {
        extra = new LinkedHashMap<>();
      }
    }

    boolean extraChanged = false;
    for (Map.Entry<String, Object> entry : patch.entrySet()) {
      if (!CORE_KEYS.contains(entry.getKey())) {
        extra.put(entry.getKey(), entry.getValue());
        extraChanged = true;
      }
    }

    if (extraChanged) {
      try {
        entity.setCustomSettingsJson(objectMapper.writeValueAsString(extra));
      } catch (Exception ignored) {
      }
    }
  }

  public Map<String, Object> toMap(AdminSettings entity) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("platformName", entity.getPlatformName());
    out.put("tagline", entity.getTagline());
    out.put("communityGuidelines", entity.getCommunityGuidelines());
    out.put("registrationOpen", entity.isRegistrationOpen());
    out.put("allowMemberCircleCreation", entity.isAllowMemberCircleCreation());
    out.put("maxCirclesPerMember", entity.getMaxCirclesPerMember());
    out.put("emailNotificationsEnabled", entity.isEmailNotificationsEnabled());

    if (entity.getCustomSettingsJson() != null && !entity.getCustomSettingsJson().isBlank()) {
      try {
        Map<String, Object> extra =
            objectMapper.readValue(entity.getCustomSettingsJson(), new TypeReference<Map<String, Object>>() {});
        if (extra != null) {
          for (Map.Entry<String, Object> e : extra.entrySet()) {
            if (!CORE_KEYS.contains(e.getKey())) {
              out.put(e.getKey(), e.getValue());
            }
          }
        }
      } catch (Exception ignored) {
      }
    }

    return out;
  }

  private boolean parseBoolean(Object val) {
    if (val instanceof Boolean b) {
      return b;
    }
    return Boolean.parseBoolean(String.valueOf(val));
  }

  private int parseInteger(Object val, int fallback) {
    if (val instanceof Number n) {
      return n.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(val).trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }
}
