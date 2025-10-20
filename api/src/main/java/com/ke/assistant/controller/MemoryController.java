package com.ke.assistant.controller;

import javax.validation.Valid;

import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ke.assistant.dto.memory.MemoryRequest;
import com.ke.assistant.dto.memory.MemoryResponse;
import com.ke.assistant.service.MemoryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryService memoryService;

    @PostMapping("/query")
    public MemoryResponse queryMemory(@Valid @RequestBody MemoryRequest request) {
        Assert.hasText(request.getThreadId(),  "ThreadId can not be empty");
        Assert.isTrue(!request.invalidQuery(), "Query can not be empty with long memory type");
        return memoryService.queryMemory(request);
    }
}
