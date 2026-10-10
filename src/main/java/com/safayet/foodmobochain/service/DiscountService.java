package com.safayet.foodmobochain.service;

import com.safayet.foodmobochain.dto.DiscountDTO;
import com.safayet.foodmobochain.model.Discount;
import com.safayet.foodmobochain.model.enums.DiscountType;
import com.safayet.foodmobochain.repository.DiscountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DiscountService {

    private final DiscountRepository discountRepository;

    public List<Discount> activeDiscounts() {
        LocalDateTime now = LocalDateTime.now(com.safayet.foodmobochain.config.MongoConfig.APP_ZONE);
        return discountRepository.findByActiveTrueAndStartsAtLessThanEqualAndEndsAtGreaterThanEqualOrderByEndsAtAsc(now, now);
    }

    public List<Discount> allDiscounts() {
        return discountRepository.findAllByOrderByEndsAtDesc();
    }

    public AppliedDiscount calculate(String code, BigDecimal subtotal) {
        if (code == null || code.isBlank()) {
            return new AppliedDiscount(null, BigDecimal.ZERO);
        }

        String normalized = code.trim().toUpperCase(java.util.Locale.ROOT);
        // Existing staged discount documents were seeded with 'code' only.
        // Prefer normalized lookup but accept the old field without touching existing records.
        Discount discount = discountRepository.findByCodeNormalized(normalized)
                .or(() -> discountRepository.findByCode(normalized))
                .orElseThrow(() -> new InvalidDiscountException("Discount code was not found."));
        LocalDateTime now = LocalDateTime.now(com.safayet.foodmobochain.config.MongoConfig.APP_ZONE);
        if (!discount.isActive() || discount.getStartsAt().isAfter(now) || discount.getEndsAt().isBefore(now)) {
            throw new InvalidDiscountException("This discount is not currently active.");
        }
        if (subtotal.compareTo(discount.getMinimumOrder()) < 0) {
            throw new InvalidDiscountException("Minimum order for this discount is ৳" + discount.getMinimumOrder().setScale(0, RoundingMode.HALF_UP) + ".");
        }

        BigDecimal amount;
        if (discount.getType() == DiscountType.PERCENTAGE) {
            amount = subtotal.multiply(discount.getValue())
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        } else {
            amount = discount.getValue();
        }

        if (discount.getMaximumDiscount() != null && amount.compareTo(discount.getMaximumDiscount()) > 0) {
            amount = discount.getMaximumDiscount();
        }
        if (amount.compareTo(subtotal) > 0) {
            amount = subtotal;
        }
        return new AppliedDiscount(discount, amount.setScale(2, RoundingMode.HALF_UP));
    }

    @Transactional
    public Discount create(DiscountDTO dto) {
        validateValue(dto);
        String normalized = dto.getCode().trim().toUpperCase(java.util.Locale.ROOT);
        if (discountRepository.existsByCodeNormalized(normalized) || discountRepository.existsByCode(normalized)) {
            throw new IllegalArgumentException("This discount code already exists.");
        }
        return discountRepository.save(fromDto(new Discount(), dto));
    }

    @Transactional
    public Discount update(String id, DiscountDTO dto) {
        validateValue(dto);
        Discount discount = discountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Discount was not found."));
        String normalized = dto.getCode().trim().toUpperCase(java.util.Locale.ROOT);
        discountRepository.findByCodeNormalized(normalized)
                .or(() -> discountRepository.findByCode(normalized))
                .ifPresent(existing -> {
                    if (!existing.getId().equals(id)) {
                        throw new IllegalArgumentException("This discount code already exists.");
                    }
                });
        return discountRepository.save(fromDto(discount, dto));
    }

    @Transactional
    public void toggle(String id) {
        Discount discount = discountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Discount was not found."));
        discount.setActive(!discount.isActive());
        discountRepository.save(discount);
    }

    public DiscountDTO toDto(Discount discount) {
        return DiscountDTO.builder()
                .code(discount.getCode())
                .name(discount.getName())
                .description(discount.getDescription())
                .type(discount.getType())
                .value(discount.getValue())
                .minimumOrder(discount.getMinimumOrder())
                .maximumDiscount(discount.getMaximumDiscount())
                .startsAt(discount.getStartsAt())
                .endsAt(discount.getEndsAt())
                .active(discount.isActive())
                .build();
    }


    private void validateValue(DiscountDTO dto) {
        if (dto.getType() == DiscountType.PERCENTAGE && dto.getValue() != null
                && dto.getValue().compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Percentage discounts cannot exceed 100%.");
        }
    }

    private Discount fromDto(Discount discount, DiscountDTO dto) {
        discount.setCode(dto.getCode().trim().toUpperCase(java.util.Locale.ROOT));
        discount.setCodeNormalized(discount.getCode());
        discount.setName(dto.getName().trim());
        discount.setDescription(dto.getDescription().trim());
        discount.setType(dto.getType());
        discount.setValue(dto.getValue());
        discount.setMinimumOrder(dto.getMinimumOrder());
        discount.setMaximumDiscount(dto.getMaximumDiscount());
        discount.setStartsAt(dto.getStartsAt());
        discount.setEndsAt(dto.getEndsAt());
        discount.setActive(dto.isActive());
        return discount;
    }

    /** Allows checkout to report promo errors beside the promo field, not under order notes. */
    public static class InvalidDiscountException extends IllegalArgumentException {
        public InvalidDiscountException(String message) {
            super(message);
        }
    }

    public record AppliedDiscount(Discount discount, BigDecimal amount) {}
}
