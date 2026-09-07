package com.aryan.spring_security_demo.security;

import com.aryan.spring_security_demo.model.Cart;
import com.aryan.spring_security_demo.model.Role;
import com.aryan.spring_security_demo.model.User;
import com.aryan.spring_security_demo.repository.CartItemRepository;
import com.aryan.spring_security_demo.repository.CartRepository;
import com.aryan.spring_security_demo.repository.OrderRepository;
import com.aryan.spring_security_demo.repository.RefreshTokenRepository;
import com.aryan.spring_security_demo.repository.RoleRepository;
import com.aryan.spring_security_demo.repository.UserRepository;
import com.aryan.spring_security_demo.security.user.UserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization tests for the secured endpoints — the negative paths as much as
 * the happy one. Two distinct guards are exercised:
 *
 * <ul>
 *   <li><b>Role gate at the edge</b> (filter chain): catalog writes are
 *       {@code ROLE_ADMIN}-only. Anonymous → 401, wrong role → 403, admin passes.
 *       Only authorities matter here, so {@link WithMockUser} is enough.</li>
 *   <li><b>Object-level ownership</b> (service layer, {@link AuthUtils}): a user
 *       may only touch their own cart/order (IDOR). This needs the real principal
 *       <em>id</em>, which {@code @WithMockUser} cannot supply — so those tests
 *       authenticate with the app's own {@link UserDetails} via a request
 *       post-processor, carrying a genuine persisted user id.</li>
 * </ul>
 *
 * <p>Methods are flat (no {@code @Nested}): nested classes were silently not
 * discovered by surefire here, and a security test that doesn't run is worse than
 * no test — it reports green while asserting nothing.
 *
 * <p>Runs the full HTTP → security → service → JPA stack against H2 ({@code test}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAuthorizationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    private Long aliceId;
    private Long bobId;
    private Long aliceCartId;
    private Long bobCartId;

    @BeforeEach
    void setUp() {
        // Children first (FK order), then owners, so each test starts clean.
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        Role customer = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_CUSTOMER")));

        User alice = persistUser("alice@example.com", customer);
        User bob = persistUser("bob@example.com", customer);
        aliceId = alice.getId();
        bobId = bob.getId();
        aliceCartId = persistCartFor(alice);
        bobCartId = persistCartFor(bob);
    }

    // --- Role gate at the edge: DELETE /products/{id} is admin-only. Hitting a
    // non-existent id separates authorization (getting past the gate) from
    // business logic (404 only once past it). ----------------------------------

    @Test
    @DisplayName("catalog write: anonymous → 401")
    void deleteProduct_anonymous_isUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/v1/products/{id}", 999_999))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    @DisplayName("catalog write: authenticated customer → 403 (wrong authority)")
    void deleteProduct_asCustomer_isForbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/products/{id}", 999_999))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access denied"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("catalog write: admin passes the gate → 404 (authorized, product absent)")
    void deleteProduct_asAdmin_passesAuthorization() throws Exception {
        mockMvc.perform(delete("/api/v1/products/{id}", 999_999))
                .andExpect(status().isNotFound());
    }

    // --- Object-level ownership (IDOR) on the cart: owner and admin succeed, a
    // stranger is denied, anonymous is 401. ------------------------------------

    @Test
    @DisplayName("cart: owner reads own cart → 200")
    void getCart_asOwner_isOk() throws Exception {
        mockMvc.perform(get("/api/v1/carts/{cartId}", aliceCartId).with(asCustomer(aliceId, "alice@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cartId").value(aliceCartId));
    }

    @Test
    @DisplayName("cart: non-owner reads another user's cart → 403 (IDOR blocked)")
    void getCart_asNonOwner_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/carts/{cartId}", bobCartId).with(asCustomer(aliceId, "alice@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access denied"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("cart: admin may read any user's cart → 200")
    void getCart_asAdmin_isOk() throws Exception {
        mockMvc.perform(get("/api/v1/carts/{cartId}", bobCartId).with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cartId").value(bobCartId));
    }

    @Test
    @DisplayName("cart: anonymous → 401")
    void getCart_anonymous_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/carts/{cartId}", aliceCartId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    // --- Object-level ownership (IDOR) on order history via ?userId= ----------

    @Test
    @DisplayName("orders: reading own order history → 200")
    void getUserOrders_forSelf_isOk() throws Exception {
        mockMvc.perform(get("/api/v1/orders").param("userId", String.valueOf(aliceId))
                        .with(asCustomer(aliceId, "alice@example.com")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("orders: reading another user's order history → 403 (IDOR blocked)")
    void getUserOrders_forAnotherUser_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/orders").param("userId", String.valueOf(bobId))
                        .with(asCustomer(aliceId, "alice@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access denied"));
    }

    // --- helpers -----------------------------------------------------------

    private User persistUser(String email, Role role) {
        User user = new User();
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail(email);
        user.setPassword("irrelevant"); // no login here — we inject the principal directly
        user.setRoles(Set.of(role));
        return userRepository.save(user);
    }

    private Long persistCartFor(User user) {
        Cart cart = new Cart();
        cart.setUser(user);
        return cartRepository.save(cart).getId();
    }

    /** Authenticate the request as the app's own principal, carrying a real user id. */
    private static RequestPostProcessor asCustomer(Long id, String email) {
        return authAs(new UserDetails(id, email, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
    }

    private static RequestPostProcessor asAdmin() {
        return authAs(new UserDetails(-1L, "admin@example.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private static RequestPostProcessor authAs(UserDetails principal) {
        return authentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
