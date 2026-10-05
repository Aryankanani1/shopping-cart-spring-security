package com.aryan.spring_security_demo.identity;

import com.aryan.spring_security_demo.cart.CartDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Response shape of {@link UserController}. Security filters are disabled: access
 * to an account is covered by the integration tests.
 */
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserServiceInterface userService;

    // Regression: the account embedded the user's whole order history, unpaginated,
    // and the storefront fetches it on every cart change. Order history is only
    // served paginated, from GET /orders.
    @Test
    void getUser_returnsTheCartButNoOrderHistory() throws Exception {
        CartDto cart = new CartDto();
        cart.setCartId(3L);
        UserDto user = new UserDto();
        user.setId(7L);
        user.setEmail("ada@example.com");
        user.setCart(cart);
        when(userService.getUserDtoById(7L)).thenReturn(user);

        mockMvc.perform(get("/api/v1/users/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("ada@example.com"))
                .andExpect(jsonPath("$.data.cart.cartId").value(3))
                .andExpect(jsonPath("$.data", not(hasKey("orders"))));
    }
}
