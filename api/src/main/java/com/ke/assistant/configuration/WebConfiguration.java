package com.ke.assistant.configuration;

import com.ke.assistant.controller.GlobalExceptionHandler;
import com.ke.bella.openapi.server.intercept.AuthorizationInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web 配置类
 */
@Configuration
@RequiredArgsConstructor
public class WebConfiguration implements WebMvcConfigurer {

    private final GlobalExceptionHandler globalExceptionHandler;
    private final AuthorizationInterceptor authorizationInterceptor;
    private final ThreadIdValidationInterceptor threadIdValidationInterceptor;

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer
                .defaultContentType(MediaType.APPLICATION_JSON)
                .ignoreAcceptHeader(true);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authorizationInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/actuator/**", "/docs/**", "/swagger-ui/**", "/favicon.ico", "/error");

        // Validate threadId for Assistant API endpoints that include thread_id in the path
        registry.addInterceptor(threadIdValidationInterceptor)
                .addPathPatterns("/v1/threads/*", "/v1/threads/*/**");
    }

    @Override
    public void configureHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
        resolvers.add(globalExceptionHandler);
    }
}
