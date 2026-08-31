package com.aryan.spring_security_demo.controller;

import com.aryan.spring_security_demo.service.image.ImageServiceInterface;
import com.aryan.spring_security_demo.dto.ImageDto;
import com.aryan.spring_security_demo.model.Image;
import com.aryan.spring_security_demo.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.sql.SQLException;
import java.util.List;

import static org.springframework.http.HttpStatus.CREATED;

@RequiredArgsConstructor
@RestController
@RequestMapping("${api.prefix}/images")
public class ImageController {

    private final ImageServiceInterface imageServiceInterface;

    @PostMapping
    public ResponseEntity<ApiResponse<?>> saveImages(@RequestParam List<MultipartFile> files, @RequestParam Long productId){
        List<ImageDto> imageDtos = imageServiceInterface.saveImages(files, productId);
        return ResponseEntity.status(CREATED).body(new ApiResponse<>("Uploaded Successfully", imageDtos));
    }

    @GetMapping("/{imageId}")
    public ResponseEntity<Resource> downloadImage(@PathVariable Long imageId) throws SQLException {
        Image image = imageServiceInterface.getImageById(imageId);
        ByteArrayResource byteArrayResource = new ByteArrayResource(image.getImage()
                .getBytes(1, (int) image.getImage().length()));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getFileType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;  filename=\"" + image
                        .getFileName() + "\"")
                .body(byteArrayResource);
    }

    @PutMapping("/{imageId}")
    public ResponseEntity<ApiResponse<?>> updateImage(@PathVariable Long imageId, @RequestBody MultipartFile file){
        imageServiceInterface.getImageById(imageId); // 404 (via global handler) if it doesn't exist
        imageServiceInterface.updateImage(file, imageId);
        return ResponseEntity.ok(new ApiResponse<>("update success!", null));
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<ApiResponse<?>> deleteImage(@PathVariable Long imageId){
        imageServiceInterface.getImageById(imageId); // 404 (via global handler) if it doesn't exist
        imageServiceInterface.deleteImageById(imageId);
        return ResponseEntity.noContent().build();
    }
}
