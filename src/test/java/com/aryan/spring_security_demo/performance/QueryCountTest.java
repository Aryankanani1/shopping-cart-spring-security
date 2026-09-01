package com.aryan.spring_security_demo.performance;

import com.aryan.spring_security_demo.service.order.OrderServiceInterface;
import com.aryan.spring_security_demo.service.product.ProductServiceInterface;
import com.aryan.spring_security_demo.dto.OrderDto;
import com.aryan.spring_security_demo.dto.ProductDto;
import com.aryan.spring_security_demo.enums.OrderStatus;
import com.aryan.spring_security_demo.model.Category;
import com.aryan.spring_security_demo.model.Image;
import com.aryan.spring_security_demo.model.Order;
import com.aryan.spring_security_demo.model.OrderItem;
import com.aryan.spring_security_demo.model.Product;
import com.aryan.spring_security_demo.model.Role;
import com.aryan.spring_security_demo.model.User;
import com.aryan.spring_security_demo.repository.CategoryRepository;
import com.aryan.spring_security_demo.repository.ImageRepository;
import com.aryan.spring_security_demo.repository.OrderRepository;
import com.aryan.spring_security_demo.repository.ProductRepository;
import com.aryan.spring_security_demo.repository.RoleRepository;
import com.aryan.spring_security_demo.repository.UserRepository;
import com.aryan.spring_security_demo.response.SlicedResponse;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards against N+1 regressions by counting the SQL statements each read path
 * actually issues. The point of these tests is not the exact number but that it
 * stays <em>bounded</em> — it must not grow with the number of rows.
 *
 * <p>To measure honestly, data is seeded in {@link #setUp()} through repositories
 * that commit (so nothing lingers in a first-level cache), and each product gets
 * its <b>own</b> category — otherwise a shared category would be cached on first
 * load and mask a reverted fetch join / entity graph.
 */
@SpringBootTest
@ActiveProfiles("test")
class QueryCountTest {

    private static final int PRODUCT_COUNT = 4;   // products, each in its own category, each with an image
    private static final int ITEM_COUNT = 4;      // line items on the single seeded order
    private static final BigDecimal PRICE = new BigDecimal("9.99");

    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private PlatformTransactionManager txManager;

    @Autowired private ProductServiceInterface productService;
    @Autowired private OrderServiceInterface orderService;

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ImageRepository imageRepository;
    @Autowired private OrderRepository orderRepository;

    private Long userId;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        imageRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        Role customer = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_CUSTOMER")));

        User user = new User();
        user.setFirstName("Grace");
        user.setLastName("Hopper");
        user.setEmail("perf@example.com");
        user.setPassword("irrelevant");
        user.setRoles(Set.of(customer));
        userId = userRepository.save(user).getId();

        Order order = new Order();
        order.setUser(user);
        order.setOrderStatus(OrderStatus.PENDING);
        order.setLocalDate(LocalDate.now());

        for (int i = 0; i < PRODUCT_COUNT; i++) {
            Category category = categoryRepository.save(new Category("Category-" + i));
            Product product = productRepository.save(
                    new Product("Product-" + i, PRICE, "desc", "Brand-" + i, 100, category));

            Image image = new Image();
            image.setFileName("img-" + i + ".png");
            image.setFileType("image/png");
            image.setURL("/api/v1/images/" + i);
            image.setProduct(product);
            imageRepository.save(image);

            order.addOrderItem(new OrderItem(product, 1, PRICE));
        }

        order.setTotalAmount(PRICE.multiply(BigDecimal.valueOf(ITEM_COUNT)));
        orderRepository.save(order);
    }

    @Test
    @DisplayName("getUserOrders (keyset) loads a slice + its items + products in a bounded number of queries")
    void getUserOrders_isBounded() {
        Statistics stats = statistics();
        stats.clear();

        SlicedResponse<OrderDto> slice = orderService.getUserOrders(userId, null, 20);

        long queries = stats.getPrepareStatementCount();
        assertThat(slice.content()).hasSize(1);
        assertThat(slice.hasNext()).isFalse();
        assertThat(slice.content().get(0).getItems()).hasSize(ITEM_COUNT);
        // Sanity: the product IS mapped, so the query genuinely traverses it.
        assertThat(slice.content().get(0).getItems().get(0).getProductName()).isNotBlank();
        // Keyset paging is two-phase: (1) an index-backed scan for the page of ids,
        // (2) one JOIN FETCH hydrating orders + items + products. Two queries, and
        // — the point — bounded: it does not grow with the number of orders or items.
        assertThat(queries)
                .as("keyset getUserOrders must page ids then hydrate items/products in a bounded query count")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("listing all products loads categories without an N+1")
    void getAllProducts_isBounded() {
        Statistics stats = statistics();
        stats.clear();

        // getConvertedProducts touches lazy imageList, so run the whole
        // load-and-map inside one transaction (mirrors the request scope).
        List<ProductDto> dtos = new TransactionTemplate(txManager).execute(status ->
                productService.getConvertedProducts(productService.getAllProducts()));

        long queries = stats.getPrepareStatementCount();
        assertThat(dtos).hasSize(PRODUCT_COUNT);
        // Sanity: both traversed associations are actually populated.
        assertThat(dtos.get(0).getCategoryName()).isNotBlank();
        assertThat(dtos.get(0).getImages()).isNotEmpty();
        // Entity graph fetches categories WITH the product list (1 query); images
        // come in 1 batched query. Dropping the graph adds a 3rd (batched category)
        // query. A truly classic N+1 (no batching) would be far higher.
        assertThat(queries)
                .as("listing products must fetch categories with the list, not separately")
                .isEqualTo(2);
    }

    private Statistics statistics() {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        return stats;
    }
}
