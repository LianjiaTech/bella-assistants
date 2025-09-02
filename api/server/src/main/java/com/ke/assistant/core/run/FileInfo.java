package com.ke.assistant.core.run;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileInfo {
    private String id;
    private String name;
    private String abstractInfo;
}
