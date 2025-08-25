package com.ke.assistant.core.plan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * 规划器决策结果
 * 封装规划器的决策和执行参数
 */
@AllArgsConstructor
@Builder
@Data
public class PlannerDecision {
    
    private final PlannerAction action;
    private final String reason;

    public static PlannerDecision init() {
        return builder().action(PlannerAction.INIT).build();
    }
    
    public static PlannerDecision waitForInput(String reason) {
        return builder().action(PlannerAction.WAIT_FOR_INPUT)
                .reason(reason)
                .build();
    }


    public static PlannerDecision waitForTool(String reason) {
        return builder().action(PlannerAction.WAIT_FOR_TOOL)
                .reason(reason)
                .build();
    }

    
    public static PlannerDecision complete(String reason) {
        return builder().action(PlannerAction.COMPLETE)
                .reason(reason)
                .build();
    }

    public static PlannerDecision error(String reason) {
        return builder().action(PlannerAction.ERROR)
                .reason(reason)
                .build();
    }

    public static PlannerDecision canceled(String reason) {
        return builder().action(PlannerAction.CANCELED)
                .reason(reason)
                .build();
    }

    public static PlannerDecision expired(String reason) {
        return builder().action(PlannerAction.EXPIRED)
                .reason(reason)
                .build();
    }

    public boolean needExecution() {
        return !action.isStop();
    }
}
