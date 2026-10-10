package com.safayet.foodmobochain.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerRegistrationDTO {
    @NotBlank(message = "Owner name is required")
    @Size(min = 2, max = 100)
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    private String email;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^(?:\\+?88)?01[3-9]\\d{8}$", message = "Enter a valid Bangladeshi mobile number")
    private String phone;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72)
    @Pattern(regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d).+$", message = "Use uppercase, lowercase and a number")
    private String password;

    @NotBlank(message = "Confirm your password")
    private String confirmPassword;

    @NotBlank(message = "Food-cart name is required")
    @Size(min = 3, max = 120)
    private String cartName;

    @NotBlank(message = "Location is required")
    @Size(max = 180)
    private String location;

    @NotBlank(message = "Cuisine is required")
    @Size(max = 100)
    private String cuisine;

    @NotBlank(message = "Tell customers about your food cart")
    @Size(min = 20, max = 1000)
    private String description;

    @NotNull(message = "Delivery fee is required")
    @DecimalMin(value = "0.00", message = "Delivery fee cannot be negative")
    @DecimalMax(value = "1000.00", message = "Delivery fee is too high")
    private BigDecimal deliveryFee;

    @Min(value = 10, message = "Minimum delivery time is 10 minutes")
    @Max(value = 180, message = "Maximum delivery time is 180 minutes")
    private int estimatedDeliveryMinutes = 35;

    @AssertTrue(message = "Passwords do not match")
    public boolean isPasswordMatching() {
        return password != null && password.equals(confirmPassword);
    }
}
