package com.ke.assistant.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 通用分页响应
 */
@Data
public class CommonPage<T> {

    private String object = "list";

    private List<T> data;

    @JsonProperty("first_id")
    private String firstId;

    @JsonProperty("last_id")
    private String lastId;

    @JsonProperty("has_more")
    private boolean hasMore;

    public CommonPage() {
    }

    public CommonPage(List<T> data, String firstId, String lastId, boolean hasMore) {
        this.data = data;
        this.firstId = firstId;
        this.lastId = lastId;
        this.hasMore = hasMore;
    }
}
