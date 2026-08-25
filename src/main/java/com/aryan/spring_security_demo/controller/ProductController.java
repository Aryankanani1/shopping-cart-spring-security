package com.aryan.spring_security_demo.controller;
import com.aryan.spring_security_demo.Service.product.ProductServiceInterface;
import com.aryan.spring_security_demo.model.Product;
import com.aryan.spring_security_demo.request.AddProductRequest;
import com.aryan.spring_security_demo.request.ProductUpdateRequest;
import com.aryan.spring_security_demo.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import static org.springframework.http.HttpStatus.*;

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
        try
        {
            Product product = productServiceInterface.getProductById(id);
            return ResponseEntity.ok(new ApiResponse("Success!", productServiceInterface.convertToDto(product)));
        }catch (Exception e){
            return ResponseEntity.status(NOT_FOUND).body(new ApiResponse("product doesn't exists",null));
        }
    }


    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse> addProduct(@RequestBody AddProductRequest name) {
                try{
                    Product product = productServiceInterface.addProduct(name);
                    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                            .path("/{id}").buildAndExpand(product.getId()).toUri();
                    return ResponseEntity.created(location)
                            .body(new ApiResponse("Success!", productServiceInterface.convertToDto(product)));
            }catch (Exception e){
                    return ResponseEntity.status(CONFLICT).body(new ApiResponse(e.getMessage(),null));
                }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse> updateProduct(@RequestBody ProductUpdateRequest request
            , @PathVariable Long id)
    {
        try{
        Product Updatedproduct = productServiceInterface.updateProductById(request,id);
            return ResponseEntity.ok(new ApiResponse("Success!", productServiceInterface.convertToDto(Updatedproduct)));

        }catch (Exception e){
            return ResponseEntity.status(NOT_FOUND).body(new ApiResponse(e.getMessage(),null));
        }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteProduct(@PathVariable Long id){
        try {
            productServiceInterface.deleteProductById(id);
            return ResponseEntity.noContent().build();
        }
        catch (Exception e){
            return ResponseEntity.status(NOT_FOUND).body(new ApiResponse("Product doesn't exits",null));
        }
    }

    @GetMapping(params = {"brand", "name"})
    public ResponseEntity<ApiResponse> getProductByBrandAndName(@RequestParam("brand") String brandName,
                                                                @RequestParam("name") String productName){

        try{
          List<Product> productsByBrand =  productServiceInterface.getProductsByBrandAndName(brandName,productName);
          if(productsByBrand.isEmpty()){
              return ResponseEntity.status(NOT_FOUND).body(new ApiResponse("No Product Found",null));
          }

          return ResponseEntity.ok(new ApiResponse("Success!", productServiceInterface.getConvertedProducts(productsByBrand)));

        }
        catch (Exception e){
            return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(new ApiResponse(e.getMessage(),null));
        }
    }

    @GetMapping(params = {"category", "name"})
    public ResponseEntity<ApiResponse> getProductByCategoryAndName(@RequestParam("category") String category,
                                                                @RequestParam("name") String productName){

        try{
            List<Product> productsByBrand =  productServiceInterface.getProductsByCategoryAndBrand(category,productName);
            if(productsByBrand.isEmpty()){
                return ResponseEntity.status(NOT_FOUND).body(new ApiResponse("No Product Found",null));
            }

            return ResponseEntity.ok(new ApiResponse("Success!", productServiceInterface.getConvertedProducts(productsByBrand)));

        }
        catch (Exception e){
            return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(new ApiResponse(e.getMessage(),null));
        }
    }

    @GetMapping(params = "name")
    public ResponseEntity<ApiResponse> getProductByName(@RequestParam("name") String name){

        try{
            List<Product> productsByBrand =  productServiceInterface.getProductsByName(name);
            if(productsByBrand.isEmpty()){
                return ResponseEntity.status(NOT_FOUND).body(new ApiResponse("No Product Found",null));
            }

            return ResponseEntity.ok(new ApiResponse("Success!", productServiceInterface.getConvertedProducts(productsByBrand)));

        }
        catch (Exception e){
            return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(new ApiResponse(e.getMessage(),null));
        }
    }

    @GetMapping(params = "brand")
    public ResponseEntity<ApiResponse> findProductByBrand(@RequestParam("brand") String brand){

        try{
            List<Product> productsByBrand =  productServiceInterface.getProductsByBrand(brand);
            if(productsByBrand.isEmpty()){
                return ResponseEntity.status(NOT_FOUND).body(new ApiResponse("No Product Found",null));
            }

            return ResponseEntity.ok(new ApiResponse("Success!", productServiceInterface.getConvertedProducts(productsByBrand)));

        }
        catch (Exception e){
            return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(new ApiResponse(e.getMessage(),null));
        }
    }

    @GetMapping(params = "category")
    public ResponseEntity<ApiResponse> findAllProductByCategory(@RequestParam("category") String category){

        try{
            List<Product> productsByBrand =  productServiceInterface.getAllProductsByCategory(category);
            if(productsByBrand.isEmpty()){
                return ResponseEntity.status(NOT_FOUND).body(new ApiResponse("No Product Found",null));
            }

            return ResponseEntity.ok(new ApiResponse("Success!", productServiceInterface.getConvertedProducts(productsByBrand)));

        }
        catch (Exception e){
            return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(new ApiResponse(e.getMessage(),null));
        }
    }

    @GetMapping(value = "/count", params = {"brand", "name"})
    public ResponseEntity<ApiResponse> countProductByBrandAndName(@RequestParam String brand, @RequestParam String name){
        try{
            var productCount = productServiceInterface.countProductsByBrandAndName(brand,name);
            return ResponseEntity.ok(new ApiResponse("Product Count!",productCount));
        }catch (Exception e){
            return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(new ApiResponse(e.getMessage(),null));
        }
    }




}
