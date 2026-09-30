package com.aryan.spring_security_demo.service.wishlist;

import com.aryan.spring_security_demo.enums.NotificationType;
import com.aryan.spring_security_demo.model.Category;
import com.aryan.spring_security_demo.model.Notification;
import com.aryan.spring_security_demo.model.Product;
import com.aryan.spring_security_demo.model.User;
import com.aryan.spring_security_demo.model.WishlistItem;
import com.aryan.spring_security_demo.repository.CartItemRepository;
import com.aryan.spring_security_demo.repository.CartRepository;
import com.aryan.spring_security_demo.repository.CategoryRepository;
import com.aryan.spring_security_demo.repository.NotificationRepository;
import com.aryan.spring_security_demo.repository.OrderRepository;
import com.aryan.spring_security_demo.repository.ProductRepository;
import com.aryan.spring_security_demo.repository.RefreshTokenRepository;
import com.aryan.spring_security_demo.repository.UserRepository;
import com.aryan.spring_security_demo.repository.WishlistItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The alert scan against a real (H2) database: each reminder, price drop and
 * restock is announced exactly once, and nothing is announced while alerts are
 * off. Time is passed in explicitly, so no test depends on the wall clock.
 */
@SpringBootTest
@ActiveProfiles("test")
class WishlistAlertServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");

    @Autowired private WishlistAlertService alertService;
    @Autowired private WishlistItemRepository wishlistItemRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    private User user;
    private Category category;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        wishlistItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        productRepository.deleteAll();

        User u = new User();
        u.setFirstName("Ada");
        u.setLastName("Lovelace");
        u.setEmail("watcher@example.com");
        u.setPassword("unused");
        user = userRepository.save(u);

        category = categoryRepository.existsByName("Electronics")
                ? categoryRepository.findByName("Electronics")
                : categoryRepository.save(new Category("Electronics"));
    }

    @Test
    @DisplayName("a due reminder fires once and is cleared; a future one waits")
    void dueReminder_firesOnceAndClears() {
        WishlistItem due = saveItem(saveProduct("Lamp", "100.00", 5), NOW.minusSeconds(60));
        WishlistItem later = saveItem(saveProduct("Desk", "300.00", 5), NOW.plusSeconds(3600));

        assertThat(alertService.fireDueReminders(NOW)).isEqualTo(1);
        assertThat(alertService.fireDueReminders(NOW)).as("already fired").isZero();

        List<Notification> sent = notificationRepository.findAll();
        assertThat(sent).singleElement().satisfies(n -> {
            assertThat(n.getType()).isEqualTo(NotificationType.WISHLIST_REMINDER);
            assertThat(n.getProductName()).isEqualTo("Lamp");
            assertThat(n.getCreatedAt()).isEqualTo(NOW);
        });
        assertThat(reload(due).getRemindAt()).isNull();
        assertThat(reload(later).getRemindAt()).isEqualTo(NOW.plusSeconds(3600));
    }

    @Test
    @DisplayName("price drops are announced once each, and only when they set a new low")
    void priceDrop_announcedOnNewLowsOnly() {
        Product lamp = saveProduct("Lamp", "100.00", 5);
        saveItem(lamp, null);

        setPrice(lamp, "80.00");
        assertThat(alertService.announcePriceDrops(NOW)).isEqualTo(1);
        assertThat(alertService.announcePriceDrops(NOW)).as("same price again").isZero();

        setPrice(lamp, "90.00");  // back up, but still under the original price
        assertThat(alertService.announcePriceDrops(NOW)).as("not a new low").isZero();

        setPrice(lamp, "70.00");
        assertThat(alertService.announcePriceDrops(NOW)).isEqualTo(1);

        List<Notification> sent = notificationRepository.findAll();
        assertThat(sent).hasSize(2).allMatch(n -> n.getType() == NotificationType.PRICE_DROP);
        assertThat(sent).anySatisfy(n -> {
            assertThat(n.getOldPrice()).isEqualByComparingTo("100.00");
            assertThat(n.getNewPrice()).isEqualByComparingTo("80.00");
        });
        assertThat(sent).anySatisfy(n -> {
            assertThat(n.getOldPrice()).isEqualByComparingTo("80.00");
            assertThat(n.getNewPrice()).isEqualByComparingTo("70.00");
        });
    }

    @Test
    @DisplayName("nothing is announced for an item whose alerts are off")
    void alertsOff_nothingAnnounced() {
        Product lamp = saveProduct("Lamp", "100.00", 0);
        WishlistItem item = saveItem(lamp, null);
        item.setAlertsEnabled(false);
        wishlistItemRepository.save(item);

        setPrice(lamp, "50.00");
        setInventory(lamp, 10);

        assertThat(alertService.announcePriceDrops(NOW)).isZero();
        assertThat(alertService.announceRestocks(NOW)).isZero();
        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    @DisplayName("a sold-out item coming back in stock is announced once")
    void restock_announcedOnce() {
        Product lamp = saveProduct("Lamp", "100.00", 0);
        saveItem(lamp, null);

        assertThat(alertService.announceRestocks(NOW)).as("still sold out").isZero();
        setInventory(lamp, 3);
        assertThat(alertService.announceRestocks(NOW)).isEqualTo(1);
        assertThat(alertService.announceRestocks(NOW)).as("already announced").isZero();

        assertThat(notificationRepository.findAll()).singleElement()
                .satisfies(n -> assertThat(n.getType()).isEqualTo(NotificationType.BACK_IN_STOCK));
    }

    @Test
    @DisplayName("an item in stock when saved that sells out and restocks is announced")
    void sellOutThenRestock_isRecognised() {
        Product lamp = saveProduct("Lamp", "100.00", 5);
        saveItem(lamp, null);

        setInventory(lamp, 0);
        assertThat(alertService.announceRestocks(NOW)).as("sell-out is recorded silently").isZero();
        setInventory(lamp, 2);
        assertThat(alertService.announceRestocks(NOW)).isEqualTo(1);
    }

    // --- helpers -----------------------------------------------------------

    private Product saveProduct(String name, String price, int inventory) {
        return productRepository.save(new Product(name, new BigDecimal(price), "", "Acme", inventory, category));
    }

    private WishlistItem saveItem(Product product, Instant remindAt) {
        WishlistItem item = new WishlistItem(user, product, NOW.minusSeconds(86_400));
        item.setRemindAt(remindAt);
        return wishlistItemRepository.save(item);
    }

    private void setPrice(Product product, String price) {
        Product fresh = productRepository.findById(product.getId()).orElseThrow();
        fresh.setPrice(new BigDecimal(price));
        productRepository.save(fresh);
    }

    private void setInventory(Product product, int inventory) {
        Product fresh = productRepository.findById(product.getId()).orElseThrow();
        fresh.setInventory(inventory);
        productRepository.save(fresh);
    }

    private WishlistItem reload(WishlistItem item) {
        return wishlistItemRepository.findById(item.getId()).orElseThrow();
    }
}
