package com.aryan.spring_security_demo.catalog;

import com.aryan.spring_security_demo.common.config.ModelMapperCustomizer;
import org.modelmapper.ModelMapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Catalog's type maps. Ordered first: the cart's line items nest a product and
 * its images, so these must exist before the cart's maps are built.
 */
@Component
@Order(10)
public class CatalogMappings implements ModelMapperCustomizer {

    @Override
    public void customize(ModelMapper modelMapper) {
        // Product.imageList -> ProductDto.images (needed for nested cart mapping).
        modelMapper.typeMap(Product.class, ProductDto.class).addMappings(mapper ->
                mapper.map(Product::getImageList, ProductDto::setImages));

        // Image field names don't match ImageDto, so map them explicitly.
        modelMapper.typeMap(Image.class, ImageDto.class).addMappings(mapper -> {
            mapper.map(Image::getId, ImageDto::setImageId);
            mapper.map(Image::getFileName, ImageDto::setImageName);
            mapper.map(Image::getURL, ImageDto::setDownloadUrl);
        });
    }
}
