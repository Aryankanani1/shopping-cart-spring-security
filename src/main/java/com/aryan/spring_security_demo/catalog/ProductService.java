package com.aryan.spring_security_demo.catalog;
import com.aryan.spring_security_demo.common.exception.AlreadyExistsException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductService implements ProductServiceInterface{
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ApplicationEventPublisher events;
    @Override
    @Transactional
    public Product addProduct(AddProductRequest request) {


        if(productExists(request.getName(),request.getBrand())){
            throw new AlreadyExistsException(request.getBrand() + " " + request.getName() + "already exists");
        }
       Category category = findOrCreateCategory(request.getCategory().getName());

       request.setCategory(category);
       return  productRepository.save(createProduct(request,category));
    }


    /** The category with this name, created if there isn't one yet (for add and update alike). */
    private Category findOrCreateCategory(String name) {
        return Optional.ofNullable(categoryRepository.findByName(name))
                .orElseGet(() -> categoryRepository.save(new Category(name)));
    }

    private boolean productExists(String name,String brand){
        return productRepository.existsByNameAndBrand(name,brand);
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
    @Transactional(readOnly = true)
    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("product not found") );
    }

    @Override
    @Transactional
    public void deleteProductById(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("product not found "));
        // Carts drop the product first, in this transaction (CartCatalogListener).
        events.publishEvent(new ProductDeletingEvent(productId));
        productRepository.delete(product);
        try {
            // Flush now so a foreign-key failure surfaces here, not at commit.
            productRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // Past order lines keep pointing at the product they sold.
            throw new ProductInUseException("This product is part of existing orders, so it can't be deleted."
                    + " Set its stock to 0 to stop selling it.");
        }
    }

    @Override
    @Transactional
    public Product updateProductById(ProductUpdateRequest request, Long productId) {
          return productRepository.findById(productId)
                  .map(existingproduct -> updateExistingProduct(existingproduct,request))
                  .map(productRepository::save).orElseThrow(() -> new ProductNotFoundException("product not found exception"));
    }

    private Product updateExistingProduct(Product existingProduct, ProductUpdateRequest productUpdateRequest){
                if (priceChanges(existingProduct.getPrice(), productUpdateRequest.getPrice())) {
                    // Carts reprice their lines in this same transaction.
                    events.publishEvent(new ProductPriceChangedEvent(
                            existingProduct.getId(), productUpdateRequest.getPrice()));
                }
                existingProduct.setName(productUpdateRequest.getName());
                existingProduct.setBrand(productUpdateRequest.getBrand());
                existingProduct.setPrice(productUpdateRequest.getPrice());
                existingProduct.setDescription(productUpdateRequest.getDescription());
                existingProduct.setInventory(productUpdateRequest.getInventory());

                // The category is optional on update: none sent leaves it unchanged.
                if (productUpdateRequest.getCategory() != null) {
                    existingProduct.setCategory(findOrCreateCategory(productUpdateRequest.getCategory().getName()));
                }
                return existingProduct;

    }
    // compareTo, not equals: 10.0 and 10.00 are the same price.
    private static boolean priceChanges(BigDecimal current, BigDecimal next) {
        return current == null || current.compareTo(next) != 0;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Long countProductsByBrandAndName(String brand, String name) {
        return productRepository.countByBrandAndName(brand,name);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductDto> findProducts(String brand, String name, String category, Pageable pageable) {
        // map() runs inside this read-only tx, so convertToDto's lazy reads
        // (category, images) resolve here — batched by default_batch_fetch_size,
        // not N+1 — rather than after the context closes.
        return productRepository.findAll(ProductSpecs.filter(brand, name, category), pageable)
                .map(this::convertToDto);
    }

    @Override
    public ProductDto convertToDto(Product product) {
        ProductDto dto = new ProductDto();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setBrand(product.getBrand());
        dto.setPrice(product.getPrice());
        dto.setDescription(product.getDescription());
        dto.setInventory(product.getInventory());
        dto.setCategoryName(product.getCategory() != null ? product.getCategory().getName() : null);
        dto.setImages(mapImages(product.getImageList()));
        return dto;
    }

    private List<ImageDto> mapImages(List<Image> images) {
        return Optional.ofNullable(images).orElseGet(List::of)
                .stream()
                .map(image -> {
                    ImageDto imageDto = new ImageDto();
                    imageDto.setImageId(image.getId());
                    imageDto.setImageName(image.getFileName());
                    imageDto.setDownloadUrl(image.getURL());
                    return imageDto;
                })
                .toList();
    }

    @Override
    public List<ProductDto> getConvertedProducts(List<Product> products) {
        return products.stream().map(this::convertToDto).toList();
    }


    // ---- DTO-returning operations: load + map in ONE transaction ------------
    // These delegate to the entity methods above and convert before the
    // transaction closes, so convertToDto's lazy reads (category, images) are
    // safe. Controllers call these and never touch a Product entity.

    @Override
    @Transactional(readOnly = true)
    public ProductDto getProductDtoById(Long id) {
        return convertToDto(getProductById(id));
    }

    @Override
    @Transactional
    public ProductDto addProductAndConvert(AddProductRequest request) {
        return convertToDto(addProduct(request));
    }

    @Override
    @Transactional
    public ProductDto updateProductAndConvert(ProductUpdateRequest request, Long id) {
        return convertToDto(updateProductById(request, id));
    }
}
