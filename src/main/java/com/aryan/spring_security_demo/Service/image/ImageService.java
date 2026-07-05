package com.aryan.spring_security_demo.Service.image;

import com.aryan.spring_security_demo.Service.product.ProductServiceInterface;
import com.aryan.spring_security_demo.dto.ImageDto;
import com.aryan.spring_security_demo.exception.CategoryNotFoundException;
import com.aryan.spring_security_demo.exception.ImageNotFoundException;
import com.aryan.spring_security_demo.model.Image;
import com.aryan.spring_security_demo.model.Product;
import com.aryan.spring_security_demo.repository.ImageRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.annotations.processing.SQL;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.rowset.serial.SerialBlob;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ImageService implements ImageServiceInterface {

    private final ImageRepository imageRepository;
    private final ProductServiceInterface productServiceInterface;

    @Override
    public Image getImageById(Long id) {
        return imageRepository.findById(id).orElseThrow(() -> new ImageNotFoundException("Image not found"));
    }

    @Override
    public void deleteImageById(Long id) {
        // find the image by id
        // after find it then delete it
        imageRepository.findById(id).ifPresentOrElse(imageRepository::delete,() -> {
            throw new ImageNotFoundException("image not found exception");
        });
    }

    @Override
    public List<ImageDto> saveImages(List<MultipartFile> files, Long productId) {

// 1. Build the entity — Create an Image and copy in the file's metadata (filename, content type)
// plus the raw bytes wrapped as a SerialBlob, and link it to the parent product.
        // 2. First save -> get the id
        // 3. set the download URL
        // 4. Second save -> store the URL
        // 5. Map to DTo
        Product product = productServiceInterface.getProductById(productId);
        List<ImageDto> savedImageDtos = new ArrayList<>();
        String downloadUrlPrefix = "/api/v1/images/image/download/";

        for (MultipartFile file : files) {
            try {
                Image image = new Image();
                image.setFileName(file.getOriginalFilename());
                image.setFileType(file.getContentType());
                image.setImage(new SerialBlob(file.getBytes()));
                image.setProduct(product);

                Image savedImage = imageRepository.save(image);
                savedImage.setURL(downloadUrlPrefix + savedImage.getId());
                imageRepository.save(savedImage);

                ImageDto imageDto = new ImageDto();
                imageDto.setImageId(savedImage.getId());
                imageDto.setImageName(savedImage.getFileName());
                imageDto.setDownloadUrl(savedImage.getURL());
                savedImageDtos.add(imageDto);

            } catch (IOException | SQLException e) {
                throw new RuntimeException("Failed to save image: " + file.getOriginalFilename(), e);
            }
        }
        return savedImageDtos;
    }


    @Override
    public void updateImage(MultipartFile file, Long imageId) {
    // find the image by the id,
        // then update the image
       Image image = getImageById(imageId);
       try {
           image.setFileType(file.getOriginalFilename());
           image.setFileType(file.getOriginalFilename());
           image.setImage(new SerialBlob(file.getBytes()));
           imageRepository.save(image);
       }
       catch (IOException | SQLException e){
           throw new RuntimeException(e.getMessage());
       }
    }
}
