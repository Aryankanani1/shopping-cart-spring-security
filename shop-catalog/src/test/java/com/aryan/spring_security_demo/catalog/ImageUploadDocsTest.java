package com.aryan.spring_security_demo.catalog;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The two upload forms' file parts list the image types the API accepts. */
class ImageUploadDocsTest {

    private static final String FORM = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

    private final ImageUploadDocs docs = new ImageUploadDocs("/api/v1");

    @Test
    void eachFilePart_listsTheAcceptedTypes_sorted() {
        OpenAPI openApi = new OpenAPI().paths(new Paths()
                .addPathItem("/api/v1/images", new PathItem().post(upload()))
                .addPathItem("/api/v1/images/{imageId}", new PathItem().put(upload())));

        docs.customise(openApi);

        String types = "image/gif, image/jpeg, image/png, image/webp";
        assertThat(form(openApi, "/api/v1/images", true).getEncoding().get("files").getContentType()).isEqualTo(types);
        assertThat(form(openApi, "/api/v1/images/{imageId}", false).getEncoding().get("file").getContentType())
                .isEqualTo(types);
    }

    @Test
    void aSpecWithoutTheUploads_isLeftAlone() {
        OpenAPI openApi = new OpenAPI().paths(new Paths().addPathItem("/api/v1/images", new PathItem()));

        docs.customise(openApi);

        assertThat(openApi.getPaths().get("/api/v1/images").getPost()).isNull();
    }

    private static Operation upload() {
        return new Operation().requestBody(new RequestBody().content(new Content().addMediaType(FORM, new MediaType())));
    }

    private static MediaType form(OpenAPI openApi, String path, boolean post) {
        PathItem item = openApi.getPaths().get(path);
        return (post ? item.getPost() : item.getPut()).getRequestBody().getContent().get(FORM);
    }
}
