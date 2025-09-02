package com.ke.assistant.configuration;

import lombok.Data;

/**
 * S3服务配置属性
 */
@Data
public class S3Properties {
    
    /**
     * S3存储桶名称
     */
    private String bucketName;
    
    /**
     * AWS区域
     */
    private String region = "us-east-1";
    
    /**
     * 访问密钥ID
     */
    private String accessKey;
    
    /**
     * 访问密钥
     */
    private String secretKey;
    
    /**
     * 自定义端点URL（用于MinIO等兼容S3的服务）
     */
    private String endpoint;
    
    /**
     * 是否使用路径样式访问
     */
    private boolean pathStyleAccess = false;
    
    /**
     * 文件上传配置
     */
    private UploadConfig upload = new UploadConfig();
    
    /**
     * 文件URL配置
     */
    private UrlConfig url = new UrlConfig();
    
    @Data
    public static class UploadConfig {
        /**
         * 图片文件夹名称
         */
        private String imageFolder = "images";
        
        /**
         * 图表文件夹名称
         */
        private String chartFolder = "charts";
        
        /**
         * 文件名时间戳格式
         */
        private String timestampPattern = "yyyyMMdd/HHmm";
        
        /**
         * 默认图表格式
         */
        private String defaultChartFormat = "jpg";
    }
    
    @Data
    public static class UrlConfig {
        /**
         * 公共访问基础URL（如果使用CDN）
         */
        private String publicBaseUrl;
        
        /**
         * 是否启用HTTPS
         */
        private boolean httpsEnabled = true;
    }
    
    /**
     * 检查S3服务是否已配置
     */
    public boolean isConfigured() {
        return bucketName != null && !bucketName.trim().isEmpty() &&
               accessKey != null && !accessKey.trim().isEmpty() &&
               secretKey != null && !secretKey.trim().isEmpty();
    }
}
