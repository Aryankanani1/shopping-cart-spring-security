package com.aryan.spring_security_demo.catalog;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Encoding;
import io.swagger.v3.oas.models.media.MediaType;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lists in the spec the image types an upload may be in ({@link ImageService#ALLOWED_TYPES}),
 * as the content type of each form's file part. Without them the spec allowed a file
 * of any type, and the staging fuzzing (Schemathesis), which sends a part in one of
 * the listed types, spent its uploads on ones the API refuses.
 */
@Component
class ImageUploadDocs implements OpenApiCustomizer {

    private final String prefix;

    ImageUploadDocs(@Value("${api.prefix}") String prefix) {
        this.prefix = prefix;
    }

    @Override
    public void customise(OpenAPI openApi) {
        // Sorted, so the spec is the same on every start (a Set's order isn't).
        String types = ImageService.ALLOWED_TYPES.stream().sorted().collect(Collectors.joining(", "));
        encode(openApi, prefix + "/images", PathItem::getPost, "files", types);
        encode(openApi, prefix + "/images/{imageId}", PathItem::getPut, "file", types);
    }

    private static void encode(OpenAPI openApi, String path, Function<PathItem, Operation> method,
                               String part, String types) {
        PathItem item = openApi.getPaths() == null ? null : openApi.getPaths().get(path);
        Operation operation = item == null ? null : method.apply(item);
        if (operation == null || operation.getRequestBody() == null || operation.getRequestBody().getContent() == null) {
            return;
        }
        MediaType form = operation.getRequestBody().getContent()
                .get(org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE);
        if (form != null) {
            form.addEncoding(part, new Encoding().contentType(types));
        }
    }
}
