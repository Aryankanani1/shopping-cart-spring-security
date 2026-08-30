package com.aryan.spring_security_demo.Service.product;

import com.aryan.spring_security_demo.dto.ProductDto;
import com.aryan.spring_security_demo.model.Product;
import com.aryan.spring_security_demo.request.AddProductRequest;
import com.aryan.spring_security_demo.request.ProductUpdateRequest;

import java.util.List;

public interface ProductServiceInterface {
    Product addProduct(AddProductRequest product);
    Product getProductById(Long id);
    void deleteProductById(Long productId);
    Product updateProductById(ProductUpdateRequest product, Long productId);
    List<Product> getAllProducts();
    List<Product> getAllProductsByCategory(String category);
    List<Product> getProductsByBrand(String brand);
    List<Product> getProductsByCategoryAndBrand(String category,String brand);
    List<Product> getProductsByName(String name);
    List<Product> getProductsByBrandAndName(String brand,String name);
    Long countProductsByBrandAndName(String brand,String name);

    ProductDto convertToDto(Product product);
    List<ProductDto> getConvertedProducts(List<Product> products);
    ProductDto convertDto(Product product);

    // DTO-returning read/write operations. Each loads AND maps inside one
    // transaction, so lazy associations (category, images) resolve while the
    // persistence context is open — safe to serialize with open-in-view off.
    ProductDto getProductDtoById(Long id);
    ProductDto addProductAndConvert(AddProductRequest request);
    ProductDto updateProductAndConvert(ProductUpdateRequest request, Long id);
    List<ProductDto> getAllProductDtos();
    List<ProductDto> getProductDtosByCategory(String category);
    List<ProductDto> getProductDtosByBrand(String brand);
    List<ProductDto> getProductDtosByCategoryAndBrand(String category, String brand);
    List<ProductDto> getProductDtosByName(String name);
    List<ProductDto> getProductDtosByBrandAndName(String brand, String name);
}
