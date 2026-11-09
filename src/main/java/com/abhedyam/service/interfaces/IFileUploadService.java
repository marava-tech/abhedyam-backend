package com.abhedyam.service.interfaces;

import com.abhedyam.dto.FileUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;

public interface IFileUploadService {
    FileUploadResponse uploadFile(MultipartFile file);

    /** Deletes our own uploads (Cloudinary folder "abhedyam") among these URLs; other URLs are ignored. Never throws. */
    void deleteFiles(Collection<String> urls);
}

