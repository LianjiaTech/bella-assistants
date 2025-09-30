package com.ke.assistant.service;

import com.ke.assistant.db.generated.tables.pojos.RunDb;
import com.ke.assistant.db.repo.RunRepo;
import com.ke.assistant.util.MetaConstants;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Conversation Service
 */
@Service
public class ConversationService {

    @Autowired
    private RunRepo runRepo;

    public boolean isConversation(String threadId) {
       RunDb run = runRepo.findAnyByThreadId(threadId);
       if(run == null || StringUtils.isBlank(run.getMetadata())) {
           return false;
       }
       Map<String, String> meta = JacksonUtils.toMap(run.getMetadata());
       return meta.containsKey(MetaConstants.RESPONSE_ID);
    }
}
