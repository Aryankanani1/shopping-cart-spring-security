package com.aryan.spring_security_demo.service.product;

import com.aryan.spring_security_demo.dto.ProductDto;
import com.aryan.spring_security_demo.model.Product;
import com.aryan.spring_security_demo.request.AddProductRequest;
import com.aryan.spring_security_demo.request.ProductUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProductServiceInterface {
    Product addProduct(AddProductRequest product);
    Product getProductById(Long id);
    void deleteProductById(Long productId);
    Product updateProductById(ProductUpdateRequest product, Long productId);
    List<Product> getAllProducts();
    Long countProductsByBrandAndName(String brand,String name);

    ProductDto convertToDto(Product product);
    List<ProductDto> getConvertedProducts(List<Product> products);

    // DTO-returning read/write operations. Each loads AND maps inside one
    // transaction, so lazy associations (category, images) resolve while the
    // persistence context is open — safe to serialize with open-in-view off.
    ProductDto getProductDtoById(Long id);
    ProductDto addProductAndConvert(AddProductRequest request);
    ProductDto updateProductAndConvert(ProductUpdateRequest request, Long id);

    /**
     * Paginated, dynamically filtered listing. Any of {@code brand}/{@code name}/
     * {@code category} may be {@code null} (absent). Mapping to {@link ProductDto}
     * happens inside the read transaction so lazy associations resolve safely.
     */
    Page<ProductDto> findProducts(String brand, String name, String category, Pageable pageable);
}
