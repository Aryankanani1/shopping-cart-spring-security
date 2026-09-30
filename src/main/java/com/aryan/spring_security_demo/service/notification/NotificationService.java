package com.aryan.spring_security_demo.service.notification;

import com.aryan.spring_security_demo.dto.NotificationDto;
import com.aryan.spring_security_demo.exception.ResourceNotFoundException;
import com.aryan.spring_security_demo.model.Notification;
import com.aryan.spring_security_demo.repository.NotificationRepository;
import com.aryan.spring_security_demo.security.AuthUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class NotificationService implements NotificationServiceInterface {

    private final NotificationRepository notificationRepository;
    private final AuthUtils authUtils;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationDto> getMyNotifications(Pageable pageable) {
        // Drop any client sort: the order is fixed (newest first) by the query.
        Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return notificationRepository
                .findByUserIdOrderByCreatedAtDescIdDesc(authUtils.currentUserId(), page)
                .map(this::convertToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public long countMyUnread() {
        return notificationRepository.countByUserIdAndReadAtIsNull(authUtils.currentUserId());
    }

    @Override
    @Transactional
    public void markRead(Long notificationId) {
        Notification notification = findMine(notificationId);
        if (notification.getReadAt() == null) {
            notification.setReadAt(clock.instant());
        }
    }

    @Override
    @Transactional
    public void markAllRead() {
        notificationRepository.markAllRead(authUtils.currentUserId(), clock.instant());
    }

    @Override
    @Transactional
    public void delete(Long notificationId) {
        notificationRepository.delete(findMine(notificationId));
    }

    private Notification findMine(Long notificationId) {
        return notificationRepository.findByIdAndUserId(notificationId, authUtils.currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("notification not found"));
    }

    private NotificationDto convertToDto(Notification notification) {
        NotificationDto dto = new NotificationDto();
        dto.setId(notification.getId());
        dto.setType(notification.getType());
        // Reading the id off the lazy proxy doesn't load the product row.
        dto.setProductId(notification.getProduct() == null ? null : notification.getProduct().getId());
        dto.setProductName(notification.getProductName());
        dto.setOldPrice(notification.getOldPrice());
        dto.setNewPrice(notification.getNewPrice());
        dto.setCreatedAt(notification.getCreatedAt());
        dto.setRead(notification.getReadAt() != null);
        return dto;
    }
}
