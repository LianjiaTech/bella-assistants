package com.ke.assistant.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

/**
 * Assistant Tool 定义
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = Tool.ToolFunction.class, name = "function"),
        @JsonSubTypes.Type(value = Tool.ToolRetrieval.class, name = "retrieval"),
        @JsonSubTypes.Type(value = Tool.ToolRag.class, name = "rag"),
        @JsonSubTypes.Type(value = Tool.ToolWebSearch.class, name = "web_search"),
        @JsonSubTypes.Type(value = Tool.ToolWebSearchTavily.class, name = "web_search_tavily"),
        @JsonSubTypes.Type(value = Tool.ToolWeatherSearch.class, name = "weather_search"),
        @JsonSubTypes.Type(value = Tool.ToolImgVision.class, name = "img_vision"),
        @JsonSubTypes.Type(value = Tool.ToolImgGenerate.class, name = "img_generate"),
        @JsonSubTypes.Type(value = Tool.ToolBar.class, name = "generate_bar"),
        @JsonSubTypes.Type(value = Tool.ToolLine.class, name = "generate_line"),
        @JsonSubTypes.Type(value = Tool.ToolPie.class, name = "generate_pie"),
        @JsonSubTypes.Type(value = Tool.ToolWikiSearch.class, name = "wiki_search"),
        @JsonSubTypes.Type(value = Tool.ToolMyWeekReport.class, name = "my_week_report"),
        @JsonSubTypes.Type(value = Tool.ToolWeekReportToMe.class, name = "week_report_to_me"),
        @JsonSubTypes.Type(value = Tool.ToolReadFiles.class, name = "read_files"),
        @JsonSubTypes.Type(value = Tool.ToolWebCrawler.class, name = "web_crawler")
})
public abstract class Tool {

    @NotBlank
    public abstract String getType();

    /**
     * Function Tool
     */
    @Data
    public static class ToolFunction extends Tool {
        private String type = "function";
        private FunctionDefinition function;
        @JsonProperty("is_final")
        private Boolean isFinal = false;

        @Override
        public String getType() {
            return "function";
        }
    }

    /**
     * Retrieval Tool
     */
    @Data
    public static class ToolRetrieval extends Tool {
        private String type = "retrieval";
        @JsonProperty("default_metadata")
        private DefaultMetadata defaultMetadata;

        @Override
        public String getType() {
            return "retrieval";
        }
    }

    /**
     * RAG Tool
     */
    @Data
    public static class ToolRag extends Tool {
        private String type = "rag";
        @JsonProperty("default_metadata")
        private DefaultMetadata defaultMetadata;

        @Override
        public String getType() {
            return "rag";
        }
    }

    /**
     * Web Search Tool
     */
    @Data
    public static class ToolWebSearch extends Tool {
        private String type = "web_search";

        @Override
        public String getType() {
            return "web_search";
        }
    }

    /**
     * Web Search Tavily Tool
     */
    @Data
    public static class ToolWebSearchTavily extends Tool {
        private String type = "web_search_tavily";

        @Override
        public String getType() {
            return "web_search_tavily";
        }
    }

    /**
     * Weather Search Tool
     */
    @Data
    public static class ToolWeatherSearch extends Tool {
        private String type = "weather_search";

        @Override
        public String getType() {
            return "weather_search";
        }
    }

    /**
     * Image Vision Tool
     */
    @Data
    public static class ToolImgVision extends Tool {
        private String type = "img_vision";

        @Override
        public String getType() {
            return "img_vision";
        }
    }

    /**
     * Image Generate Tool
     */
    @Data
    public static class ToolImgGenerate extends Tool {
        private String type = "img_generate";

        @Override
        public String getType() {
            return "img_generate";
        }
    }

    /**
     * Bar Chart Tool
     */
    @Data
    public static class ToolBar extends Tool {
        private String type = "generate_bar";

        @Override
        public String getType() {
            return "generate_bar";
        }
    }

    /**
     * Line Chart Tool
     */
    @Data
    public static class ToolLine extends Tool {
        private String type = "generate_line";

        @Override
        public String getType() {
            return "generate_line";
        }
    }

    /**
     * Pie Chart Tool
     */
    @Data
    public static class ToolPie extends Tool {
        private String type = "generate_pie";

        @Override
        public String getType() {
            return "generate_pie";
        }
    }

    /**
     * Wiki Search Tool
     */
    @Data
    public static class ToolWikiSearch extends Tool {
        private String type = "wiki_search";

        @Override
        public String getType() {
            return "wiki_search";
        }
    }

    /**
     * My Week Report Tool
     */
    @Data
    public static class ToolMyWeekReport extends Tool {
        private String type = "my_week_report";

        @Override
        public String getType() {
            return "my_week_report";
        }
    }

    /**
     * Week Report To Me Tool
     */
    @Data
    public static class ToolWeekReportToMe extends Tool {
        private String type = "week_report_to_me";

        @Override
        public String getType() {
            return "week_report_to_me";
        }
    }

    /**
     * Read Files Tool
     */
    @Data
    public static class ToolReadFiles extends Tool {
        private String type = "read_files";

        @Override
        public String getType() {
            return "read_files";
        }
    }

    /**
     * Web Crawler Tool
     */
    @Data
    public static class ToolWebCrawler extends Tool {
        private String type = "web_crawler";

        @Override
        public String getType() {
            return "web_crawler";
        }
    }

    /**
     * Function Definition
     */
    @Data
    public static class FunctionDefinition {
        @NotBlank
        private String name;
        private String description;
        private Map<String, Object> parameters;
    }

    /**
     * Default Metadata for RAG/Retrieval tools
     */
    @Data
    public static class DefaultMetadata {
        @JsonProperty("top_k")
        private Integer topK = 3;
        private Double score = 0.8;
        @JsonProperty("empty_recall_reply")
        private String emptyRecallReply = "";
        @JsonProperty("metadata_filter")
        private List<Map<String, Object>> metadataFilter;
        @JsonProperty("retrieve_mode")
        private String retrieveMode = "fusion";
        private List<Map<String, Object>> plugins;
        private String instructions = "";
    }
}
