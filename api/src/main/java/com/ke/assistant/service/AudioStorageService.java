package com.ke.assistant.service;

import com.ke.bella.openapi.server.OpenAiServiceFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.UUID;

/**
 * Service for handling audio storage and base64 conversion.
 * Supports saving base64 audio inputs to S3 and returning a public URL.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AudioStorageService {

    private final OpenAiServiceFactory openAiServiceFactory;

    /**
     * upload audio input base64string
     * @return file_id
     */
    public String uploadAudio(String base64Data, String format) {
        byte[] audioBytes = Base64.getDecoder().decode(base64Data);
        String fileExtension = "." + format.toLowerCase();
        return openAiServiceFactory.create().uploadFile("storage", audioBytes, UUID.randomUUID() + fileExtension).getId();
    }
}

