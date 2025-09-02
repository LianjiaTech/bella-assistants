package com.ke.assistant.core.file;

import com.theokanning.openai.file.File;
import com.theokanning.openai.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class DefaultFileProvider implements FileProvider {

    private final OpenAiService openAiService;

    public DefaultFileProvider(OpenAiService openAiService) {
        this.openAiService = openAiService;
    }

    @Override
    public List<FileInfo> provide(List<String> fileIds) {
        List<FileInfo> result = new ArrayList<>();
        for(String fileId : fileIds) {
            try {
                File file = openAiService.retrieveFile(fileId);
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
