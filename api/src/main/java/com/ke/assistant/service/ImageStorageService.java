package com.ke.assistant.service;

import com.ke.assistant.db.context.RepoContext;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for handling image storage and base64 conversion
 */
@Slf4j
@Service
public class ImageStorageService {

    private static final Pattern BASE64_IMAGE_PATTERN = Pattern.compile("^data:image/(\\w+);base64,(.+)$");

    @Autowired
    private S3Service s3Service;

    /**
     * Process image URL - if it's a valid URL, return it directly
     * If it's a base64 string, upload to S3 and return the S3 URL
     * Otherwise, throw a parameter validation exception
     *
     * @param imageUrl The image URL or base64 string
     * @return S3 URL if base64 was uploaded, otherwise the original URL
     * @throws BizParamCheckException if the input is neither a valid URL nor a base64 image
     */
    public String processImageUrl(String imageUrl) {
        if(RepoContext.isActive()) {
            return imageUrl;
        }

        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            throw new BizParamCheckException("Image URL cannot be null or empty");
        }

        // First check if it's a valid URL
        if (isValidUrl(imageUrl)) {
            // It's a valid URL, return as is
            return imageUrl;
        }

        // Check if it's a base64 image
        Matcher matcher = BASE64_IMAGE_PATTERN.matcher(imageUrl);
        if (!matcher.matches()) {
            // Neither a valid URL nor a base64 image
            throw new BizParamCheckException("Invalid image input: must be either a valid URL or base64 encoded image");
        }

        try {
            // Extract image format and base64 data
            String imageFormat = matcher.group(1);
            String base64Data = matcher.group(2);

            // Decode base64 to bytes
            byte[] imageData = Base64.getDecoder().decode(base64Data);

            // Determine content type and file extension
            String contentType = "image/" + imageFormat.toLowerCase();
            String fileExtension = "." + imageFormat.toLowerCase();

            // Upload to S3 and get URL
            String s3Url = s3Service.uploadFile(imageData, contentType, fileExtension);

            log.info("Successfully uploaded base64 image to S3: {} (size: {} bytes)", s3Url, imageData.length);

            return s3Url;

        } catch (Exception e) {
            log.error("Failed to upload base64 image to S3: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload image to S3: " + e.getMessage(), e);
        }
    }

    /**
     * Check if a string is a valid URL
     *
     * @param url The string to check
     * @return true if it's a valid URL, false otherwise
     */
    private boolean isValidUrl(String url) {
        try {
            new URL(url);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }
}
