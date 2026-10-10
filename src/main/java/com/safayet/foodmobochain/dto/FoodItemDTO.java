package com.safayet.foodmobochain.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodItemDTO {
    @NotBlank(message = "Food name is required")
    @Size(min = 2, max = 120)
    private String name;

    @NotBlank(message = "Choose a category")
    private String categoryId;

    @NotBlank(message = "Description is required")
    @Size(min = 10, max = 1200)
    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "1.00", message = "Price must be at least ৳1")
    @DecimalMax(value = "100000.00")
    private BigDecimal price;

    // A new image may arrive as a multipart file in the same POST; validated after upload.
    @Size(max = 512)
    private String image;

    private boolean available = true;
    private boolean featured;
    private boolean spicySupported;
}
