package com.aryan.spring_security_demo.service.notification;

import com.aryan.spring_security_demo.dto.NotificationDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * The authenticated caller's own in-app inbox. No method takes a user id; a
 * notification id that isn't the caller's is treated as not found (404).
 */
public interface NotificationServiceInterface {

    /** Newest first. Any client-supplied sort is ignored. */
    Page<NotificationDto> getMyNotifications(Pageable pageable);

    long countMyUnread();

    void markRead(Long notificationId);

    void markAllRead();

    void delete(Long notificationId);
}
