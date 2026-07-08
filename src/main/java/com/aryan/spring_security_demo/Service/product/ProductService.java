package com.aryan.spring_security_demo.Service.product;

import com.aryan.spring_security_demo.exception.ProductNotFoundException;
import com.aryan.spring_security_demo.model.Category;
import com.aryan.spring_security_demo.model.Product;
import com.aryan.spring_security_demo.repository.CategoryRepository;
import com.aryan.spring_security_demo.repository.ProductRepository;
import com.aryan.spring_security_demo.request.AddProductRequest;
import com.aryan.spring_security_demo.request.ProductUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductService implements ProductServiceInterface{

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    @Override
    public Product addProduct(AddProductRequest request) {
        // check if category is in DB or not
       Category category = Optional.ofNullable(categoryRepository.findByName(request.getCategory().getName()))
                         .orElseGet(() -> {
                   Category newCategory = new Category(request.getCategory().getName());
                   return categoryRepository.save(newCategory);
               });

       request.setCategory(category);
       return  productRepository.save(createProduct(request,category));
        // if yes set it as the new product category
        // if no set as the new category
        // and then set the product into that category
    }

    private Product createProduct(AddProductRequest productRequest, Category category){
        return new Product(
                productRequest.getName(),
                productRequest.getPrice(),
                productRequest.getDescription(),
                productRequest.getBrand(),
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
                  throw new ProductNotFoundException("product not found ");
        });
    }

    @Override
    public Product updateProductById(ProductUpdateRequest request, Long productId) {
          return productRepository.findById(productId)
                  .map(existingproduct -> updateExistingProduct(existingproduct,request))
                  .map(productRepository::save).orElseThrow(() -> new ProductNotFoundException("product not found exception"));



    }

    public Product updateExistingProduct(Product existingProduct, ProductUpdateRequest productUpdateRequest){
                existingProduct.setName(productUpdateRequest.getName());
                existingProduct.setBrand(productUpdateRequest.getBrand());
                existingProduct.setPrice(productUpdateRequest.getPrice());
                existingProduct.setDescription(productUpdateRequest.getDescription());
                existingProduct.setInventory(productUpdateRequest.getInventory());

                Category category = categoryRepository.findByName(productUpdateRequest.getCategory().getName());
                existingProduct.setCategory(category);
                return existingProduct;

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
