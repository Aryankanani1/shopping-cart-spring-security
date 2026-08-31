package com.aryan.spring_security_demo.controller;
import com.aryan.spring_security_demo.service.product.ProductServiceInterface;
import com.aryan.spring_security_demo.dto.ProductDto;
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
 *
 * <p>Every response is a {@link ProductDto} produced by the service inside its
 * transaction — controllers never touch a {@code Product} entity, so there is no
 * lazy-loading or entity-leak risk during serialization.
 */
@RequiredArgsConstructor
@RestController
@Validated
@RequestMapping("${api.prefix}/products")
public class ProductController {

    private final ProductServiceInterface productServiceInterface;

    @GetMapping
    public ResponseEntity<ApiResponse<?>> getAllProducts(){
        return ResponseEntity.ok(new ApiResponse<>("Success!", productServiceInterface.getAllProductDtos()));
    }

    @GetMapping("/{productId:\\d+}")
    public ResponseEntity<ApiResponse<?>> getProductById(@PathVariable("productId") Long id){
        return ResponseEntity.ok(new ApiResponse<>("Success!", productServiceInterface.getProductDtoById(id)));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<?>> addProduct(@Valid @RequestBody AddProductRequest name) {
        ProductDto product = productServiceInterface.addProductAndConvert(name);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(product.getId()).toUri();
        return ResponseEntity.created(location).body(new ApiResponse<>("Success!", product));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> updateProduct(@Valid @RequestBody ProductUpdateRequest request,
                                                     @PathVariable Long id) {
        return ResponseEntity.ok(new ApiResponse<>("Success!", productServiceInterface.updateProductAndConvert(request, id)));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> deleteProduct(@PathVariable Long id){
        productServiceInterface.deleteProductById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(params = {"brand", "name"})
    public ResponseEntity<ApiResponse<?>> getProductByBrandAndName(@RequestParam("brand") String brandName,
                                                                @RequestParam("name") String productName){
        return ResponseEntity.ok(new ApiResponse<>("Success!", productServiceInterface.getProductDtosByBrandAndName(brandName, productName)));
    }

    @GetMapping(params = {"category", "name"})
    public ResponseEntity<ApiResponse<?>> getProductByCategoryAndName(@RequestParam("category") String category,
                                                                   @RequestParam("name") String productName){
        return ResponseEntity.ok(new ApiResponse<>("Success!", productServiceInterface.getProductDtosByCategoryAndBrand(category, productName)));
    }

    @GetMapping(params = "name")
    public ResponseEntity<ApiResponse<?>> getProductByName(@RequestParam("name") String name){
        return ResponseEntity.ok(new ApiResponse<>("Success!", productServiceInterface.getProductDtosByName(name)));
    }

    @GetMapping(params = "brand")
    public ResponseEntity<ApiResponse<?>> findProductByBrand(@RequestParam("brand") String brand){
        return ResponseEntity.ok(new ApiResponse<>("Success!", productServiceInterface.getProductDtosByBrand(brand)));
    }

    @GetMapping(params = "category")
    public ResponseEntity<ApiResponse<?>> findAllProductByCategory(@RequestParam("category") String category){
        return ResponseEntity.ok(new ApiResponse<>("Success!", productServiceInterface.getProductDtosByCategory(category)));
    }

    @GetMapping(value = "/count", params = {"brand", "name"})
    public ResponseEntity<ApiResponse<?>> countProductByBrandAndName(@RequestParam String brand, @RequestParam String name){
        var productCount = productServiceInterface.countProductsByBrandAndName(brand, name);
        return ResponseEntity.ok(new ApiResponse<>("Product Count!", productCount));
    }
}
