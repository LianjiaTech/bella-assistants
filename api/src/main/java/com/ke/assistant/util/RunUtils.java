package com.ke.assistant.util;

import com.ke.assistant.db.generated.tables.pojos.RunStepDb;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.Usage;
import com.theokanning.openai.assistants.run_step.RunStep;
import com.theokanning.openai.assistants.run_step.StepDetails;
import com.theokanning.openai.common.LastError;
import org.apache.commons.lang3.StringUtils;

import java.time.ZoneOffset;
import java.util.HashMap;

public class RunUtils {
    /**
     * 将RunStepDb转换为RunStepInfo
     */
    @SuppressWarnings("unchecked")
    public static RunStep convertStepToInfo(RunStepDb runDb) {
        if(runDb == null) {
            return null;
        }

        RunStep info = new RunStep();
        BeanUtils.copyProperties(runDb, info);

        info.setCreatedAt((int) runDb.getCreatedAt().toEpochSecond(ZoneOffset.ofHours(8)));

        info.setCreatedAt((int) runDb.getCreatedAt().toEpochSecond(ZoneOffset.ofHours(8)));
        if(runDb.getCancelledAt() != null) {
            info.setCancelledAt((int) runDb.getCancelledAt().toEpochSecond(ZoneOffset.ofHours(8)));
        }
        if(runDb.getExpiresAt() != null) {
            info.setExpiresAt((int) runDb.getExpiresAt().toEpochSecond(ZoneOffset.ofHours(8)));
        }

        if(StringUtils.isNotBlank(runDb.getLastError())) {
            info.setLastError(JacksonUtils.deserialize(runDb.getLastError(), LastError.class));
        }

        if(StringUtils.isNotBlank(runDb.getMetadata())) {
            info.setMetadata(JacksonUtils.toMap(runDb.getMetadata()));
        } else {
            info.setMetadata(new HashMap<>());
        }

        if(StringUtils.isNotBlank(runDb.getStepDetails())) {
            StepDetails stepDetails = JacksonUtils.deserialize(runDb.getStepDetails(), StepDetails.class);
            info.setStepDetails(stepDetails);
            if(StringUtils.isNotBlank(stepDetails.getText())) {
                info.getMetadata().put(MetaConstants.TEXT, stepDetails.getText());
            }
            if(StringUtils.isNotBlank(stepDetails.getReasoningContent())) {
                info.getMetadata().put(MetaConstants.REASONING, stepDetails.getReasoningContent());
            }
            if(StringUtils.isNotBlank(stepDetails.getReasoningContentSignature())) {
                info.getMetadata().put(MetaConstants.REASONING_SIG, stepDetails.getReasoningContentSignature());
            }
            if(StringUtils.isNotBlank(stepDetails.getRedactedReasoningContent())) {
                info.getMetadata().put(MetaConstants.REDACTED_REASONING, stepDetails.getRedactedReasoningContent());
            }
        }

        if(StringUtils.isNotBlank(runDb.getUsage())) {
            info.setUsage(JacksonUtils.deserialize(runDb.getUsage(), Usage.class));
        }

        return info;
    }
}
