package com.aryan.spring_security_demo.controller;

import com.aryan.spring_security_demo.Service.image.ImageServiceInterface;
import com.aryan.spring_security_demo.dto.ImageDto;
import com.aryan.spring_security_demo.exception.ImageNotFoundException;
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

import static org.springframework.http.HttpStatus.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("${api.prefix}/images")
public class ImageController {

    private final ImageServiceInterface imageServiceInterface;

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse> saveImages(@RequestParam List<MultipartFile> files,@RequestParam Long productId){

            try {
                List<ImageDto> imageDtos = imageServiceInterface.saveImages(files, productId);
                return ResponseEntity.ok(new ApiResponse("Uploaded Successfully", imageDtos));

            }
            catch (Exception e){
                return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                        .body(new ApiResponse("build failed!",e.getMessage()));
            }
        }


    //downloadImage
    @GetMapping("/image/download/{imageId}")
    public ResponseEntity<Resource> downloadImage(@PathVariable Long imageId) throws SQLException {
        Image image = imageServiceInterface.getImageById(imageId);
        ByteArrayResource byteArrayResource = new ByteArrayResource(image.getImage()
                .getBytes(1, (int) image.getImage().length()));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getFileType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment;  filename=\"" +image
                .getFileName() +"\"")
                .body(byteArrayResource);


    }

    @PutMapping("/image/{imageId}/update")
    public ResponseEntity<ApiResponse> updateImage(@PathVariable Long imageId, @RequestBody MultipartFile file){
        try {
            // find the image
            Image image = imageServiceInterface.getImageById(imageId);
            // if image exists
            if (image != null) {
                imageServiceInterface.updateImage(file, imageId);
                return ResponseEntity.ok(new ApiResponse("update success!", null));
            }
        }
        catch (ImageNotFoundException e){
            return ResponseEntity.status(NOT_FOUND).body(new ApiResponse(e.getMessage(),null));
        }
        return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                .body(new ApiResponse("Update failed!",INTERNAL_SERVER_ERROR));
        }


    @DeleteMapping("/image/{imageId}/delete")
    public ResponseEntity<ApiResponse> deleteImage(@PathVariable Long imageId){
        try{
            Image image = imageServiceInterface.getImageById(imageId);
            if(image != null){
                imageServiceInterface.deleteImageById(imageId);
                return ResponseEntity.ok(new ApiResponse("delete success!",null));
            }

        }catch (ImageNotFoundException e){
            return ResponseEntity.status(NOT_FOUND).body(new ApiResponse(e.getMessage(),null));
        }
        return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                .body(new ApiResponse("delete failed!",INTERNAL_SERVER_ERROR));
    }
}
