package com.caifan.admin.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("offline")
class UserChannelAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void customerWebPagesAreRemoved() throws Exception {
        mockMvc.perform(get("/mall/login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void appApiRequiresBearerToken() throws Exception {
        mockMvc.perform(get("/api/mall/goods"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(2001));
    }
}
