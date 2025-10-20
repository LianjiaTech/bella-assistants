package com.ke.assistant.core.tools;

import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.NotNull;

import com.ke.bella.openapi.protocol.BellaEventSourceListener;

import okhttp3.Response;
import okhttp3.sse.EventSource;

public class ToolCallListener extends BellaEventSourceListener {

    final String toolCallId;
    final ToolOutputChannel toolOutputChannel;
    final SseConverter converter;
    final CompletableFuture<String> finishFuture;
    final StringBuilder output;
    final boolean send;
    boolean finish;

    public ToolCallListener(String toolCallId, ToolOutputChannel toolOutputChannel, SseConverter converter, boolean send) {
        super();
        this.toolCallId = toolCallId;
        this.toolOutputChannel = toolOutputChannel;
        this.converter = converter;
        this.send = send;
        finishFuture = new CompletableFuture<>();
        output = new StringBuilder();
    }

    @Override
    public void onEvent(@NotNull EventSource eventSource, String id, String type, String msg) {
        String chunk = converter.convert(type, msg);
        if(chunk == null) {
            return;
        }
        send(chunk);
        output.append(chunk);
    }

    @Override
    public void onClosed(@NotNull EventSource eventSource) {
        if(!finish) {
            send("[TOOL_DONE]");
            finish = true;
            finishFuture.complete(output.toString());
        }
    }

    @Override
    public void onFailure(@NotNull EventSource eventSource, Throwable t, Response response) {
        if(finish) {
            return;
        }

        if(t != null) {
            send(t.getMessage());
            finishFuture.completeExceptionally(t);
        } else if(response != null) {
            try {
                if(response.body() != null) {
                    String body = response.body().string();
                    send(body);
                    finishFuture.completeExceptionally(new RuntimeException(body));
                } else {
                    send(response.message());
                    finishFuture.completeExceptionally(new RuntimeException(response.message()));
                }
            } catch (Exception e) {
                send(response.message());
                finishFuture.completeExceptionally(new RuntimeException(response.message()));
            }
        }
        send("[TOOL_DONE]");
        finish = true;
    }

    public String getOutput() {
        try {
            return finishFuture.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void send(String msg) {
        if(send) {
            toolOutputChannel.output(toolCallId, msg);
        }
    }
}
