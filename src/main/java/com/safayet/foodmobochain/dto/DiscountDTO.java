package com.safayet.foodmobochain.dto;

import com.safayet.foodmobochain.model.enums.DiscountType;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscountDTO {
    @NotBlank(message = "Discount code is required")
    @Pattern(regexp = "^[A-Za-z0-9_-]{3,40}$", message = "Use letters, numbers, hyphen or underscore")
    private String code;

    @NotBlank(message = "Name is required")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "Description is required")
    @Size(max = 500)
    private String description;

    @NotNull
    private DiscountType type;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal value;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal minimumOrder;

    @DecimalMin("0.00")
    private BigDecimal maximumDiscount;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startsAt;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endsAt;

    private boolean active = true;

    @AssertTrue(message = "End date must be after start date")
    public boolean isDateRangeValid() {
        return startsAt == null || endsAt == null || endsAt.isAfter(startsAt);
    }
}
