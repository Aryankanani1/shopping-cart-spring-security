package com.aryan.spring_security_demo.catalog;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

@Data
@JsonPropertyOrder({"imageId", "imageName", "downloadUrl"})
public class ImageDto {
    private Long imageId;
    private String imageName;
    private String downloadUrl;

}
