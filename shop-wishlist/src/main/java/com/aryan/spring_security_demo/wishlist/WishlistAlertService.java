package com.aryan.spring_security_demo.wishlist;

import com.aryan.spring_security_demo.notification.Notification;
import com.aryan.spring_security_demo.notification.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Turns wishlist state into in-app notifications. Driven by {@link WishlistAlertJob}
 * on a schedule; kept separate from the job so each phase runs in its own
 * transaction (called through the proxy) and tests can call it directly.
 *
 * <p>In every phase the notification is written and the item's baseline moved in
 * the <em>same</em> transaction, which is what makes each event fire exactly once:
 * if the transaction rolls back (say, the user edited the item at that moment and
 * the {@code @Version} check failed), neither happens, and the next run retries.
 *
 * <p>Price and stock are polled rather than hooked into every place that changes
 * them (admin edits, order placement, cancellation restock, …), so no future code
 * path can forget to raise an alert.
 */
@Service
@RequiredArgsConstructor
public class WishlistAlertService {

    private final WishlistItemRepository wishlistItemRepository;
    private final NotificationRepository notificationRepository;

    /** Fire every reminder due at or before {@code now}. Reminders are one-shot: each is cleared as it fires. */
    @Transactional
    public int fireDueReminders(Instant now) {
        List<WishlistItem> due = wishlistItemRepository.findDueReminders(now);
        for (WishlistItem item : due) {
            notificationRepository.save(Notification.reminder(item.getUser(), item.getProduct(), now));
            item.setRemindAt(null);
        }
        return due.size();
    }

    /**
     * Announce prices that fell below the lowest price already announced, then
     * lower the baseline to the new price. A price that bounces back up and down
     * again is only announced if it sets a new low, so a flickering sale doesn't
     * spam the user.
     */
    @Transactional
    public int announcePriceDrops(Instant now) {
        List<WishlistItem> dropped = wishlistItemRepository.findPriceDrops();
        for (WishlistItem item : dropped) {
            BigDecimal price = item.getProduct().getPrice();
            notificationRepository.save(Notification.priceDrop(item.getUser(), item.getProduct(), item.getAlertPrice(), price, now));
            item.setAlertPrice(price);
        }
        return dropped.size();
    }

    /**
     * Announce products that came back in stock. Sell-outs are recorded silently
     * first, so a product that sold out and was restocked since the last run is
     * recognised as a restock.
     */
    @Transactional
    public int announceRestocks(Instant now) {
        wishlistItemRepository.findNewlySoldOut().forEach(item -> item.setWasInStock(false));
        List<WishlistItem> restocked = wishlistItemRepository.findRestocked();
        for (WishlistItem item : restocked) {
            notificationRepository.save(Notification.backInStock(item.getUser(), item.getProduct(), now));
            item.setWasInStock(true);
        }
        return restocked.size();
    }
}
