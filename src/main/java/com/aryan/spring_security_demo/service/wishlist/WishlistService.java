package com.aryan.spring_security_demo.service.wishlist;

import com.aryan.spring_security_demo.dto.WishlistItemDto;
import com.aryan.spring_security_demo.exception.ProductNotFoundException;
import com.aryan.spring_security_demo.exception.ResourceNotFoundException;
import com.aryan.spring_security_demo.model.Product;
import com.aryan.spring_security_demo.model.WishlistItem;
import com.aryan.spring_security_demo.repository.ProductRepository;
import com.aryan.spring_security_demo.repository.UserRepository;
import com.aryan.spring_security_demo.repository.WishlistItemRepository;
import com.aryan.spring_security_demo.security.AuthUtils;
import com.aryan.spring_security_demo.service.product.ProductServiceInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WishlistService implements WishlistServiceInterface {

    private final WishlistItemRepository wishlistItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductServiceInterface productService;
    private final AuthUtils authUtils;
    private final Clock clock;

    // Every method converts to DTOs inside its transaction: the product's category
    // and images are lazy, and open-in-view is off.

    @Override
    @Transactional(readOnly = true)
    public List<WishlistItemDto> getMyWishlist() {
        return wishlistItemRepository.findAllByUserIdWithProduct(authUtils.currentUserId())
                .stream()
                .map(this::convertToDto)
                .toList();
    }

    @Override
    @Transactional
    public AddResult add(Long productId) {
        Long userId = authUtils.currentUserId();
        Optional<WishlistItem> existing = wishlistItemRepository.findByUserIdAndProductId(userId, productId);
        if (existing.isPresent()) {
            return new AddResult(convertToDto(existing.get()), false);
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("product not found"));
        // getReferenceById: only the FK is needed, so don't load the user row.
        WishlistItem item = new WishlistItem(userRepository.getReferenceById(userId), product, clock.instant());
        return new AddResult(convertToDto(wishlistItemRepository.save(item)), true);
    }

    @Override
    @Transactional
    public void remove(Long productId) {
        wishlistItemRepository.delete(findMine(productId));
    }

    @Override
    @Transactional
    public WishlistItemDto setReminder(Long productId, Instant remindAt) {
        WishlistItem item = findMine(productId);
        item.setRemindAt(remindAt);
        return convertToDto(item);
    }

    @Override
    @Transactional
    public WishlistItemDto clearReminder(Long productId) {
        WishlistItem item = findMine(productId);
        item.setRemindAt(null);
        return convertToDto(item);
    }

    @Override
    @Transactional
    public WishlistItemDto setAlerts(Long productId, boolean enabled) {
        WishlistItem item = findMine(productId);
        if (enabled && !item.isAlertsEnabled()) {
            // Changes made while alerts were off are not replayed — start fresh.
            item.resetAlertBaseline();
        }
        item.setAlertsEnabled(enabled);
        return convertToDto(item);
    }

    private WishlistItem findMine(Long productId) {
        return wishlistItemRepository.findByUserIdAndProductId(authUtils.currentUserId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("product is not in your wishlist"));
    }

    private WishlistItemDto convertToDto(WishlistItem item) {
        WishlistItemDto dto = new WishlistItemDto();
        dto.setProduct(productService.convertToDto(item.getProduct()));
        dto.setAddedAt(item.getCreatedAt());
        dto.setPriceWhenAdded(item.getPriceWhenAdded());
        dto.setRemindAt(item.getRemindAt());
        dto.setAlertsEnabled(item.isAlertsEnabled());
        return dto;
    }
}
