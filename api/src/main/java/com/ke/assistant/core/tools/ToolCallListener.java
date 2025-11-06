package com.ke.assistant.core.tools;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.jetbrains.annotations.NotNull;

import com.ke.bella.openapi.protocol.BellaEventSourceListener;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.sse.EventSource;

@Slf4j
public class ToolCallListener extends BellaEventSourceListener {

    final String toolCallId;
    final ToolOutputChannel toolOutputChannel;
    final SseConverter converter;
    final CompletableFuture<String> finishFuture;
    final StringBuilder output;
    final boolean send;
    final Function<String, Boolean> finishChecker;

    public ToolCallListener(String toolCallId, ToolOutputChannel toolOutputChannel, SseConverter converter, boolean send, Function<String, Boolean> finishChecker) {
        super();
        this.toolCallId = toolCallId;
        this.toolOutputChannel = toolOutputChannel;
        this.converter = converter;
        this.send = send;
        this.finishChecker = finishChecker;
        finishFuture = new CompletableFuture<>();
        output = new StringBuilder();
    }

    @Override
    public void onEvent(@NotNull EventSource eventSource, String id, String type, String msg) {
        String chunk = converter.convert(type, msg);
        if(chunk == null) {
            return;
        }
        if(finishChecker.apply(chunk)) {
            finishFuture.complete(output.toString());
            return;
        }
        output.append(chunk);
        send(chunk);
    }

    @Override
    public void onClosed(@NotNull EventSource eventSource) {
        if(!finishFuture.isDone()) {
            send("[TOOL_DONE]");
            finishFuture.complete(output.toString());
        }
    }

    @Override
    public void onFailure(@NotNull EventSource eventSource, Throwable t, Response response) {
        if(finishFuture.isDone()) {
            return;
        }

        try {
            if(t != null) {
                send(t.getMessage());
                finishFuture.completeExceptionally(t);
            } else if(response != null) {
                try {
                    String errorMsg;
                    if(response.body() != null) {
                        errorMsg = response.body().string();
                    } else {
                        errorMsg = response.message();
                    }
                    send(errorMsg);
                    finishFuture.completeExceptionally(new RuntimeException(errorMsg));
                } catch (Exception e) {
                    String msg = "Failed to read error response: " + e.getMessage();
                    log.error("Error reading response body", e);
                    send(msg);
                    finishFuture.completeExceptionally(new RuntimeException(msg));
                }
            } else {
                String msg = "SSE connection failed with unknown error";
                log.error(msg);
                send(msg);
                finishFuture.completeExceptionally(new RuntimeException(msg));
            }
        } catch (Exception ex) {
            log.error("Error in onFailure handler", ex);
            if (!finishFuture.isDone()) {
                finishFuture.completeExceptionally(new RuntimeException("onFailure handler error: " + ex.getMessage()));
            }
        } finally {
            if (!finishFuture.isDone()) {
                finishFuture.completeExceptionally(new RuntimeException("onFailure handler error"));
            }
            send("[TOOL_DONE]");
        }
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
