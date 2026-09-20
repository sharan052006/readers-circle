package com.readerscircle.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityMatrixTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper om;
  @Autowired AuthService auth;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;

  private String readerAccess() {
    String email = "r" + System.nanoTime() + "@t.com";
    return auth.registerReader(new RegisterRequest("R", email, "password123")).accessToken();
  }

  @Test
  void publicAuthAnonymousUsersForbidden() throws Exception {
    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"X\",\"email\":\"x"
                        + System.nanoTime()
                        + "@t.com\",\"password\":\"password123\"}"))
        .andExpect(status().isCreated());

    mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
  }

  @Test
  void readerCannotListUsersAdminCan() throws Exception {
    String reader = readerAccess();
    mvc.perform(get("/api/users").header("Authorization", "Bearer " + reader))
        .andExpect(status().isForbidden());
  }

  @Test
  void meRequiresAuth() throws Exception {
    mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    String reader = readerAccess();
    mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + reader))
        .andExpect(status().isOk());
  }

  @Test
  void duplicateRegister409() throws Exception {
    String email = "d" + System.nanoTime() + "@t.com";
    String body =
        om.writeValueAsString(new RegisterRequest("D", email, "password123"));
    mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated());
    mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict());
  }

  @Test
  void invalidLogin401() throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@t.com\",\"password\":\"wrong\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void adminCanPatchRole() throws Exception {
    String email = "p" + System.nanoTime() + "@t.com";
    AuthResponse r = auth.registerReader(new RegisterRequest("P", email, "password123"));
    String adminEmail = "adm" + System.nanoTime() + "@t.com";
    AuthResponse adminReg = auth.registerReader(new RegisterRequest("ADM", adminEmail, "password123"));
    promote(adminReg.userId());
    String adminToken = jwtFor(adminReg.userId());
    mvc.perform(
            patch("/api/users/" + r.userId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ORGANIZER\"}"))
        .andExpect(status().isOk());
  }

  private void promote(java.util.UUID id) {
    User u = users.findById(id).orElseThrow();
    u.setRole(Role.ADMIN);
    users.save(u);
  }

  private String jwtFor(java.util.UUID id) {
    return jwt.generateAccess(users.findById(id).orElseThrow());
  }
}
