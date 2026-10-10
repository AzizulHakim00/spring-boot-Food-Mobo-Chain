package com.safayet.foodmobochain.service;

import com.safayet.foodmobochain.model.Discount;
import com.safayet.foodmobochain.dto.DiscountDTO;
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
import static org.mockito.Mockito.verify;

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
    @Test
    void legacyFmc100CodeIsRecognizedWithoutNormalizedMongoField() {
        Discount discount = Discount.builder()
                .id("discounts:2")
                .code("FMC100")
                .codeNormalized(null)
                .name("Food Mobo 100")
                .type(DiscountType.FIXED_AMOUNT)
                .value(new BigDecimal("100.00"))
                .minimumOrder(new BigDecimal("700.00"))
                .startsAt(LocalDateTime.now(com.safayet.foodmobochain.config.MongoConfig.APP_ZONE).minusDays(1))
                .endsAt(LocalDateTime.now(com.safayet.foodmobochain.config.MongoConfig.APP_ZONE).plusDays(3))
                .active(true).build();
        when(repository.findByCodeNormalized("FMC100")).thenReturn(Optional.empty());
        when(repository.findByCode("FMC100")).thenReturn(Optional.of(discount));

        assertThat(service.calculate(" fmc100 ", new BigDecimal("820.00")).amount())
                .isEqualByComparingTo("100.00");
        verify(repository).findByCode("FMC100");
        assertThatThrownBy(() -> service.calculate("FMC100", new BigDecimal("699.99")))
                .isInstanceOf(DiscountService.InvalidDiscountException.class)
                .hasMessageContaining("Minimum order");
    }

    @Test
    void percentageDiscountUsesRealDhakaTimeAndNormalizesMixedCaseCode() {
        Discount discount = Discount.builder()
                .code("WELCOME15").name("Welcome Offer")
                .type(DiscountType.PERCENTAGE)
                .value(new BigDecimal("15.00"))
                .minimumOrder(new BigDecimal("300.00"))
                .maximumDiscount(new BigDecimal("150.00"))
                .startsAt(LocalDateTime.now(com.safayet.foodmobochain.config.MongoConfig.APP_ZONE).minusDays(1))
                .endsAt(LocalDateTime.now(com.safayet.foodmobochain.config.MongoConfig.APP_ZONE).plusDays(3))
                .active(true).build();
        when(repository.findByCodeNormalized("WELCOME15")).thenReturn(Optional.of(discount));

        assertThat(service.calculate("Welcome15", new BigDecimal("820.00")).amount())
                .isEqualByComparingTo("123.00");
    }

    @Test
    void expiredPromoMustNotDiscountAnything() {
        Discount expired = Discount.builder()
                .code("OLD").name("Old Deal").type(DiscountType.FIXED_AMOUNT)
                .value(new BigDecimal("100.00")).minimumOrder(BigDecimal.ZERO)
                .startsAt(LocalDateTime.now(com.safayet.foodmobochain.config.MongoConfig.APP_ZONE).minusDays(6))
                .endsAt(LocalDateTime.now(com.safayet.foodmobochain.config.MongoConfig.APP_ZONE).minusDays(2))
                .active(true).build();
        when(repository.findByCodeNormalized("OLD")).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> service.calculate("OLD", new BigDecimal("820.00")))
                .isInstanceOf(DiscountService.InvalidDiscountException.class)
                .hasMessageContaining("not currently active");
    }

    @Test
    void adminCannotCreateDuplicateCodeOverLegacySeed() {
        when(repository.existsByCodeNormalized("FMC100")).thenReturn(false);
        when(repository.existsByCode("FMC100")).thenReturn(true);
        DiscountDTO candidate = DiscountDTO.builder()
                .code("fmc100").name("Duplicate")
                .description("Duplicate promo in legacy staging seed")
                .type(DiscountType.FIXED_AMOUNT).value(new BigDecimal("50.00"))
                .minimumOrder(BigDecimal.ZERO)
                .startsAt(LocalDateTime.now(com.safayet.foodmobochain.config.MongoConfig.APP_ZONE))
                .endsAt(LocalDateTime.now(com.safayet.foodmobochain.config.MongoConfig.APP_ZONE).plusDays(1))
                .active(true).build();
        assertThatThrownBy(() -> service.create(candidate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

}
