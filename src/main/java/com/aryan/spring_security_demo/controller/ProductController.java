package com.aryan.spring_security_demo.controller;
import com.aryan.spring_security_demo.exception.InvalidSortException;
import com.aryan.spring_security_demo.service.product.ProductServiceInterface;
import com.aryan.spring_security_demo.dto.ProductDto;
import com.aryan.spring_security_demo.request.AddProductRequest;
import com.aryan.spring_security_demo.request.ProductUpdateRequest;
import com.aryan.spring_security_demo.response.ApiResponse;
import com.aryan.spring_security_demo.response.PagedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Set;

/**
 * Products resource. Verbs are carried by the HTTP method and the collection is
 * filtered with query parameters (Richardson Maturity Model level 2):
 *
 * <pre>
 *   GET    /products?brand=&name=&category=  paginated + filtered list (any subset of filters)
 *   GET    /products/count?brand=&name=      count matching brand + name
 *   GET    /products/{id}                    fetch one
 *   POST   /products                         create        -> 201 + Location
 *   PUT    /products/{id}                     replace
 *   DELETE /products/{id}                     delete        -> 204
 * </pre>
 *
 * <p>The list endpoint is paginated ({@code page}/{@code size}/{@code sort}) and
 * returns a {@link com.aryan.spring_security_demo.response.PagedResponse} of DTOs;
 * filters are optional and combined dynamically.
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

    /**
     * Fields a client may sort by. Each must be an indexed {@code Product} column
     * (see {@code @Table(indexes=...)} on the entity) — sorting on anything else
     * would force a full sort, and echoing arbitrary client-supplied field names
     * into the ORDER BY would expose internals. Anything outside this set is a 400.
     */
    private static final Set<String> ALLOWED_SORT = Set.of("id", "name", "price", "brand");

    /**
     * Paginated, filterable product listing.
     *
     * <pre>
     *   GET /products?brand=&amp;name=&amp;category=&amp;page=0&amp;size=20&amp;sort=name,asc
     * </pre>
     *
     * <p>{@code Pageable} is bound automatically by Spring MVC; {@link PageableDefault}
     * supplies the defaults and {@code spring.data.web.pageable.max-page-size} caps
     * the size. Filters are optional and combined dynamically (see ProductSpecs).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ProductDto>>> getProducts(
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String category,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        validateSort(pageable.getSort());
        Page<ProductDto> page = productServiceInterface.findProducts(brand, name, category, pageable);
        return ResponseEntity.ok(new ApiResponse<>("Success!", PagedResponse.from(page)));
    }

    private void validateSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!ALLOWED_SORT.contains(order.getProperty())) {
                throw new InvalidSortException(order.getProperty(), ALLOWED_SORT);
            }
        }
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

    @GetMapping(value = "/count", params = {"brand", "name"})
    public ResponseEntity<ApiResponse<?>> countProductByBrandAndName(@RequestParam String brand, @RequestParam String name){
        var productCount = productServiceInterface.countProductsByBrandAndName(brand, name);
        return ResponseEntity.ok(new ApiResponse<>("Product Count!", productCount));
    }
}
