package com.ke.assistant.enums;

public enum MemoryType {
    LONG_MEMORY("LongMemory"),
    SHORT_MEMORY("ShortMemory"),
    MIX_MEMORY("MixMemory");

    private final String value;

    MemoryType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}