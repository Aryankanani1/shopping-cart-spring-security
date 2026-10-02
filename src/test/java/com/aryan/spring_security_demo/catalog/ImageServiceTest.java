package com.aryan.spring_security_demo.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Upload checks. Images are served back from the API's own origin with their
 * stored content type, so only real image types may be stored: an HTML or SVG
 * upload would otherwise run script there.
 */
@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    private static final byte[] BYTES = {1, 2, 3};

    @Mock private ImageRepository imageRepository;
    @Mock private ImagePersistenceService imagePersistenceService;

    @InjectMocks private ImageService imageService;

    @ParameterizedTest
    @ValueSource(strings = {"text/html", "image/svg+xml", "application/javascript", "application/octet-stream"})
    void saveImages_nonImageType_isRejectedAndNothingStored(String contentType) {
        List<MultipartFile> files = List.of(file("ok.png", "image/png"), file("bad", contentType));

        assertThatThrownBy(() -> imageService.saveImages(files, 1L)).isInstanceOf(InvalidImageException.class);
        verify(imagePersistenceService, never()).saveAll(anyList(), any());
    }

    @Test
    void saveImages_missingContentType_isRejected() {
        assertThatThrownBy(() -> imageService.saveImages(List.of(file("x.png", null)), 1L))
                .isInstanceOf(InvalidImageException.class);
    }

    @Test
    void saveImages_emptyFile_isRejected() {
        MultipartFile empty = new MockMultipartFile("files", "blank.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> imageService.saveImages(List.of(empty), 1L))
                .isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("blank.png is empty");
    }

    @Test
    void saveImages_validImages_arePersistedWithTheirNameAndType() {
        imageService.saveImages(List.of(file("a.png", "image/png"), file("b.webp", "image/webp")), 1L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Image>> images = ArgumentCaptor.forClass(List.class);
        verify(imagePersistenceService).saveAll(images.capture(), eq(1L));
        assertThat(images.getValue()).extracting(Image::getFileName, Image::getFileType)
                .containsExactly(tuple("a.png", "image/png"), tuple("b.webp", "image/webp"));
    }

    @Test
    void updateImage_nonImageType_isRejected() {
        assertThatThrownBy(() -> imageService.updateImage(file("x.html", "text/html"), 3L))
                .isInstanceOf(InvalidImageException.class);
        verify(imagePersistenceService, never()).replaceContent(any(), any(), any(), any());
    }

    @Test
    void deleteImage_missing_is404() {
        when(imageRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> imageService.deleteImageById(3L)).isInstanceOf(ImageNotFoundException.class);
    }

    private static MultipartFile file(String name, String contentType) {
        return new MockMultipartFile("files", name, contentType, BYTES);
    }
}
