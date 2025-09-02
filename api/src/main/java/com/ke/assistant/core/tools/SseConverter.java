package com.ke.assistant.core.tools;

public interface SseConverter {
    String convert(String eventType, String msg);
}
