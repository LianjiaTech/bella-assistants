package com.ke.assistant.controller;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.OpenapiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 全局异常处理器拦截器
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler implements HandlerExceptionResolver {

    private final ObjectMapper objectMapper;

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, 
                                       Object handler, Exception ex) {
        log.error(request.getRequestURI() + " with error: " + ex.getMessage(), ex);
        try {
            if(ex instanceof ChannelException ce) {
                handleChannelException(response, ce);
            } else if(ex instanceof MethodArgumentNotValidException manve) {
                handleValidationException(response, manve);
            } else if(ex instanceof ConstraintViolationException cve) {
                handleConstraintViolationException(response, cve);
            } else if(ex instanceof IllegalArgumentException iae) {
                handleIllegalArgumentException(response, iae);
            } else if(ex instanceof RuntimeException re) {
                handleRuntimeException(response, re);
            } else {
                handleGenericException(response, ex);
            }
        } catch (Exception e) {
            log.error("Failed to handle exception", e);
        }
        return new ModelAndView();
    }

    private void handleChannelException(HttpServletResponse response, ChannelException e) {
        log.error("ChannelException: {}", e.getMessage(), e);
        OpenapiResponse.OpenapiError error = e.convertToOpenapiError();
        writeErrorResponse(response, error, e.getHttpCode());
    }

    private void handleValidationException(HttpServletResponse response, MethodArgumentNotValidException e) {
        log.error("Validation error: {}", e.getMessage(), e);

        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");

        OpenapiResponse.OpenapiError error = new OpenapiResponse.OpenapiError("validation_error", message, HttpStatus.BAD_REQUEST.value());
        writeErrorResponse(response, error, HttpStatus.BAD_REQUEST.value());
    }

    private void handleConstraintViolationException(HttpServletResponse response, ConstraintViolationException e) {
        log.error("Constraint violation: {}", e.getMessage(), e);

        String message = e.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Constraint violation");

        OpenapiResponse.OpenapiError error = new OpenapiResponse.OpenapiError("constraint_violation", message, HttpStatus.BAD_REQUEST.value());
        writeErrorResponse(response, error, HttpStatus.BAD_REQUEST.value());
    }

    private void handleIllegalArgumentException(HttpServletResponse response, IllegalArgumentException e) {
        log.error("IllegalArgumentException: {}", e.getMessage(), e);

        OpenapiResponse.OpenapiError error = new OpenapiResponse.OpenapiError("invalid_request_error", e.getMessage(),
                HttpStatus.BAD_REQUEST.value());
        writeErrorResponse(response, error, HttpStatus.BAD_REQUEST.value());
    }

    private void handleRuntimeException(HttpServletResponse response, RuntimeException e) {
        log.error("RuntimeException: {}", e.getMessage(), e);

        OpenapiResponse.OpenapiError error = new OpenapiResponse.OpenapiError("api_error", "Internal server error",
                HttpStatus.INTERNAL_SERVER_ERROR.value());
        writeErrorResponse(response, error, HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    private void handleGenericException(HttpServletResponse response, Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);

        OpenapiResponse.OpenapiError error = new OpenapiResponse.OpenapiError("api_error", "An unexpected error occurred",
                HttpStatus.INTERNAL_SERVER_ERROR.value());
        writeErrorResponse(response, error, HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    private void writeErrorResponse(HttpServletResponse response, OpenapiResponse.OpenapiError error, int statusCode) {
        try {
            response.setStatus(statusCode);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            
            String jsonResponse = objectMapper.writeValueAsString(error);
            PrintWriter writer = response.getWriter();
            writer.write(jsonResponse);
            writer.flush();
        } catch (IOException ex) {
            log.error("Failed to write error response", ex);
        }
    }
}
