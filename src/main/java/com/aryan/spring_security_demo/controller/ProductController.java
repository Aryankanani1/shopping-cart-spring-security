package com.aryan.spring_security_demo.controller;
import com.aryan.spring_security_demo.Service.product.ProductServiceInterface;
import com.aryan.spring_security_demo.exception.ResourceNotFoundException;
import com.aryan.spring_security_demo.model.Product;
import com.aryan.spring_security_demo.request.AddProductRequest;
import com.aryan.spring_security_demo.request.ProductUpdateRequest;
import com.aryan.spring_security_demo.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Products resource. Verbs are carried by the HTTP method and the collection is
 * filtered with query parameters (Richardson Maturity Model level 2):
 *
 * <pre>
 *   GET    /products                        list all
 *   GET    /products?brand=&name=           filter by brand + name
 *   GET    /products?category=&name=         filter by category + name
 *   GET    /products?name=                   filter by name
 *   GET    /products?brand=                  filter by brand
 *   GET    /products?category=               filter by category
 *   GET    /products/count?brand=&name=      count matching brand + name
 *   GET    /products/{id}                    fetch one
 *   POST   /products                         create        -> 201 + Location
 *   PUT    /products/{id}                     replace
 *   DELETE /products/{id}                     delete        -> 204
 * </pre>
 */
@RequiredArgsConstructor
@RestController
@Validated
@RequestMapping("${api.prefix}/products")
public class ProductController {

    private final ProductServiceInterface productServiceInterface;

    @GetMapping
    public ResponseEntity<ApiResponse> getAllProducts(){
        List<Product> products = productServiceInterface.getAllProducts();
        return ResponseEntity.ok(new ApiResponse("Success!", productServiceInterface.getConvertedProducts(products)));
    }

    @GetMapping("/{productId:\\d+}")
    public ResponseEntity<ApiResponse> getProductById(@PathVariable("productId") Long id){
        Product product = productServiceInterface.getProductById(id);
        return ResponseEntity.ok(new ApiResponse("Success!", productServiceInterface.convertToDto(product)));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse> addProduct(@Valid @RequestBody AddProductRequest name) {
        Product product = productServiceInterface.addProduct(name);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(product.getId()).toUri();
        return ResponseEntity.created(location)
                .body(new ApiResponse("Success!", productServiceInterface.convertToDto(product)));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse> updateProduct(@Valid @RequestBody ProductUpdateRequest request,
                                                     @PathVariable Long id) {
        Product updatedProduct = productServiceInterface.updateProductById(request, id);
        return ResponseEntity.ok(new ApiResponse("Success!", productServiceInterface.convertToDto(updatedProduct)));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteProduct(@PathVariable Long id){
        productServiceInterface.deleteProductById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(params = {"brand", "name"})
    public ResponseEntity<ApiResponse> getProductByBrandAndName(@RequestParam("brand") String brandName,
                                                                @RequestParam("name") String productName){
        List<Product> products = productServiceInterface.getProductsByBrandAndName(brandName, productName);
        return ResponseEntity.ok(new ApiResponse("Success!", productServiceInterface.getConvertedProducts(requireNonEmpty(products))));
    }

    @GetMapping(params = {"category", "name"})
    public ResponseEntity<ApiResponse> getProductByCategoryAndName(@RequestParam("category") String category,
                                                                   @RequestParam("name") String productName){
        List<Product> products = productServiceInterface.getProductsByCategoryAndBrand(category, productName);
        return ResponseEntity.ok(new ApiResponse("Success!", productServiceInterface.getConvertedProducts(requireNonEmpty(products))));
    }

    @GetMapping(params = "name")
    public ResponseEntity<ApiResponse> getProductByName(@RequestParam("name") String name){
        List<Product> products = productServiceInterface.getProductsByName(name);
        return ResponseEntity.ok(new ApiResponse("Success!", productServiceInterface.getConvertedProducts(requireNonEmpty(products))));
    }

    @GetMapping(params = "brand")
    public ResponseEntity<ApiResponse> findProductByBrand(@RequestParam("brand") String brand){
        List<Product> products = productServiceInterface.getProductsByBrand(brand);
        return ResponseEntity.ok(new ApiResponse("Success!", productServiceInterface.getConvertedProducts(requireNonEmpty(products))));
    }

    @GetMapping(params = "category")
    public ResponseEntity<ApiResponse> findAllProductByCategory(@RequestParam("category") String category){
        List<Product> products = productServiceInterface.getAllProductsByCategory(category);
        return ResponseEntity.ok(new ApiResponse("Success!", productServiceInterface.getConvertedProducts(requireNonEmpty(products))));
    }

    @GetMapping(value = "/count", params = {"brand", "name"})
    public ResponseEntity<ApiResponse> countProductByBrandAndName(@RequestParam String brand, @RequestParam String name){
        var productCount = productServiceInterface.countProductsByBrandAndName(brand, name);
        return ResponseEntity.ok(new ApiResponse("Product Count!", productCount));
    }

    /** Empty search results are surfaced as 404 (mapped by the global handler). */
    private List<Product> requireNonEmpty(List<Product> products){
        if (products.isEmpty()) {
            throw new ResourceNotFoundException("No Product Found");
        }
        return products;
    }
}
