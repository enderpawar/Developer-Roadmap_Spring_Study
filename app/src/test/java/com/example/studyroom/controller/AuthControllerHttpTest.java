package com.example.studyroom.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
}
