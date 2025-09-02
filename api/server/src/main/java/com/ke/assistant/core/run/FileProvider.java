package com.ke.assistant.core.run;

import java.util.List;

public interface FileProvider {
    List<FileInfo> provide(List<String> fileIds);
}
