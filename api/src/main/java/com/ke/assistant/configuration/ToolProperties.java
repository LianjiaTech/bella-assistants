package com.ke.assistant.configuration;

import lombok.Data;

@Data
public class ToolProperties {
    private RagToolProperties rag = new RagToolProperties();
    private WebSearchToolProperties webSearch = new WebSearchToolProperties();
    private WebSearchTavilyToolProperties webSearchTavily = new WebSearchTavilyToolProperties();
    private ImageGenerateToolProperties imageGenerate = new ImageGenerateToolProperties();
    private WebCrawlerToolProperties webCrawler = new WebCrawlerToolProperties();
    private WeatherSearchToolProperties weatherSearch = new WeatherSearchToolProperties();
    private WikiSearchToolProperties wikiSearch = new WikiSearchToolProperties();
    private ReadFilesToolProperties readFiles = new ReadFilesToolProperties();
    private ImgVisionToolProperties imgVision = new ImgVisionToolProperties();
    private RetrievalToolProperties retrieval = new RetrievalToolProperties();
    private BarToolProperties barTool = new BarToolProperties();
    private LineToolProperties lineTool = new LineToolProperties();
    private PieToolProperties pieTool = new PieToolProperties();
    
    @Data
    public static class RagToolProperties {
        private String url;
        private double score = 0.7;
        private int topK = 5;
        private String model = "gpt-4o";
        private boolean isFinal = false;
    }
    
    @Data
    public static class WebSearchToolProperties {
        private String url = "http://localhost:8080/search";
    }
    
    @Data
    public static class WebSearchTavilyToolProperties {
        private String url;
        private String apiKey;
        private String searchDepth = "advanced";
        private boolean includeRawContent = false;
        private int maxResults = 5;
    }
    
    @Data
    public static class ImageGenerateToolProperties {
        private String format = "jpg";
        private boolean isFinal = false;
    }
    
    @Data
    public static class WebCrawlerToolProperties {
        private String url;
        private String apiKey;
        private int timeout = 30; // 30秒
    }
    
    @Data
    public static class WeatherSearchToolProperties {
        private String url = "https://restapi.amap.com/v3/weather/weatherInfo";
        private String apiKey;
        private String units = "metric";
        private String language = "zh";
    }
    
    @Data
    public static class WikiSearchToolProperties {
        private String url = "https://zh.wikipedia.org/api/rest_v1/page/summary/";
        private String language = "zh";
        private int limit = 10;
    }
    
    @Data
    public static class ReadFilesToolProperties {
        private String url;
    }
    
    @Data
    public static class ImgVisionToolProperties {
        private String model = "gpt-4o";
        private boolean isFinal = false;
    }
    
    @Data
    public static class RetrievalToolProperties {
        private String url;
        private double score = 0.7;
        private int topK = 5;
        private String model = "text-embedding-ada-002";
        private boolean isFinal = false;
    }

    @Data
    public static class BarToolProperties {
        private int width = 800;
        private int height = 600;
        private boolean isFinal = true;
    }
    
    @Data
    public static class LineToolProperties {
        private int width = 800;
        private int height = 600;
        private boolean isFinal = true;
    }
    
    @Data
    public static class PieToolProperties {
        private int width = 800;
        private int height = 600;
        private boolean isFinal = true;
    }
}
