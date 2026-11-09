package com.abhedyam.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileUploadServiceTest {
    @Test
    void onlyOurCloudinaryUploadsAreDeleted() {
        assertArrayEquals(new String[]{"image", "abhedyam/abc123"},
                FileUploadService.cloudinaryTarget("https://res.cloudinary.com/x/image/upload/v1699/abhedyam/abc123.jpg"));
        assertArrayEquals(new String[]{"raw", "abhedyam/bill.pdf"},
                FileUploadService.cloudinaryTarget("https://res.cloudinary.com/x/raw/upload/v1/abhedyam/bill.pdf"));
        assertArrayEquals(new String[]{"image", "abhedyam/p"},
                FileUploadService.cloudinaryTarget("https://res.cloudinary.com/x/image/upload/c_fill,w_200/v1/abhedyam/p.png"));
        assertNull(FileUploadService.cloudinaryTarget("https://lh3.googleusercontent.com/a/photo.jpg"));
        assertNull(FileUploadService.cloudinaryTarget("https://res.cloudinary.com/x/image/upload/v1/other/abc.jpg"));
        assertNull(FileUploadService.cloudinaryTarget(null));
    }
}
