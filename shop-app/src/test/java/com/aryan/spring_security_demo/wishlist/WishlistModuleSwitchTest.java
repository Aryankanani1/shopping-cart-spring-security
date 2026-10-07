package com.aryan.spring_security_demo.wishlist;

import com.aryan.spring_security_demo.catalog.ProductController;
import com.aryan.spring_security_demo.notification.NotificationController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code app.modules.wishlist.enabled=false} drops the whole wishlist module —
 * endpoints, services and the scheduled alert job — while every other module
 * keeps working. (With the default, the module is on: see WishlistIntegrationTest.)
 */
@SpringBootTest(properties = "app.modules.wishlist.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WishlistModuleSwitchTest {

    @Autowired private ApplicationContext context;
    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("switched off: none of the module's beans exist")
    void disabled_moduleBeansAreAbsent() {
        assertThat(context.getBeanNamesForType(WishlistModule.class)).isEmpty();
        assertThat(context.getBeanNamesForType(WishlistController.class)).isEmpty();
        assertThat(context.getBeanNamesForType(WishlistServiceInterface.class)).isEmpty();
        assertThat(context.getBeanNamesForType(WishlistAlertService.class)).isEmpty();
        assertThat(context.getBeanNamesForType(WishlistAlertJob.class)).as("no scheduled scan").isEmpty();
    }

    @Test
    @DisplayName("switched off: the other modules are unaffected")
    void disabled_otherModulesStillLoad() {
        assertThat(context.getBeanNamesForType(ProductController.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(NotificationController.class)).hasSize(1);
    }

    @Test
    @WithMockUser
    @DisplayName("switched off: the wishlist endpoints are gone (404)")
    void disabled_endpointsAreGone() throws Exception {
        mockMvc.perform(get("/api/v1/wishlist")).andExpect(status().isNotFound());
    }
}
