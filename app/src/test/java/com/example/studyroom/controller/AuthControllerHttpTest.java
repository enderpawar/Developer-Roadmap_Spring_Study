package com.example.studyroom.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void signupReturnsCreatedMemberWithoutPassword() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "jinwoo01",
                                  "password": "password1234",
                                  "name": "진우"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("jinwoo01"))
                .andExpect(jsonPath("$.name").value("진우"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void signupReturns409WhenLoginIdAlreadyExists() throws Exception {
        String body = """
                {
                  "loginId": "duplicate01",
                  "password": "password1234",
                  "name": "철수"
                }
                """;

        mockMvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("이미 사용 중인 아이디입니다. (loginId: duplicate01)"));
    }

    @Test
    void signupReturns400WhenPasswordTooShort() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "shortpw01",
                                  "password": "1234",
                                  "name": "영희"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.password").value("비밀번호는 8자 이상이어야 합니다"));
    }

    @Test
    void loginReturnsTokenForCorrectCredentials() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "loginok01",
                                  "password": "password1234",
                                  "name": "민지"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "loginok01",
                                  "password": "password1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(not(emptyString())));
    }

    @Test
    void loginReturns401ForWrongPassword() throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "loginwrong01",
                                  "password": "password1234",
                                  "name": "하늘"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "loginwrong01",
                                  "password": "wrongPassword"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("아이디 또는 비밀번호가 올바르지 않습니다."));
    }

    @Test
    void loginReturns401WhenLoginIdDoesNotExist() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "no-such-id",
                                  "password": "password1234"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("아이디 또는 비밀번호가 올바르지 않습니다."));
    }
}
