package com.aryan.spring_security_demo.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.rowset.serial.SerialBlob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Download slice for {@link ImageController}. Security filters are disabled: who
 * may read images is covered by the security tests.
 */
@WebMvcTest(ImageController.class)
@AutoConfigureMockMvc(addFilters = false)
class ImageControllerTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImageServiceInterface imageService;

    // Regression: the uploaded name was pasted into the header unescaped, so a
    // quote ended the filename early and a backslash was read as an escape.
    @ParameterizedTest
    @ValueSource(strings = {"lamp.png", "desk \"lamp\".png", "back\\slash.png"})
    void download_quotesTheFileName(String fileName) throws Exception {
        String header = downloadHeader(fileName);

        assertThat(header).startsWith("attachment");
        assertThat(quotedFilename(header)).isEqualTo(fileName);
    }

    // Header bytes are ISO-8859-1, so a name outside it can't be sent raw; it
    // travels percent-encoded as UTF-8 in filename* instead.
    @Test
    void download_sendsANameOutsideLatin1Encoded() throws Exception {
        String header = downloadHeader("灯.png");

        assertThat(header.chars()).as(header).allMatch(c -> c <= 0xFF);
        assertThat(ContentDisposition.parse(header).getFilename()).isEqualTo("灯.png");
    }

    private String downloadHeader(String fileName) throws Exception {
        Image image = new Image();
        image.setFileName(fileName);
        image.setFileType("image/png");
        image.setImage(new SerialBlob(PNG));
        when(imageService.getImageById(5L)).thenReturn(image);

        return mockMvc.perform(get("/api/v1/images/5"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG))
                .andReturn().getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);
    }

    /** The {@code filename="..."} parameter, read strictly as an RFC 9110 quoted-string. */
    private static String quotedFilename(String header) {
        int start = header.indexOf("filename=\"");
        assertThat(start).as("filename parameter in " + header).isNotNegative();
        StringBuilder name = new StringBuilder();
        for (int i = start + "filename=\"".length(); i < header.length(); i++) {
            char c = header.charAt(i);
            if (c == '\\' && i + 1 < header.length()) {
                name.append(header.charAt(++i));
            } else if (c == '"') {
                return name.toString();
            } else {
                name.append(c);
            }
        }
        throw new AssertionError("unterminated filename in " + header);
    }
}
