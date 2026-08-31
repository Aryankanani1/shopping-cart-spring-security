package com.aryan.spring_security_demo.service.image;

import com.aryan.spring_security_demo.dto.ImageDto;
import com.aryan.spring_security_demo.exception.ImageNotFoundException;
import com.aryan.spring_security_demo.model.Image;
import com.aryan.spring_security_demo.repository.ImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.rowset.serial.SerialBlob;
import java.io.IOException;
import java.sql.Blob;
import java.sql.SQLException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ImageService implements ImageServiceInterface {

    private final ImageRepository imageRepository;
    private final ImagePersistenceService imagePersistenceService;

    @Override
    @Transactional(readOnly = true)
    public Image getImageById(Long id) {
        return imageRepository.findById(id).orElseThrow(() -> new ImageNotFoundException("Image not found"));
    }

    @Override
    @Transactional
    public void deleteImageById(Long id) {
        // find the image by id, then delete it
        imageRepository.findById(id).ifPresentOrElse(imageRepository::delete, () -> {
            throw new ImageNotFoundException("image not found exception");
        });
    }

    @Override
    public List<ImageDto> saveImages(List<MultipartFile> files, Long productId) {
        // Read the upload bytes and build the Blobs OUTSIDE any transaction, so the
        // slow file I/O never pins a pooled DB connection. Only the persistence step
        // (imagePersistenceService.saveAll) runs in a short transaction.
        List<Image> images = files.stream().map(this::readImage).toList();
        return imagePersistenceService.saveAll(images, productId);
    }

    @Override
    public void updateImage(MultipartFile file, Long imageId) {
        // Blob is read outside the transaction; the DB write is short-lived.
        Blob content = readBlob(file);
        imagePersistenceService.replaceContent(
                imageId, file.getOriginalFilename(), file.getContentType(), content);
    }

    private Image readImage(MultipartFile file) {
        Image image = new Image();
        image.setFileName(file.getOriginalFilename());
        image.setFileType(file.getContentType());
        image.setImage(readBlob(file));
        return image;
    }

    private Blob readBlob(MultipartFile file) {
        try {
            return new SerialBlob(file.getBytes());
        } catch (IOException | SQLException e) {
            throw new RuntimeException("Failed to read image: " + file.getOriginalFilename(), e);
        }
    }
}
