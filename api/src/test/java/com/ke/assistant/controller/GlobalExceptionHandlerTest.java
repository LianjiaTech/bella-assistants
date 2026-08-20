package com.ke.assistant.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ke.bella.openapi.common.exception.ChannelException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class GlobalExceptionHandlerTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler(new ObjectMapper());

    @BeforeEach
    void setUp() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void shouldLogClientChannelExceptionAsWarnWithoutStackTrace() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/threads");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ChannelException exception = new ChannelException.AuthorizationException("invalid Authorization header");

        exceptionHandler.resolveException(request, response, null, exception);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage())
                .isEqualTo("ChannelException: method=GET, uri=/v1/threads, status=401, message=invalid Authorization header");
        assertThat(event.getThrowableProxy()).isNull();
    }

    @Test
    void shouldLogServerChannelExceptionAsErrorWithStackTrace() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/responses");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ChannelException exception = new ChannelException.OpenAIException(500, "api_error", "upstream failed");

        exceptionHandler.resolveException(request, response, null, exception);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage())
                .isEqualTo("ChannelException: method=POST, uri=/v1/responses, status=500, message=upstream failed");
        assertThat(event.getThrowableProxy()).isNotNull();
    }
}
