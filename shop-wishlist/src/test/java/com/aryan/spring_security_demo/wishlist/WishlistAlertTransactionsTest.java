package com.aryan.spring_security_demo.wishlist;

import com.aryan.spring_security_demo.catalog.Product;
import com.aryan.spring_security_demo.identity.User;
import com.aryan.spring_security_demo.notification.Notification;
import com.aryan.spring_security_demo.notification.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The alert scan's transaction boundaries: every item is handled in a transaction
 * of its own, so one that fails is rolled back alone and the rest still commit.
 * The transaction manager is a mock, so the test sees each begin, commit and
 * rollback; the alert rules themselves are covered against MySQL by
 * {@code WishlistAlertServiceTest} in shop-app.
 */
@ExtendWith(MockitoExtension.class)
class WishlistAlertTransactionsTest {

    private static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");

    @Mock private WishlistItemRepository wishlistItemRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private PlatformTransactionManager transactionManager;

    private WishlistAlertService alertService;

    @BeforeEach
    void setUp() {
        alertService = new WishlistAlertService(wishlistItemRepository, notificationRepository, transactionManager);
    }

    @Test
    void eachItemIsHandledInANewTransactionOfItsOwn() {
        WishlistItem lamp = dueItem(1L, "Lamp");
        WishlistItem desk = dueItem(2L, "Desk");
        scanFinds(lamp, desk);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        assertThat(alertService.fireDueReminders(NOW)).isEqualTo(2);

        InOrder order = inOrder(transactionManager, notificationRepository);
        for (int i = 0; i < 2; i++) {
            order.verify(transactionManager).getTransaction(any());
            order.verify(notificationRepository).save(any(Notification.class));
            order.verify(transactionManager).commit(any());
        }
        verify(transactionManager, times(2)).getTransaction(argThat(definition ->
                definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        assertThat(lamp.getRemindAt()).isNull();
        assertThat(desk.getRemindAt()).isNull();
    }

    @Test
    void anItemThatFails_isRolledBackAlone_andTheNextStillCommits() {
        WishlistItem poison = dueItem(1L, "Lamp");
        WishlistItem desk = dueItem(2L, "Desk");
        scanFinds(poison, desk);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(notificationRepository.save(any(Notification.class)))
                .thenThrow(new DataIntegrityViolationException("Column 'product_name' cannot be null"))
                .thenReturn(null);

        assertThat(alertService.fireDueReminders(NOW)).as("only the desk's reminder went out").isEqualTo(1);

        verify(transactionManager).rollback(any());
        verify(transactionManager).commit(any());
        assertThat(poison.getRemindAt()).as("still due, so the next run retries it").isEqualTo(NOW);
        assertThat(desk.getRemindAt()).isNull();
    }

    @Test
    void aConflictFoundAtCommit_isNotCounted_andDoesNotStopTheNextItem() {
        scanFinds(dueItem(1L, "Lamp"), dueItem(2L, "Desk"));
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        // The first commit fails the way a @Version check does when the user edited
        // the item meanwhile; the database then keeps neither the notification nor
        // the cleared reminder.
        doThrow(new ObjectOptimisticLockingFailureException(WishlistItem.class, 1L))
                .doNothing()
                .when(transactionManager).commit(any());

        assertThat(alertService.fireDueReminders(NOW)).isEqualTo(1);

        verify(notificationRepository, times(2)).save(any(Notification.class));
        verify(transactionManager, times(2)).commit(any());
    }

    @Test
    void anItemChangedOrDeletedSinceTheScan_isLeftAlone() {
        WishlistItem postponed = dueItem(1L, "Lamp");
        postponed.setRemindAt(NOW.plusSeconds(3600));  // the user moved the reminder after the scan
        when(wishlistItemRepository.findDueReminderIds(NOW)).thenReturn(List.of(1L, 2L));
        when(wishlistItemRepository.findByIdWithProduct(1L)).thenReturn(Optional.of(postponed));
        when(wishlistItemRepository.findByIdWithProduct(2L)).thenReturn(Optional.empty());  // removed
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        assertThat(alertService.fireDueReminders(NOW)).isZero();

        verify(notificationRepository, never()).save(any());
        assertThat(postponed.getRemindAt()).isEqualTo(NOW.plusSeconds(3600));
    }

    @Test
    void aPriceThatRoseAgainSinceTheScan_isNotAnnounced() {
        WishlistItem item = item(1L, "Lamp");
        item.getProduct().setPrice(new BigDecimal("120.00"));  // above the 100.00 baseline again
        when(wishlistItemRepository.findPriceDropIds()).thenReturn(List.of(1L));
        when(wishlistItemRepository.findByIdWithProduct(1L)).thenReturn(Optional.of(item));
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        assertThat(alertService.announcePriceDrops(NOW)).isZero();

        verify(notificationRepository, never()).save(any());
        assertThat(item.getAlertPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void restocks_recordSellOutsAndAnnounceRestocks_eachItemInItsOwnTransaction() {
        WishlistItem soldOut = item(1L, "Lamp");
        soldOut.getProduct().setInventory(0);
        WishlistItem restocked = item(2L, "Desk");
        restocked.setWasInStock(false);
        when(wishlistItemRepository.findNewlySoldOutIds()).thenReturn(List.of(1L));
        when(wishlistItemRepository.findRestockedIds()).thenReturn(List.of(2L));
        when(wishlistItemRepository.findByIdWithProduct(1L)).thenReturn(Optional.of(soldOut));
        when(wishlistItemRepository.findByIdWithProduct(2L)).thenReturn(Optional.of(restocked));
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        assertThat(alertService.announceRestocks(NOW)).as("only restocks are counted").isEqualTo(1);

        verify(transactionManager, times(2)).commit(any());
        verify(notificationRepository).save(any(Notification.class));
        assertThat(soldOut.isWasInStock()).isFalse();
        assertThat(restocked.isWasInStock()).isTrue();
    }

    // --- helpers -----------------------------------------------------------

    /** An item on a 100.00 product with 5 in stock, its baseline taken at that. */
    private static WishlistItem item(Long id, String productName) {
        Product product = new Product(productName, new BigDecimal("100.00"), "", "Acme", 5, null);
        product.setId(id);
        User user = new User();
        user.setId(7L);
        WishlistItem item = new WishlistItem(user, product, NOW.minusSeconds(86_400));
        item.setId(id);
        return item;
    }

    private static WishlistItem dueItem(Long id, String productName) {
        WishlistItem item = item(id, productName);
        item.setRemindAt(NOW);
        return item;
    }

    private void scanFinds(WishlistItem... items) {
        when(wishlistItemRepository.findDueReminderIds(NOW))
                .thenReturn(Arrays.stream(items).map(WishlistItem::getId).toList());
        for (WishlistItem item : items) {
            when(wishlistItemRepository.findByIdWithProduct(item.getId())).thenReturn(Optional.of(item));
        }
    }
}
