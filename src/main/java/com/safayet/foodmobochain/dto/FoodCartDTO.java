package com.safayet.foodmobochain.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodCartDTO {
    @NotBlank(message = "Food-cart name is required")
    @Size(min = 3, max = 120)
    private String name;

    @NotBlank(message = "Description is required")
    @Size(min = 20, max = 1000)
    private String description;

    @NotBlank(message = "Location is required")
    @Size(max = 180)
    private String location;

    @NotBlank(message = "Cuisine is required")
    @Size(max = 100)
    private String cuisine;

    @NotBlank(message = "Cover image path is required")
    @Size(max = 512)
    private String coverImage;

    @NotNull(message = "Delivery fee is required")
    @DecimalMin(value = "0.00")
    @DecimalMax(value = "1000.00")
    private BigDecimal deliveryFee;

    @Min(10)
    @Max(180)
    private int estimatedDeliveryMinutes;
}
