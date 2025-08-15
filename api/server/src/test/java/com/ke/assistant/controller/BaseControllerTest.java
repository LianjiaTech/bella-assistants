package com.ke.assistant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ke.assistant.configuration.AssistantProperties;
import com.ke.bella.openapi.server.BellaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

@SpringBootTest
@AutoConfigureMockMvc  
@ActiveProfiles("ut")
@Transactional
@Rollback
@EnableConfigurationProperties(AssistantProperties.class)
@ComponentScan(basePackages = {"com.ke.assistant"})
@BellaService
public abstract class BaseControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Value("${test.api.key}")
    protected String apiKey;

    protected String loadTestData(String filename) throws Exception {
        ClassPathResource resource = new ClassPathResource("testdata/" + filename);
        try (java.io.InputStream is = resource.getInputStream()) {
            return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
        }
    }

    protected MockHttpServletRequestBuilder addAuthHeader(MockHttpServletRequestBuilder requestBuilder) {
        return requestBuilder.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
    }
}
