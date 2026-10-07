package com.aryan.spring_security_demo.common.config;

import org.modelmapper.ModelMapper;

/**
 * A module's entity-to-DTO mappings, for the fields whose names differ. Each
 * module registers the type maps for its own classes as a bean of this type, and
 * {@link ModelMapperConfig} applies all of them, so no module has to know
 * another's DTOs. Annotate with {@code @Order} when one module's map nests
 * another's (the cart's line items nest catalog's product).
 */
@FunctionalInterface
public interface ModelMapperCustomizer {

    void customize(ModelMapper modelMapper);
}
