package com.aryan.spring_security_demo.security.config;

import com.aryan.spring_security_demo.dto.ImageDto;
import com.aryan.spring_security_demo.dto.OrderDto;
import com.aryan.spring_security_demo.dto.ProductDto;
import com.aryan.spring_security_demo.model.Image;
import com.aryan.spring_security_demo.model.Order;
import com.aryan.spring_security_demo.model.Product;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class shopConfig {

    @Bean
    public ModelMapper modelMapper()
    {
        ModelMapper modelMapper = new ModelMapper();

        // Order field names don't match OrderDto, so map them explicitly.
        modelMapper.typeMap(Order.class, OrderDto.class).addMappings(mapper -> {
            mapper.map(Order::getLocalDate, OrderDto::setOrderDate);
            mapper.map(Order::getOrderStatus, OrderDto::setStatus);
            mapper.map(Order::getOrderItems, OrderDto::setItems);
        });

        // Product.imageList -> ProductDto.images (needed for nested cart mapping).
        modelMapper.typeMap(Product.class, ProductDto.class).addMappings(mapper ->
            mapper.map(Product::getImageList, ProductDto::setImages));

        // Image field names don't match ImageDto, so map them explicitly.
        modelMapper.typeMap(Image.class, ImageDto.class).addMappings(mapper -> {
            mapper.map(Image::getId, ImageDto::setImageId);
            mapper.map(Image::getFileName, ImageDto::setImageName);
            mapper.map(Image::getURL, ImageDto::setDownloadUrl);
        });

        return modelMapper;
    }
}
