package com.aryan.spring_security_demo.controller;
import com.aryan.spring_security_demo.Service.product.ProductServiceInterface;
import com.aryan.spring_security_demo.exception.ProductNotFoundException;
import com.aryan.spring_security_demo.model.Product;
import com.aryan.spring_security_demo.request.AddProductRequest;
import com.aryan.spring_security_demo.request.ProductUpdateRequest;
import com.aryan.spring_security_demo.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
        return ResponseEntity.ok(new ApiResponse("Success!",products));
    }

    @GetMapping("product/{productId}/product")
    public ResponseEntity<ApiResponse> getProductById(@PathVariable("productId") Long id){
        try
        {
            Product product = productServiceInterface.getProductById(id);
            return ResponseEntity.ok(new ApiResponse("Success!", product));
        }catch (ProductNotFoundException e){
            return ResponseEntity.status(NOT_FOUND).body(new ApiResponse("product doesn't exists",null));
        }
    }

    @PostMapping("/add")
    public ResponseEntity<ApiResponse> addProduct(@RequestBody AddProductRequest name) {
                try{
                    Product product = productServiceInterface.addProduct(name);
                    return ResponseEntity.ok(new ApiResponse("Success!",product));
            }catch (ProductNotFoundException e){
                    return ResponseEntity.status(CONFLICT).body(new ApiResponse(e.getMessage(),null));
                }
    }

    @PutMapping("/product/{id}/product")
    public ResponseEntity<ApiResponse> updateProduct(@RequestBody ProductUpdateRequest request
            , @PathVariable Long id)
    {
        try{
        Product Updatedproduct = productServiceInterface.updateProductById(request,id);
            return ResponseEntity.ok(new ApiResponse("Success!",Updatedproduct));

        }catch (ProductNotFoundException e){
            return ResponseEntity.status(NOT_FOUND).body(new ApiResponse(e.getMessage(),null));
        }
    }


    @DeleteMapping("/product/{id}/delete")
    public ResponseEntity<ApiResponse> deleteProduct(@PathVariable Long id){
        try {
            productServiceInterface.deleteProductById(id);
            return ResponseEntity.ok(new ApiResponse("Success!",null));
        }
        catch (ProductNotFoundException e){
            return ResponseEntity.status(NOT_FOUND).body(new ApiResponse("Product doesn't exits",null));
        }
    }

    @GetMapping("/by/brand-and-name")
    public ResponseEntity<ApiResponse> getProductByBrandAndName(@PathVariable String brandName,
                                                         @PathVariable String productName){

        try{
          List<Product> productsByBrand =  productServiceInterface.getProductsByBrandAndName(brandName,productName);
          if(productsByBrand.isEmpty()){
              return ResponseEntity.status(NOT_FOUND).body(new ApiResponse("No Product Found",null));
          }

          return ResponseEntity.ok(new ApiResponse("Success!",null));

        }
        catch (ProductNotFoundException e){
            return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(new ApiResponse(e.getMessage(),null));
        }
    }



}


