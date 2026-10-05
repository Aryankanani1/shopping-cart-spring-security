package com.aryan.spring_security_demo.catalog;

import com.aryan.spring_security_demo.common.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
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
                // The uploaded name goes through the builder: it escapes quotes and
                // backslashes, and adds a filename* (RFC 6266) carrying the name in
                // UTF-8. Pasted in raw, a quote ended the filename early.
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(image.getFileName(), StandardCharsets.UTF_8)
                        .build().toString())
                .body(byteArrayResource);
    }

    /** Replace an image's file, sent as the multipart part {@code file}. */
    @PutMapping("/{imageId}")
    public ResponseEntity<ApiResponse<?>> updateImage(@PathVariable Long imageId, @RequestParam MultipartFile file){
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
