package com.abhedyam.service;

import com.abhedyam.dto.FileUploadResponse;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.service.interfaces.IFileUploadService;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadService implements IFileUploadService {
    
    private final Cloudinary cloudinary;
    private static final String FOLDER_NAME = "abhedyam";
    private static final String[] ALLOWED_IMAGE_TYPES = {
        "image/jpeg", "image/jpg", "image/png", "image/gif",
        "image/webp", "image/bmp", "image/svg+xml"
    };
    private static final String PDF_TYPE = "application/pdf";
    // .../<image|raw>/upload/[transforms/][v123/]abhedyam/<id>[.ext]
    private static final Pattern OUR_UPLOAD = Pattern.compile(
            "^https?://res\\.cloudinary\\.com/[^/]+/(image|raw)/upload/(?:[^/]+/)*?(" + FOLDER_NAME + "/[^?#]+)$");
    
    @Override
    public FileUploadResponse uploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("INVALID_FILE", "File is required");
        }
        
        String contentType = file.getContentType();
        boolean pdf = PDF_TYPE.equalsIgnoreCase(contentType);
        if (contentType == null || (!isImageType(contentType) && !pdf)) {
            throw new BusinessException("INVALID_FILE_TYPE",
                "Only image and PDF files are allowed. Supported formats: JPEG, JPG, PNG, GIF, WEBP, BMP, SVG, PDF");
        }
        
        try {
            Map<String, Object> uploadParams = ObjectUtils.asMap(
                "folder", FOLDER_NAME,
                "resource_type", pdf ? "raw" : "image"
            );
            
            Map<String, Object> uploadResult = cloudinary.uploader().upload(
                file.getBytes(),
                uploadParams
            );
            
            String url = (String) uploadResult.get("secure_url");
            String publicId = (String) uploadResult.get("public_id");
            
            log.info("File uploaded successfully: publicId={}, url={}", publicId, url);
            
            return new FileUploadResponse(url, publicId);
        } catch (IOException e) {
            log.error("Error uploading file to Cloudinary", e);
            throw new BusinessException("UPLOAD_FAILED", "Unable to upload file. Please try again");
        }
    }
    
    @Override
    public void deleteFiles(Collection<String> urls) {
        for (String url : urls) {
            String[] target = cloudinaryTarget(url);
            if (target == null) continue;
            try {
                cloudinary.uploader().destroy(target[1], ObjectUtils.asMap("resource_type", target[0]));
            } catch (Exception e) {
                log.warn("Cloudinary delete failed for {}: {}", target[1], e.getMessage());
            }
        }
    }

    /** {resourceType, publicId} for one of our uploads, else null. Image ids drop the extension, raw (PDF) ids keep it. */
    static String[] cloudinaryTarget(String url) {
        if (url == null) return null;
        Matcher m = OUR_UPLOAD.matcher(url.trim());
        if (!m.matches()) return null;
        String type = m.group(1), id = m.group(2);
        if (type.equals("image")) {
            int dot = id.lastIndexOf('.');
            if (dot > id.lastIndexOf('/')) id = id.substring(0, dot);
        }
        return new String[]{type, id};
    }

    private boolean isImageType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String lowerContentType = contentType.toLowerCase();
        for (String allowedType : ALLOWED_IMAGE_TYPES) {
            if (lowerContentType.equals(allowedType)) {
                return true;
            }
        }
        return false;
    }
}

