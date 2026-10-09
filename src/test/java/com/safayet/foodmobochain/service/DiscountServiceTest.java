package com.safayet.foodmobochain.service;

import com.safayet.foodmobochain.model.Discount;
import com.safayet.foodmobochain.model.enums.DiscountType;
import com.safayet.foodmobochain.repository.DiscountRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiscountServiceTest {

    private final DiscountRepository repository = mock(DiscountRepository.class);
    private final DiscountService service = new DiscountService(repository);

    @Test
    void percentageDiscountHonorsMaximumCap() {
        Discount discount = Discount.builder()
                .code("SAVE20")
                .name("Save 20")
                .description("Test")
                .type(DiscountType.PERCENTAGE)
                .value(new BigDecimal("20"))
                .minimumOrder(new BigDecimal("100"))
                .maximumDiscount(new BigDecimal("150"))
                .startsAt(LocalDateTime.now().minusDays(1))
                .endsAt(LocalDateTime.now().plusDays(1))
                .active(true)
                .build();
        when(repository.findByCodeNormalized("SAVE20")).thenReturn(Optional.of(discount));

        assertThat(service.calculate("SAVE20", new BigDecimal("1000")).amount())
                .isEqualByComparingTo("150");
    }

    @Test
    void invalidCodeIsRejected() {
        when(repository.findByCodeNormalized("NOPE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.calculate("NOPE", new BigDecimal("500")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
