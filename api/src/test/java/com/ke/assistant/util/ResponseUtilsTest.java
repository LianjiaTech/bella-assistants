package com.ke.assistant.util;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.theokanning.openai.response.CreateResponseRequest;
import com.theokanning.openai.response.InputValue;
import com.theokanning.openai.response.MessageRole;
import com.theokanning.openai.response.content.InputMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResponseUtilsTest {

    @Test
    void shouldRejectInputMessageWithoutContent() {
        InputMessage inputMessage = new InputMessage();
        inputMessage.setRole(MessageRole.USER);

        CreateResponseRequest request = new CreateResponseRequest();
        request.setInput(InputValue.of(List.of(inputMessage)));

        BizParamCheckException exception = assertThrows(BizParamCheckException.class,
                () -> ResponseUtils.checkAndConvertInputToMessages(request, null, null, null));

        assertEquals("input message content can not be null", exception.getMessage());
    }
}
