package com.ke.assistant.dto.memory;

import lombok.Data;

@Data
public class StrategyParam {
    private int turnNum = 3;
    private int topK = 5;
    private double threshold = 0.6;
}