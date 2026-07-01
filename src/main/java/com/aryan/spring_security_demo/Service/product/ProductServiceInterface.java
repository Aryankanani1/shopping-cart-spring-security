package com.aryan.spring_security_demo.Service.product;

import com.aryan.spring_security_demo.model.Product;
import com.aryan.spring_security_demo.request.AddProductRequest;

import java.util.List;

public interface ProductServiceInterface {
    Product addProduct(AddProductRequest product);
    Product getProductById(Long id);
    void deleteProductById(Long productId);
    void updateProductById(Product product, Long productId);

    List<Product> getAllProducts();
    List<Product> getAllProductsByCategory(String category);
    List<Product> getProductsByBrand(String brand);
    List<Product> getProductsByCategoryAndBrand(String category,String brand);
    List<Product> getProductsByName(String name);
    List<Product> getProductsByBrandAndName(String brand,String name);
    Long countProductsByBrandAndName(String brand,String name);


}
