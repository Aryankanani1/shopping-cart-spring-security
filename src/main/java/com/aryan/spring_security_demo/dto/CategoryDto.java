package com.aryan.spring_security_demo.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

/**
 * API view of a {@link com.aryan.spring_security_demo.model.Category}. Only the
 * fields a client needs are exposed — deliberately no {@code version} and no
 * {@code productList}, so the response can never recurse or trigger lazy loading.
 */
@Data
@JsonPropertyOrder({"id", "name"})
public class CategoryDto {
    private Long id;
    private String name;
}
