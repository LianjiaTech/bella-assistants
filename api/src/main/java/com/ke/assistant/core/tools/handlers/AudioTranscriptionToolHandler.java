package com.ke.assistant.core.tools.handlers;

import com.google.common.collect.Lists;
import com.ke.assistant.configuration.AssistantProperties;
import com.ke.assistant.configuration.ToolProperties;
import com.ke.assistant.core.tools.BellaToolHandler;
import com.ke.assistant.core.tools.ToolContext;
import com.ke.assistant.core.tools.ToolOutputChannel;
import com.ke.assistant.core.tools.ToolResult;
import com.ke.bella.openapi.server.OpenAiServiceFactory;
import com.theokanning.openai.audio.CreateTranscriptionRequest;
import com.theokanning.openai.audio.TranscriptionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class AudioTranscriptionToolHandler extends BellaToolHandler {
    @Autowired
    private AssistantProperties assistantProperties;
    @Autowired
    private OpenAiServiceFactory openAiServiceFactory;

    private ToolProperties.AudioTranscriptionToolProperties transcriptionToolProperties;

    @PostConstruct
    public void init() {
        this.transcriptionToolProperties = assistantProperties.getTools().getAudioTranscription();
    }

    @Override
    public ToolResult doExecute(ToolContext context, Map<String, Object> arguments, ToolOutputChannel channel) {
        String fileId = arguments.containsKey("file_id") ? arguments.get("file_id").toString() : null;
        String format = arguments.containsKey("format") ? arguments.get("format").toString() : null;
        if(fileId == null) {
            throw new IllegalArgumentException("file_id is null");
        }
        if(format == null) {
            throw new IllegalArgumentException("format is null");
        }
        File tempFile = null;
        try {
            tempFile = File.createTempFile("audio-", "." + format);
            openAiServiceFactory.create().retrieveFileContentAndSave(fileId, tempFile.toPath());
            CreateTranscriptionRequest req = CreateTranscriptionRequest.builder()
                    .model(transcriptionToolProperties.getModel())
                    .responseFormat("json")
                    .build();
            TranscriptionResult result = openAiServiceFactory.create().createTranscription(req, tempFile);
            String transcription = result != null ? result.getText() : "转录音频失败";
            return ToolResult.builder()
                    .message(transcription)
                    .build();
        } catch (Exception e) {
            log.warn("Audio transcription failed:{}", e.getMessage(), e);
            return ToolResult.builder()
                    .error(e.getMessage())
                    .build();
        } finally {
            if (tempFile != null && tempFile.exists()) {
                // noinspection ResultOfMethodCallIgnored
                tempFile.delete();
            }
        }
    }

    @Override
    public String getToolName() {
        return "audio_transcription";
    }

    @Override
    public String getDescription() {
        return "将音频文件转录为文本。支持多种音频格式，如mp3、mp4、mpeg、mpga、m4a、wav、webm等。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        // file_id 参数 (必需)
        Map<String, Object> fileIdParam = new HashMap<>();
        fileIdParam.put("type", "string");
        fileIdParam.put("description", "要转录的音频文件ID");
        properties.put("file_id", fileIdParam);

        // format 参数 (必需)
        Map<String, Object> formatParam = new HashMap<>();
        formatParam.put("type", "string");
        formatParam.put("description", "音频文件格式，支持mp3、mp4、mpeg、mpga、m4a、wav、webm等，默认为wav");
        formatParam.put("enum", Lists.newArrayList("mp3", "mp4", "mpeg", "mpga", "m4a", "wav", "webm"));
        properties.put("format", formatParam);

        parameters.put("properties", properties);
        parameters.put("required", Lists.newArrayList("file_id", "format"));

        return parameters;
    }

    @Override
    public boolean isFinal() {
        return false;
    }
}
