package com.aryan.spring_security_demo;

import com.aryan.spring_security_demo.cart.CartModule;
import com.aryan.spring_security_demo.catalog.CatalogModule;
import com.aryan.spring_security_demo.common.CommonModule;
import com.aryan.spring_security_demo.identity.IdentityModule;
import com.aryan.spring_security_demo.notification.NotificationModule;
import com.aryan.spring_security_demo.order.OrderModule;
import com.aryan.spring_security_demo.wishlist.WishlistModule;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * The application is the list of modules below. Unlike
 * {@code @SpringBootApplication}, this class does not component-scan its package:
 * each module scans only its own package (see
 * {@link com.aryan.spring_security_demo.common.ModuleConfiguration}), so what the
 * application contains is stated here rather than implied by where files happen
 * to sit. A class in a package no module covers is never picked up — the
 * {@code ModuleCompositionTest} fails if that happens.
 *
 * <p>{@code @EnableAutoConfiguration} still registers this package for JPA, so
 * entities and repositories are found in every module.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import({
        CommonModule.class,
        IdentityModule.class,
        CatalogModule.class,
        CartModule.class,
        OrderModule.class,
        NotificationModule.class,
        WishlistModule.class,
})
public class SpringSecurityDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringSecurityDemoApplication.class, args);
	}
}
