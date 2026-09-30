package com.aryan.spring_security_demo.controller;

import com.aryan.spring_security_demo.dto.WishlistItemDto;
import com.aryan.spring_security_demo.request.WishlistAlertsRequest;
import com.aryan.spring_security_demo.request.WishlistReminderRequest;
import com.aryan.spring_security_demo.response.ApiResponse;
import com.aryan.spring_security_demo.service.wishlist.WishlistServiceInterface;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

/**
 * The caller's own wishlist ("my wishlist" — there is no user id in any path).
 * Items are addressed by product id. Requires authentication via the catch-all
 * rule in ShopConfig.
 */
@RestController
@RequestMapping("${api.prefix}/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistServiceInterface wishlistService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<WishlistItemDto>>> getWishlist() {
        return ResponseEntity.ok(new ApiResponse<>("success!", wishlistService.getMyWishlist()));
    }

    /**
     * Save a product. PUT because it is idempotent: 201 Created the first time,
     * 200 with the existing item after that — so a double click is harmless.
     */
    @PutMapping("/items/{productId}")
    public ResponseEntity<ApiResponse<?>> addItem(@PathVariable Long productId) {
        WishlistServiceInterface.AddResult result = wishlistService.add(productId);
        if (result.created()) {
            return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().build().toUri())
                    .body(new ApiResponse<>("Added to wishlist", result.item()));
        }
        return ResponseEntity.ok(new ApiResponse<>("Already in wishlist", result.item()));
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<ApiResponse<?>> removeItem(@PathVariable Long productId) {
        wishlistService.remove(productId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/items/{productId}/reminder")
    public ResponseEntity<ApiResponse<?>> setReminder(@PathVariable Long productId,
                                                      @Valid @RequestBody WishlistReminderRequest request) {
        WishlistItemDto item = wishlistService.setReminder(productId, request.getRemindAt());
        return ResponseEntity.ok(new ApiResponse<>("Reminder set", item));
    }

    /** Clears the reminder. Returns the updated item (not 204) so the client can refresh its row. */
    @DeleteMapping("/items/{productId}/reminder")
    public ResponseEntity<ApiResponse<?>> clearReminder(@PathVariable Long productId) {
        WishlistItemDto item = wishlistService.clearReminder(productId);
        return ResponseEntity.ok(new ApiResponse<>("Reminder cleared", item));
    }

    @PutMapping("/items/{productId}/alerts")
    public ResponseEntity<ApiResponse<?>> setAlerts(@PathVariable Long productId,
                                                    @Valid @RequestBody WishlistAlertsRequest request) {
        WishlistItemDto item = wishlistService.setAlerts(productId, request.getEnabled());
        return ResponseEntity.ok(new ApiResponse<>("Alerts updated", item));
    }
}
