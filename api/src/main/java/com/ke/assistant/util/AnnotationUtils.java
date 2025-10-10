package com.ke.assistant.util;

import com.theokanning.openai.assistants.message.content.Annotation;
import com.theokanning.openai.assistants.message.content.FileCitation;
import com.theokanning.openai.assistants.message.content.FilePath;
import com.theokanning.openai.assistants.message.content.URLCitation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AnnotationUtils {

    public static Annotation buildFileRead(String fileId) {
        Annotation annotation = new Annotation();
        annotation.setType("file_path");
        FilePath path = new FilePath();
        path.setFileId(fileId);
        annotation.setFilePath(path);
        return annotation;
    }

    public static Annotation buildFileSearch(String fileId, String fileName, String chunkId, Double score, String fileTag) {
        Annotation annotation = new Annotation();
        annotation.setType("file_citation");
        FileCitation fileCitation = new FileCitation();
        fileCitation.setFileId(fileId);
        Map<String, String> metadata = new HashMap<>();
        if(fileName != null) {
            metadata.put("fileName", fileName);
        }
        if (chunkId != null) {
            metadata.put("chunkId", chunkId);
        }
        if (score != null) {
            metadata.put("score", score.toString());
        }
        if(fileTag != null) {
            metadata.put("fileTag", fileTag);
        }
        annotation.setFileCitation(fileCitation);
        annotation.setMetadata(metadata);
        return annotation;
    }

    public static Annotation buildWebSearch(String url, String title) {
        Annotation annotation = new Annotation();
        annotation.setType("url_citation");
        URLCitation urlCitation = new URLCitation();
        urlCitation.setUrl(url);
        urlCitation.setTitle(title);
        annotation.setUrlCitation(urlCitation);
        return annotation;
    }

    public static com.theokanning.openai.response.content.Annotation convertToResponseAnnotation(Annotation annotation) {
        if(annotation == null) {
            return null;
        }
        switch (annotation.getType()) {
        case "file_citation" -> {
                com.theokanning.openai.response.content.FileCitation fileCitation = new  com.theokanning.openai.response.content.FileCitation();
                if(annotation.getFileCitation() != null) {
                    fileCitation.setFileId(annotation.getFileCitation().getFileId());
                }
                if(annotation.getMetadata() != null) {
                    Map<String, String> metadata = annotation.getMetadata();
                    fileCitation.setFilename(metadata.get("fileName"));
                    fileCitation.setChunkId(metadata.get("chunkId"));
                    if(metadata.get("score") != null) {
                        fileCitation.setScore(Double.parseDouble(metadata.get("score")));
                    }
                    fileCitation.setFileTag(metadata.get("fileTag"));
                }
                return fileCitation;
        }
        case "url_citation" -> {
                com.theokanning.openai.response.content.URLCitation urlCitation = new com.theokanning.openai.response.content.URLCitation();
                if(annotation.getUrlCitation() != null) {
                    urlCitation.setUrl(annotation.getUrlCitation().getUrl());
                    urlCitation.setTitle(annotation.getUrlCitation().getTitle());
                }
                urlCitation.setStartIndex(annotation.getStartIndex());
                urlCitation.setEndIndex(annotation.getEndIndex());
                return urlCitation;
        }
        case "file_path" -> {
                com.theokanning.openai.response.content.FilePath filePath = new com.theokanning.openai.response.content.FilePath();
                if(annotation.getFilePath() != null) {
                    filePath.setFileId(annotation.getFilePath().getFileId());
                }
                return filePath;
        }
        case "container_file_citation" -> {
                com.theokanning.openai.response.content.ContainerFileCitation containerFileCitation = new com.theokanning.openai.response.content.ContainerFileCitation();
                if(annotation.getContainerFileCitation() != null) {
                    containerFileCitation.setFileId(annotation.getContainerFileCitation().getFileId());
                    containerFileCitation.setContainerId(annotation.getContainerFileCitation().getContainerId());
                }
                containerFileCitation.setStartIndex(annotation.getStartIndex());
                containerFileCitation.setEndIndex(annotation.getEndIndex());
                return containerFileCitation;
        }
        default -> {
                return null;
        }
        }
    }

    public static List<com.theokanning.openai.response.content.Annotation> convertToResponseAnnotations(List<Annotation> annotations) {
        if(annotations == null) {
            return new ArrayList<>();
        }
        return annotations.stream().map(AnnotationUtils::convertToResponseAnnotation).collect(Collectors.toList());
    }

    public static Annotation convertFromResponseAnnotation(com.theokanning.openai.response.content.Annotation responseAnnotation) {
        if(responseAnnotation == null) {
            return null;
        }

        Annotation annotation = new Annotation();

        if(responseAnnotation instanceof com.theokanning.openai.response.content.FileCitation fileCitation) {
            annotation.setType("file_citation");

            FileCitation fc = new FileCitation();
            fc.setFileId(fileCitation.getFileId());
            annotation.setFileCitation(fc);

            Map<String, String> metadata = new HashMap<>();
            if(fileCitation.getFilename() != null) {
                metadata.put("fileName", fileCitation.getFilename());
            }
            if(fileCitation.getChunkId() != null) {
                metadata.put("chunkId", fileCitation.getChunkId());
            }
            if(fileCitation.getScore() != null) {
                metadata.put("score", fileCitation.getScore().toString());
            }
            if(fileCitation.getFileTag() != null) {
                metadata.put("fileTag", fileCitation.getFileTag());
            }
            annotation.setMetadata(metadata);

        } else if(responseAnnotation instanceof com.theokanning.openai.response.content.URLCitation urlCitation) {
            annotation.setType("url_citation");

            URLCitation uc = new URLCitation();
            uc.setUrl(urlCitation.getUrl());
            uc.setTitle(urlCitation.getTitle());
            annotation.setUrlCitation(uc);

            annotation.setStartIndex(urlCitation.getStartIndex());
            annotation.setEndIndex(urlCitation.getEndIndex());

        } else if(responseAnnotation instanceof com.theokanning.openai.response.content.FilePath filePath) {
            annotation.setType("file_path");

            com.theokanning.openai.assistants.message.content.FilePath fp = new com.theokanning.openai.assistants.message.content.FilePath();
            fp.setFileId(filePath.getFileId());
            annotation.setFilePath(fp);

        } else if(responseAnnotation instanceof com.theokanning.openai.response.content.ContainerFileCitation containerFileCitation) {
            annotation.setType("container_file_citation");

            com.theokanning.openai.assistants.message.content.ContainerFileCitation cfc = new com.theokanning.openai.assistants.message.content.ContainerFileCitation();
            cfc.setFileId(containerFileCitation.getFileId());
            cfc.setContainerId(containerFileCitation.getContainerId());
            annotation.setContainerFileCitation(cfc);

            annotation.setStartIndex(containerFileCitation.getStartIndex());
            annotation.setEndIndex(containerFileCitation.getEndIndex());
        }

        return annotation;
    }

    public static List<Annotation> convertFromResponseAnnotations(List<com.theokanning.openai.response.content.Annotation> annotations) {
        if(annotations == null) {
            return new ArrayList<>();
        }
        return annotations.stream().map(AnnotationUtils::convertFromResponseAnnotation).collect(Collectors.toList());
    }
}
