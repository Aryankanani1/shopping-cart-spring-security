package com.aryan.spring_security_demo.catalog;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ImageServiceInterface {
    Image getImageById(Long id);
    void deleteImageById(Long id);
    List<ImageDto> saveImages(List<MultipartFile> files, Long productId);
    void updateImage(MultipartFile file, Long imageId);



}
