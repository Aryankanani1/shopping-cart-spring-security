package com.aryan.spring_security_demo.common.web;

import com.aryan.spring_security_demo.common.exception.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Checks {@code page} and {@code sort} for the endpoints that take a
 * {@link Pageable} or {@link Sort}, before Spring Data reads them: two values it
 * can't handle failed deep inside it, as 500s (found by fuzzing the API).
 *
 * <ul>
 *   <li>A {@code page} so large that its row offset passes {@link Integer#MAX_VALUE}
 *       overflows when the query is bound.</li>
 *   <li>Spring Data decodes each {@code sort} value again, after the server has, so
 *       a {@code %} in it is an invalid escape. A sort is property names and
 *       directions ({@code price,desc}), so anything else is refused here; which
 *       properties are allowed is still the endpoint's own check.</li>
 * </ul>
 */
public class PagingParameterInterceptor implements HandlerInterceptor {

    /**
     * No listing here has this many pages, and with {@code max-page-size} (100) the
     * row offset stays far below {@code Integer.MAX_VALUE}.
     */
    static final long MAX_PAGE = 1_000_000;

    /**
     * Letters, digits, {@code _ . ,} and nothing else: one character class, no nested
     * repetition, so a crafted value can't make matching slow (ReDoS).
     */
    private static final Pattern SORT = Pattern.compile("[A-Za-z0-9_.,]*");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || !takesPagingParameters(method)) {
            return true;
        }
        String page = request.getParameter("page");
        if (page != null && parse(page) > MAX_PAGE) {
            throw new BadRequestException("Invalid page", "page must be at most " + MAX_PAGE);
        }
        String[] sorts = request.getParameterValues("sort");
        if (sorts != null && Arrays.stream(sorts).anyMatch(sort -> !SORT.matcher(sort).matches())) {
            throw new BadRequestException("Invalid sort parameter",
                    "sort must be property names and directions, like price,desc");
        }
        return true;
    }

    private static boolean takesPagingParameters(HandlerMethod method) {
        return Arrays.stream(method.getMethodParameters())
                .map(parameter -> parameter.getParameterType())
                .anyMatch(type -> Pageable.class.isAssignableFrom(type) || Sort.class.isAssignableFrom(type));
    }

    /** The page number, or 0 for one Spring Data would ignore anyway (not a number). */
    private static long parse(String page) {
        try {
            return Long.parseLong(page.trim());
        } catch (NumberFormatException e) {
            return page.trim().matches("\\d+") ? Long.MAX_VALUE : 0;  // digits too many for a long
        }
    }
}
