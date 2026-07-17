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
import java.util.List;
import static org.springframework.http.HttpStatus.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("${api.prefix}/products")
public class ProductController {

    private final ProductServiceInterface productServiceInterface;

    @GetMapping("/all")
    public ResponseEntity<ApiResponse> getAllProducts(){
        List<Product> products = productServiceInterface.getAllProducts();
        return ResponseEntity.ok(new ApiResponse("Success!", productServiceInterface.getConvertedProducts(products)));
    }

    @GetMapping("product/{productId}/product")
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
    @PostMapping("/add")
    public ResponseEntity<ApiResponse> addProduct(@RequestBody AddProductRequest name) {
                try{
                    Product product = productServiceInterface.addProduct(name);
                    return ResponseEntity.ok(new ApiResponse("Success!", productServiceInterface.convertToDto(product)));
            }catch (Exception e){
                    return ResponseEntity.status(CONFLICT).body(new ApiResponse(e.getMessage(),null));
                }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PutMapping("/product/{id}/product")
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
    @DeleteMapping("/product/{id}/delete")
    public ResponseEntity<ApiResponse> deleteProduct(@PathVariable Long id){
        try {
            productServiceInterface.deleteProductById(id);
            return ResponseEntity.ok(new ApiResponse("Success!",null));
        }
        catch (Exception e){
            return ResponseEntity.status(NOT_FOUND).body(new ApiResponse("Product doesn't exits",null));
        }
    }

    @GetMapping("/products/by/brand-and-name")
    public ResponseEntity<ApiResponse> getProductByBrandAndName(@RequestParam String brandName,
                                                                @RequestParam String productName){

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

    @GetMapping("/products/by/category-and-brand")
    public ResponseEntity<ApiResponse> getProductByBrandAndCategory(@RequestParam String category,
                                                                @RequestParam String productName){

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

    @GetMapping("/products/{name}/products")
    public ResponseEntity<ApiResponse> getProductByBrandAndCategory(@PathVariable String name){

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

    @GetMapping("/product/by-brand")
    public ResponseEntity<ApiResponse> findProductByBrand(@RequestParam String brand){

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

    @GetMapping("/product/{category}/all/products")
    public ResponseEntity<ApiResponse> findAllProductByCategory(@PathVariable String category){

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

    @GetMapping("/product/count/by-brand/and-name")
    public ResponseEntity<ApiResponse> countProductByBrandAndName(@RequestParam String brand, @RequestParam String name){
        try{
            var productCount = productServiceInterface.countProductsByBrandAndName(brand,name);
            return ResponseEntity.ok(new ApiResponse("Product Count!",productCount));
        }catch (Exception e){
            return ResponseEntity.ok(new ApiResponse(e.getMessage(),null));
        }
    }




}
