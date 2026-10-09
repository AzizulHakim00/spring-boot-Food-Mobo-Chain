package com.safayet.foodmobochain.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDTO {
    private String foodItemId;
    private String foodCartId;

    @Min(value = 1, message = "Choose a rating")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    private int rating;

    @NotBlank(message = "Write a short review")
    @Size(min = 3, max = 1000)
    private String comment;

    @AssertTrue(message = "Choose a food item or food cart to review")
    public boolean isTargetValid() {
        return (foodItemId != null) ^ (foodCartId != null);
    }
}
