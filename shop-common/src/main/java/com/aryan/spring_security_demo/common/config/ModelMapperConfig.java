package com.aryan.spring_security_demo.common.config;

import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * The one {@link ModelMapper}, with every module's type maps applied in their
 * {@code @Order} (see {@link ModelMapperCustomizer}).
 */
@Configuration(proxyBeanMethods = false)
public class ModelMapperConfig {

    @Bean
    public ModelMapper modelMapper(List<ModelMapperCustomizer> customizers) {
        ModelMapper modelMapper = new ModelMapper();
        customizers.forEach(customizer -> customizer.customize(modelMapper));
        return modelMapper;
    }
}
