package com.ke.assistant.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ke.assistant.configuration.AssistantProperties;
import com.ke.assistant.configuration.S3Properties;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * S3文件上传服务
 */
@Slf4j
@Service
public class S3Service {
    @Autowired
    private AssistantProperties properties;
    private S3Properties s3Properties;
    private S3Client s3Client;

    @PostConstruct
    public void init() {
        s3Properties = properties.getS3();
        if (isConfigured()) {
            try {
                StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(s3Properties.getAccessKey(), s3Properties.getSecretKey())
                );

                S3ClientBuilder builder = S3Client.builder()
                    .region(Region.of(s3Properties.getRegion()))
                    .credentialsProvider(credentialsProvider);

                // 如果配置了自定义endpoint（比如MinIO）
                if(s3Properties.getEndpoint() != null && !s3Properties.getEndpoint().isBlank()) {
                    builder.endpointOverride(URI.create(s3Properties.getEndpoint()))
                           .forcePathStyle(s3Properties.isPathStyleAccess());
                }

                s3Client = builder.build();
                log.info("S3Client initialized successfully with bucket: {}", s3Properties.getBucketName());
            } catch (Exception e) {
                log.error("Failed to initialize S3Client", e);
            }
        } else {
            log.warn("S3 service not configured - file upload will be disabled");
        }
    }

    @PreDestroy
    public void cleanup() {
        if (s3Client != null) {
            s3Client.close();
            log.info("S3Client closed");
        }
    }

    /**
     * 检查S3服务是否已配置
     */
    public boolean isConfigured() {
        return s3Properties.isConfigured();
    }

    /**
     * 上传字节数组到S3
     * 
     * @param data 文件数据
     * @param contentType 文件类型
     * @param fileExtension 文件扩展名
     * @return 文件的公共访问URL
     */
    public String uploadFile(byte[] data, String contentType, String fileExtension) {
        if (!isConfigured()) {
            throw new RuntimeException("S3服务未配置。请设置以下环境变量：AWS_S3_BUCKET_NAME, AWS_S3_ACCESS_KEY, AWS_S3_SECRET_KEY");
        }
        
        if (s3Client == null) {
            throw new RuntimeException("S3客户端初始化失败，请检查S3配置参数");
        }

        String fileName = generateFileName(fileExtension);
        
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.getBucketName())
                .key(fileName)
                .contentType(contentType)
                .contentLength((long) data.length)
                .build();

            RequestBody requestBody = RequestBody.fromInputStream(
                new ByteArrayInputStream(data), data.length
            );

            PutObjectResponse response = s3Client.putObject(putObjectRequest, requestBody);
            
            String fileUrl = generateFileUrl(fileName);
            log.info("File uploaded successfully: {} (ETag: {})", fileName, response.eTag());
            
            return fileUrl;
            
        } catch (S3Exception e) {
            log.error("Failed to upload file to S3: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload file to S3: " + e.getMessage(), e);
        }
    }

    /**
     * 上传输入流到S3
     * 
     * @param inputStream 输入流
     * @param contentLength 内容长度
     * @param contentType 文件类型
     * @param fileExtension 文件扩展名
     * @return 文件的公共访问URL
     */
    public String uploadFile(InputStream inputStream, long contentLength, String contentType, String fileExtension) {
        if (!isConfigured()) {
            throw new RuntimeException("S3服务未配置。请设置以下环境变量：AWS_S3_BUCKET_NAME, AWS_S3_ACCESS_KEY, AWS_S3_SECRET_KEY");
        }
        
        if (s3Client == null) {
            throw new RuntimeException("S3客户端初始化失败，请检查S3配置参数");
        }

        String fileName = generateFileName(fileExtension);
        
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.getBucketName())
                .key(fileName)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();

            RequestBody requestBody = RequestBody.fromInputStream(inputStream, contentLength);

            PutObjectResponse response = s3Client.putObject(putObjectRequest, requestBody);
            
            String fileUrl = generateFileUrl(fileName);
            log.info("File uploaded successfully: {} (ETag: {})", fileName, response.eTag());
            
            return fileUrl;
            
        } catch (S3Exception e) {
            log.error("Failed to upload file to S3: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload file to S3: " + e.getMessage(), e);
        }
    }

    /**
     * 生成唯一的文件名
     */
    private String generateFileName(String fileExtension) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern(s3Properties.getUpload().getTimestampPattern()));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        
        // 根据文件扩展名确定文件夹
        String folder = fileExtension.toLowerCase().contains("png") || fileExtension.toLowerCase().contains("jpg") || fileExtension.toLowerCase().contains("jpeg") 
            ? s3Properties.getUpload().getImageFolder() : s3Properties.getUpload().getChartFolder();
            
        return String.format("%s/%s/%s%s", folder, timestamp, uuid, fileExtension);
    }

    /**
     * 生成文件的公共访问URL
     */
    private String generateFileUrl(String fileName) {
        // 如果配置了公共基础URL（如CDN），使用它
        if(s3Properties.getUrl().getPublicBaseUrl() != null && !s3Properties.getUrl().getPublicBaseUrl().isBlank()) {
            String baseUrl = s3Properties.getUrl().getPublicBaseUrl();
            baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            if(s3Properties.isPathStyleAccess()) {
                return String.format("%s/%s/%s", baseUrl, s3Properties.getBucketName(), fileName);
            }
            return String.format("%s/%s", baseUrl, fileName);
        }

        if(s3Properties.getEndpoint() != null && !s3Properties.getEndpoint().isBlank()) {
            // 自定义endpoint的情况（如MinIO）
            String baseUrl = s3Properties.getEndpoint().endsWith("/") ? s3Properties.getEndpoint().substring(0, s3Properties.getEndpoint().length() - 1) : s3Properties.getEndpoint();
            if (s3Properties.isPathStyleAccess()) {
                return String.format("%s/%s/%s", baseUrl, s3Properties.getBucketName(), fileName);
            } else {
                String protocol = s3Properties.getUrl().isHttpsEnabled() ? "https" : "http";
                return String.format("%s://%s.%s/%s", protocol, s3Properties.getBucketName(), baseUrl.replace("https://", "").replace("http://", ""), fileName);
            }
        } else {
            // AWS S3标准URL格式
            String protocol = s3Properties.getUrl().isHttpsEnabled() ? "https" : "http";
            return String.format("%s://%s.s3.%s.amazonaws.com/%s", protocol, s3Properties.getBucketName(), s3Properties.getRegion(), fileName);
        }
    }

    /**
     * 上传图片文件（专门用于图表）
     * 
     * @param imageData 图片数据
     * @param format 图片格式（jpg, png等），如果为null则使用默认格式
     * @return 图片的公共访问URL
     */
    public String uploadChart(byte[] imageData, String format) {
        String actualFormat = (format != null && !format.isBlank()) ? format : s3Properties.getUpload().getDefaultChartFormat();
        String contentType = "image/" + actualFormat.toLowerCase();
        String fileExtension = "." + actualFormat.toLowerCase();
        return uploadFile(imageData, contentType, fileExtension);
    }
}
