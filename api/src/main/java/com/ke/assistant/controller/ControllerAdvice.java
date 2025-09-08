package com.ke.assistant.controller;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.Operator;
import com.theokanning.openai.assistants.IUssrRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Type;

@RestControllerAdvice
public class ControllerAdvice extends RequestBodyAdviceAdapter {
    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        if(body instanceof IUssrRequest) {
            if(((IUssrRequest) body).getUser() != null) {
                BellaContext.setOperator(Operator.builder().sourceId(((IUssrRequest) body).getUser()).build());
            }
        }
        return body;
    }

}

