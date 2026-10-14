package com.aryan.spring_security_demo.wishlist;

import com.aryan.spring_security_demo.notification.Notification;
import com.aryan.spring_security_demo.notification.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;

/**
 * Turns wishlist state into in-app notifications. Driven by {@link WishlistAlertJob}
 * on a schedule; kept separate from the job so tests can call it directly.
 *
 * <p>Each phase scans for the ids of the items that need action, then handles
 * every item in a <em>transaction of its own</em>, started in code with a
 * {@link TransactionTemplate}: {@code @Transactional} can't give each pass of a
 * loop its own transaction, as it only applies to calls that come in through the
 * Spring proxy. In that transaction the notification is written and the item's
 * baseline moved together, which is what makes each event fire exactly once: if
 * it rolls back (say, the user edited the item at that moment and the
 * {@code @Version} check failed), neither happens, and the next run retries that
 * item. Since every item commits on its own, one that fails doesn't hold back the
 * other users' alerts, and no row stays locked for longer than its own update.
 *
 * <p>Price and stock are polled rather than hooked into every place that changes
 * them (admin edits, order placement, cancellation restock, …), so no future code
 * path can forget to raise an alert.
 */
@Service
public class WishlistAlertService {

    private static final Logger log = LoggerFactory.getLogger(WishlistAlertService.class);

    private final WishlistItemRepository wishlistItemRepository;
    private final NotificationRepository notificationRepository;
    private final TransactionTemplate perItemTransaction;

    public WishlistAlertService(WishlistItemRepository wishlistItemRepository,
                                NotificationRepository notificationRepository,
                                PlatformTransactionManager transactionManager) {
        this.wishlistItemRepository = wishlistItemRepository;
        this.notificationRepository = notificationRepository;
        this.perItemTransaction = new TransactionTemplate(transactionManager);
        // A new transaction even if the caller has one open: committing each item
        // on its own is the point.
        this.perItemTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Fire every reminder due at or before {@code now}. Reminders are one-shot: each is cleared as it fires. */
    public int fireDueReminders(Instant now) {
        return eachInOwnTransaction("reminder", wishlistItemRepository.findDueReminderIds(now), item -> {
            if (!item.isReminderDue(now)) {
                return false;
            }
            notificationRepository.save(Notification.reminder(item.getUser(), item.getProduct(), now));
            item.setRemindAt(null);
            return true;
        });
    }

    /**
     * Announce prices that fell below the lowest price already announced, then
     * lower the baseline to the new price. A price that bounces back up and down
     * again is only announced if it sets a new low, so a flickering sale doesn't
     * spam the user.
     */
    public int announcePriceDrops(Instant now) {
        return eachInOwnTransaction("price-drop", wishlistItemRepository.findPriceDropIds(), item -> {
            if (!item.hasPriceDrop()) {
                return false;
            }
            BigDecimal price = item.getProduct().getPrice();
            notificationRepository.save(Notification.priceDrop(item.getUser(), item.getProduct(), item.getAlertPrice(), price, now));
            item.setAlertPrice(price);
            return true;
        });
    }

    /**
     * Announce products that came back in stock. Sell-outs are recorded silently
     * first, so a product that sold out and was restocked since the last run is
     * recognised as a restock.
     */
    public int announceRestocks(Instant now) {
        eachInOwnTransaction("sold-out", wishlistItemRepository.findNewlySoldOutIds(), item -> {
            if (!item.isNewlySoldOut()) {
                return false;
            }
            item.setWasInStock(false);
            return true;
        });
        return eachInOwnTransaction("back-in-stock", wishlistItemRepository.findRestockedIds(), item -> {
            if (!item.isRestocked()) {
                return false;
            }
            notificationRepository.save(Notification.backInStock(item.getUser(), item.getProduct(), now));
            item.setWasInStock(true);
            return true;
        });
    }

    /**
     * Load each scanned item and pass it to {@code handle} in a new transaction,
     * committed before the next item starts. {@code handle} checks the item still
     * needs action (it may have changed since the scan) and returns whether it
     * acted; an item deleted since the scan is skipped. An item that fails is
     * rolled back and logged, and the loop goes on to the next.
     *
     * @return how many items {@code handle} acted on
     */
    private int eachInOwnTransaction(String phase, List<Long> ids, Predicate<WishlistItem> handle) {
        int acted = 0;
        for (Long id : ids) {
            try {
                Boolean didAct = perItemTransaction.execute(status -> wishlistItemRepository.findByIdWithProduct(id)
                        .map(handle::test)
                        .orElse(false));
                if (Boolean.TRUE.equals(didAct)) {
                    acted++;
                }
            } catch (RuntimeException e) {
                // Thrown by handle, and then rolled back by the template, or by the
                // commit itself (a version conflict surfaces at flush). Either way
                // nothing of this item was saved, so the next run tries it again.
                log.warn("Wishlist scan: {} for item {} failed, will retry next run", phase, id, e);
            }
        }
        return acted;
    }
}
