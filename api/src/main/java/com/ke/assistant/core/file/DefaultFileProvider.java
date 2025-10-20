package com.ke.assistant.core.file;

import java.util.ArrayList;
import java.util.List;

import com.ke.bella.openapi.server.OpenAiServiceFactory;
import com.theokanning.openai.file.File;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultFileProvider implements FileProvider {

    private final OpenAiServiceFactory openAiServiceFactory;

    public DefaultFileProvider(OpenAiServiceFactory openAiServiceFactory) {
        this.openAiServiceFactory = openAiServiceFactory;
    }

    @Override
    public List<FileInfo> provide(List<String> fileIds) {
        List<FileInfo> result = new ArrayList<>();
        for(String fileId : fileIds) {
            try {
                File file = openAiServiceFactory.create().retrieveFile(fileId);
                if(file != null) {
                    FileInfo fileInfo = FileInfo.builder()
                            .id(fileId)
                            .name(file.getFilename())
                            .build();
                    result.add(fileInfo);
                }
            } catch (Exception e) {
                log.warn(e.getMessage(), e);
            }
        }
        return result;
    }
}
