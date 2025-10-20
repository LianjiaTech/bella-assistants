package com.ke.assistant.service;

import java.util.Base64;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ke.assistant.db.context.RepoContext;
import com.ke.bella.openapi.server.OpenAiServiceFactory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for handling audio storage and base64 conversion.
 * Supports saving base64 audio data to file-api and returning a public file id.
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
        String fileName = UUID.randomUUID() + fileExtension;
        if(RepoContext.isActive()) {
            return RepoContext.store().upload(fileName, audioBytes);
        }
        return openAiServiceFactory.create().uploadFile("storage", audioBytes, fileName).getId();
    }
}

