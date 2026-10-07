package com.aryan.spring_security_demo;

import com.aryan.spring_security_demo.cart.CartNotFoundException;
import com.aryan.spring_security_demo.catalog.CategoryNotFoundException;
import com.aryan.spring_security_demo.catalog.ImageNotFoundException;
import com.aryan.spring_security_demo.catalog.InsufficientStockException;
import com.aryan.spring_security_demo.catalog.InvalidImageException;
import com.aryan.spring_security_demo.catalog.ProductInUseException;
import com.aryan.spring_security_demo.catalog.ProductNotFoundException;
import com.aryan.spring_security_demo.common.exception.AlreadyExistsException;
import com.aryan.spring_security_demo.common.exception.GlobalExceptionHandler;
import com.aryan.spring_security_demo.common.exception.InvalidSortException;
import com.aryan.spring_security_demo.common.exception.ResourceNotFoundException;
import com.aryan.spring_security_demo.identity.InvalidPasswordException;
import com.aryan.spring_security_demo.identity.InvalidRefreshTokenException;
import com.aryan.spring_security_demo.identity.UserNotFoundException;
import com.aryan.spring_security_demo.order.EmptyCartException;
import com.aryan.spring_security_demo.order.InvalidCursorException;
import com.aryan.spring_security_demo.order.InvalidOrderStateException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.ProblemDetail;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * The error response every domain exception produces: status, title, detail and
 * field errors. Spring picks the handler method the way it does for a request
 * (ExceptionHandlerMethodResolver), so this holds however the handler is
 * organised, and a change to any client-visible error shows up here.
 */
class ExceptionMappingTest {

    private static final GlobalExceptionHandler HANDLER = new GlobalExceptionHandler();
    private static final ExceptionHandlerMethodResolver RESOLVER =
            new ExceptionHandlerMethodResolver(GlobalExceptionHandler.class);

    static Stream<Arguments> exceptions() {
        return Stream.of(
                // 404
                arguments(new ResourceNotFoundException("order not found!"), 404, "Resource not found", "order not found!", null),
                arguments(new CartNotFoundException("cart not found"), 404, "Resource not found", "cart not found", null),
                arguments(new ProductNotFoundException("product not found"), 404, "Resource not found", "product not found", null),
                arguments(new CategoryNotFoundException("category not found"), 404, "Resource not found", "category not found", null),
                arguments(new ImageNotFoundException("Image not found"), 404, "Resource not found", "Image not found", null),
                arguments(new UserNotFoundException("failed to find user"), 404, "Resource not found", "failed to find user", null),
                // 409
                arguments(new AlreadyExistsException("Books already exists"), 409, "Resource already exists", "Books already exists", null),
                arguments(new InvalidOrderStateException("Cannot change order status from SHIPPED to CANCELLED"), 409,
                        "Invalid order state", "Cannot change order status from SHIPPED to CANCELLED", null),
                arguments(new InsufficientStockException("Kettle", 2), 409, "Insufficient stock", "Only 2 of Kettle left in stock", null),
                arguments(new ProductInUseException("in use"), 409, "Product in use", "in use", null),
                arguments(new EmptyCartException("Your cart is empty"), 409, "Cart is empty", "Your cart is empty", null),
                // 401: the detail never reveals which check failed
                arguments(new InvalidRefreshTokenException("Refresh token has expired"), 401, "Authentication failed",
                        "Invalid or expired refresh token", null),
                // 400
                arguments(new InvalidSortException("color", List.of("name")), 400, "Invalid sort parameter",
                        "Unsupported sort property 'color'. Allowed sort fields: [name]", null),
                arguments(new InvalidCursorException("abc"), 400, "Invalid pagination cursor", "Invalid pagination cursor: 'abc'", null),
                arguments(new InvalidImageException("a.txt is empty"), 400, "Invalid image", "a.txt is empty", null),
                arguments(new InvalidPasswordException("currentPassword", "Current password is incorrect"), 400,
                        "Validation failed", "Current password is incorrect",
                        Map.of("currentPassword", "Current password is incorrect")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("exceptions")
    void mapsToItsErrorResponse(RuntimeException exception, int status, String title, String detail,
                                Map<String, String> errors) throws Exception {
        Method method = RESOLVER.resolveMethod(exception);
        assertThat(method).as("a handler for " + exception.getClass().getSimpleName()).isNotNull();

        ProblemDetail problem = (ProblemDetail) method.invoke(HANDLER, exception);

        assertThat(problem.getStatus()).isEqualTo(status);
        assertThat(problem.getTitle()).isEqualTo(title);
        assertThat(problem.getDetail()).isEqualTo(detail);
        if (errors == null) {
            assertThat(problem.getProperties()).as("no field errors").satisfiesAnyOf(
                    properties -> assertThat(properties).isNull(),
                    properties -> assertThat(properties).doesNotContainKey("errors"));
        } else {
            assertThat(problem.getProperties()).containsEntry("errors", errors);
        }
    }
}
