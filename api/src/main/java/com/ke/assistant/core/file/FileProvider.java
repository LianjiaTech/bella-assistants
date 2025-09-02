package com.ke.assistant.core.file;

import java.util.List;

public interface FileProvider {
    List<FileInfo> provide(List<String> fileIds);
}
