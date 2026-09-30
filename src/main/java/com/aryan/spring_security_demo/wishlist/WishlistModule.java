package com.aryan.spring_security_demo.wishlist;

import com.aryan.spring_security_demo.common.ModuleConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;

/**
 * Wishlists, dated reminders and price/stock alerts (including the scheduled
 * {@link WishlistAlertJob}). Scans only {@code com.aryan.spring_security_demo.wishlist};
 * see {@link ModuleConfiguration}.
 *
 * <p>The one optional module: {@code app.modules.wishlist.enabled=false} (env
 * {@code APP_MODULES_WISHLIST_ENABLED}) drops the whole module — its endpoints,
 * services and the alert job — without touching code. Because this class is the
 * module's only entry point, the condition covers everything in the package; no
 * other module depends on it. Its tables and repository stay (the repository is
 * auto-configured app-wide), so switching it back on picks up where it left off.
 */
@ModuleConfiguration
@ConditionalOnBooleanProperty(name = "app.modules.wishlist.enabled", matchIfMissing = true)
public class WishlistModule {
}
