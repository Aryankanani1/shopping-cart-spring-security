package com.aryan.spring_security_demo.Service.product;

import com.aryan.spring_security_demo.exception.ProductNotFoundException;
import com.aryan.spring_security_demo.model.Category;
import com.aryan.spring_security_demo.model.Product;
import com.aryan.spring_security_demo.repository.ProductRepository;
import com.aryan.spring_security_demo.request.AddProductRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
@RequiredArgsConstructor
public class ProductService implements ProductServiceInterface{

    private final ProductRepository productRepository;
    @Override
    public Product addProduct(AddProductRequest product) {
        return null;
    }

    private Product createProduct(AddProductRequest productRequest, Category category){
        return new Product(
                productRequest.getName(),
                productRequest.getPrice(),
                productRequest.getBrand(),
                productRequest.getDescription(),
                productRequest.getInventory(),
                productRequest.getCategory()
        );
    }

    @Override
    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("product not found") );
    }

    @Override
    public void deleteProductById(Long productId) {
productRepository.findById(productId)
        .ifPresentOrElse(productRepository::delete,
                () ->
                {
                    new ProductNotFoundException("product not found ");
        });
    }

    @Override
    public void updateProductById(Product product, Long productId) {

    }

    @Override
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @Override
    public List<Product> getAllProductsByCategory(String category) {
        return productRepository.findByCategoryName(category);
    }

    @Override
    public List<Product> getProductsByBrand(String brand) {
        return productRepository.findByBrand(brand);
    }

    @Override
    public List<Product> getProductsByCategoryAndBrand(String category, String brand) {
        return productRepository.findByCategoryNameAndBrand(category,brand);
    }
    @Override
    public List<Product> getProductsByName(String name) {
        return productRepository.findByName(name);
    }

    @Override
    public List<Product> getProductsByBrandAndName(String brand, String name) {
        return productRepository.findByBrandAndName(brand,name);
    }

    @Override
    public Long countProductsByBrandAndName(String brand, String name) {
        return productRepository.countByBrandAndName(brand,name);
    }
}
