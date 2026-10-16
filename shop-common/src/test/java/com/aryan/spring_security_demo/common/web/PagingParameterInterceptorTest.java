package com.aryan.spring_security_demo.common.web;

import com.aryan.spring_security_demo.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The page and sort values Spring Data couldn't handle are refused before it reads them. */
class PagingParameterInterceptorTest {

    private final PagingParameterInterceptor interceptor = new PagingParameterInterceptor();

    /** Stand-ins for controller methods, with and without paging. */
    static class Endpoints {
        void list(Pageable pageable) {
        }

        void one(Long id) {
        }
    }

    @Test
    void aPageTooFarOut_isRefused() {
        for (String page : new String[] {String.valueOf(PagingParameterInterceptor.MAX_PAGE + 1),
                "2147483647", "99999999999999999999999"}) {
            assertThatThrownBy(() -> check(paged(), request("page", page)))
                    .as("page=%s", page).isInstanceOf(BadRequestException.class).hasMessageContaining("page");
        }
    }

    @Test
    void ordinaryPages_andOnesSpringDataIgnores_pass() {
        for (String page : new String[] {"0", "3", String.valueOf(PagingParameterInterceptor.MAX_PAGE), "-1", "abc", ""}) {
            assertThat(check(paged(), request("page", page))).as("page=%s", page).isTrue();
        }
    }

    // Spring Data decodes sort values a second time, so a % in one is an invalid escape.
    @Test
    void aSortThatIsntPropertiesAndDirections_isRefused() {
        for (String sort : new String[] {"%", "price%2Cdesc", "name;drop", "nämé", "price desc"}) {
            assertThatThrownBy(() -> check(paged(), request("sort", sort)))
                    .as("sort=%s", sort).isInstanceOf(BadRequestException.class).hasMessageContaining("sort");
        }
    }

    @Test
    void propertySortsWithDirections_pass() {
        for (String sort : new String[] {"price,desc", "name", "createdAt,asc", "category.name,desc", ""}) {
            assertThat(check(paged(), request("sort", sort))).as("sort=%s", sort).isTrue();
        }
    }

    @Test
    void endpointsWithoutPaging_areLeftAlone() throws NoSuchMethodException {
        HandlerMethod one = new HandlerMethod(new Endpoints(), Endpoints.class.getDeclaredMethod("one", Long.class));

        assertThat(check(one, request("page", "99999999999"))).isTrue();
        assertThat(check(one, request("sort", "%"))).isTrue();
        assertThat(interceptor.preHandle(request("sort", "%"), new MockHttpServletResponse(), "not a controller method")).isTrue();
    }

    private boolean check(HandlerMethod handler, MockHttpServletRequest request) {
        return interceptor.preHandle(request, new MockHttpServletResponse(), handler);
    }

    private static HandlerMethod paged() {
        try {
            return new HandlerMethod(new Endpoints(), Endpoints.class.getDeclaredMethod("list", Pageable.class));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    private static MockHttpServletRequest request(String name, String value) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        request.addParameter(name, value);
        return request;
    }
}
