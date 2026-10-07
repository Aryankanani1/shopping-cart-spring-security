package com.aryan.spring_security_demo.catalog;

import com.aryan.spring_security_demo.common.exception.AlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Product writes: duplicate checks, the price-change and deletion events that
 * keep carts in step, and the "ordered products can't be deleted" rule.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final Long PRODUCT_ID = 5L;

    @Mock private ProductRepository productRepository;
    @Mock private CategoryServiceInterface categoryService;
    @Mock private ApplicationEventPublisher events;

    @InjectMocks private ProductService productService;

    private Category lighting;
    private Product lamp;

    @BeforeEach
    void setUp() {
        lighting = new Category("Lighting");
        lamp = new Product("Desk Lamp", new BigDecimal("10.00"), "LED", "Acme", 3, lighting);
        lamp.setId(PRODUCT_ID);
        lenient().when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---- add --------------------------------------------------------------

    @Test
    void addProduct_duplicateNameAndBrand_isRejected() {
        when(productRepository.existsByNameAndBrand("Desk Lamp", "Acme")).thenReturn(true);

        // Regression: the message read "Acme Desk Lampalready exists".
        assertThatThrownBy(() -> productService.addProduct(addRequest("Lighting")))
                .isInstanceOf(AlreadyExistsException.class)
                .hasMessage("Acme Desk Lamp already exists");
        verify(productRepository, never()).save(any());
        verify(categoryService, never()).findOrCreate(any());
    }

    @Test
    void addProduct_resolvesTheCategoryByName() {
        when(categoryService.findOrCreate("Lighting")).thenReturn(lighting);

        Product added = productService.addProduct(addRequest("Lighting"));

        assertThat(added.getCategory()).isSameAs(lighting);
        assertThat(added.getPrice()).isEqualByComparingTo("12.50");
    }

    // ---- update -----------------------------------------------------------

    @Test
    void update_missingProduct_is404() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateProductById(updateRequest("12.00", null), PRODUCT_ID))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void update_newPrice_tellsCartsToReprice() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(lamp));

        productService.updateProductById(updateRequest("12.00", null), PRODUCT_ID);

        verify(events).publishEvent(new ProductPriceChangedEvent(PRODUCT_ID, new BigDecimal("12.00")));
        assertThat(lamp.getPrice()).isEqualByComparingTo("12.00");
    }

    @Test
    void update_samePriceWithDifferentScale_isNotAPriceChange() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(lamp));

        productService.updateProductById(updateRequest("10.0", null), PRODUCT_ID);  // was 10.00

        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void update_withoutCategory_keepsTheCurrentOne() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(lamp));

        productService.updateProductById(updateRequest("10.00", null), PRODUCT_ID);

        assertThat(lamp.getCategory()).isSameAs(lighting);
        verify(categoryService, never()).findOrCreate(any());
    }

    @Test
    void update_withCategory_resolvesItByName() {
        Category outdoor = new Category("Outdoor");
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(lamp));
        when(categoryService.findOrCreate("Outdoor")).thenReturn(outdoor);

        productService.updateProductById(updateRequest("10.00", "Outdoor"), PRODUCT_ID);

        assertThat(lamp.getCategory()).isSameAs(outdoor);
    }

    // ---- delete -----------------------------------------------------------

    @Test
    void delete_missingProduct_is404AndTellsNoOne() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.deleteProductById(PRODUCT_ID))
                .isInstanceOf(ProductNotFoundException.class);
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void delete_tellsCartsFirstThenDeletesAndFlushes() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(lamp));

        productService.deleteProductById(PRODUCT_ID);

        InOrder order = inOrder(events, productRepository);
        order.verify(events).publishEvent(new ProductDeletingEvent(PRODUCT_ID));
        order.verify(productRepository).delete(lamp);
        order.verify(productRepository).flush();
    }

    @Test
    void delete_productStillInOrders_isProductInUse() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(lamp));
        doThrow(new DataIntegrityViolationException("FK orderitems.product_id")).when(productRepository).flush();

        assertThatThrownBy(() -> productService.deleteProductById(PRODUCT_ID))
                .isInstanceOf(ProductInUseException.class)
                .hasMessageContaining("stock to 0");
    }

    // ---- helpers ----------------------------------------------------------

    private static AddProductRequest addRequest(String category) {
        AddProductRequest request = new AddProductRequest();
        request.setName("Desk Lamp");
        request.setBrand("Acme");
        request.setPrice(new BigDecimal("12.50"));
        request.setInventory(4);
        request.setCategory(new CategoryRequest(category));
        return request;
    }

    private static ProductUpdateRequest updateRequest(String price, String category) {
        ProductUpdateRequest request = new ProductUpdateRequest();
        request.setName("Desk Lamp");
        request.setBrand("Acme");
        request.setPrice(new BigDecimal(price));
        request.setInventory(3);
        request.setCategory(category == null ? null : new CategoryRequest(category));
        return request;
    }
}
