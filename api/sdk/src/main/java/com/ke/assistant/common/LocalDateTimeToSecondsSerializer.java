package com.ke.assistant.common;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * LocalDateTime 序列化器，将LocalDateTime转换为秒级时间戳
 */
public class LocalDateTimeToSecondsSerializer extends JsonSerializer<LocalDateTime> {

    @Override
    public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
        } else {
            // 将LocalDateTime转换为UTC时间戳（秒）
            long epochSecond = value.toEpochSecond(ZoneOffset.UTC);
            gen.writeNumber(epochSecond);
        }
    }
}