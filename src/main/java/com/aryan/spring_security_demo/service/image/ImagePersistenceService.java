package com.aryan.spring_security_demo.service.image;

import com.aryan.spring_security_demo.dto.ImageDto;
import com.aryan.spring_security_demo.exception.ImageNotFoundException;
import com.aryan.spring_security_demo.model.Image;
import com.aryan.spring_security_demo.model.Product;
import com.aryan.spring_security_demo.repository.ImageRepository;
import com.aryan.spring_security_demo.service.product.ProductServiceInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Blob;
import java.util.ArrayList;
import java.util.List;

/**
 * DB-only persistence for images. The heavy file I/O (reading the upload bytes
 * and building the {@link Blob}) is done by {@link ImageService} <em>before</em>
 * these methods run, so each transaction here is short and never holds a pooled
 * connection open across a slow read.
 */
@Service
@RequiredArgsConstructor
public class ImagePersistenceService {

    private static final String DOWNLOAD_URL_PREFIX = "/api/v1/images/image/download/";

    private final ImageRepository imageRepository;
    private final ProductServiceInterface productServiceInterface;

    @Transactional
    public List<ImageDto> saveAll(List<Image> images, Long productId) {
        Product product = productServiceInterface.getProductById(productId);
        List<ImageDto> savedImageDtos = new ArrayList<>();
        for (Image image : images) {
            image.setProduct(product);
            // First save -> get the id, then set the download URL and save again.
            Image savedImage = imageRepository.save(image);
            savedImage.setURL(DOWNLOAD_URL_PREFIX + savedImage.getId());
            imageRepository.save(savedImage);
            savedImageDtos.add(toDto(savedImage));
        }
        return savedImageDtos;
    }

    @Transactional
    public void replaceContent(Long imageId, String fileName, String fileType, Blob content) {
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new ImageNotFoundException("image not found"));
        image.setFileName(fileName);
        image.setFileType(fileType);
        image.setImage(content);
        imageRepository.save(image);
    }

    private ImageDto toDto(Image image) {
        ImageDto imageDto = new ImageDto();
        imageDto.setImageId(image.getId());
        imageDto.setImageName(image.getFileName());
        imageDto.setDownloadUrl(image.getURL());
        return imageDto;
    }
}
