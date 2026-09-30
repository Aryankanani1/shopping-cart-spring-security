package com.aryan.spring_security_demo.controller;

import com.aryan.spring_security_demo.dto.NotificationDto;
import com.aryan.spring_security_demo.response.ApiResponse;
import com.aryan.spring_security_demo.response.PagedResponse;
import com.aryan.spring_security_demo.service.notification.NotificationServiceInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * The caller's own in-app inbox (wishlist reminders and price/stock alerts).
 * Requires authentication via the catch-all rule in ShopConfig.
 */
@RestController
@RequestMapping("${api.prefix}/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private static final int DEFAULT_SIZE = 20;

    private final NotificationServiceInterface notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<NotificationDto>>> getNotifications(
            @PageableDefault(size = DEFAULT_SIZE) Pageable pageable) {
        PagedResponse<NotificationDto> page = PagedResponse.from(notificationService.getMyNotifications(pageable));
        return ResponseEntity.ok(new ApiResponse<>("success!", page));
    }

    /** Cheap count for the header badge, which polls it. */
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount() {
        return ResponseEntity.ok(new ApiResponse<>("success!", notificationService.countMyUnread()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<?>> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<?>> markAllRead() {
        notificationService.markAllRead();
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> delete(@PathVariable Long id) {
        notificationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
