package com.safayet.foodmobochain.dto;

import com.safayet.foodmobochain.model.enums.PaymentMethod;
import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutDTO {
    @NotBlank(message = "Delivery address is required")
    @Size(min = 8, max = 300)
    private String deliveryAddress;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^(?:\\+?88)?01[3-9]\\d{8}$", message = "Enter a valid Bangladeshi mobile number")
    private String phone;

    @Size(max = 500)
    private String note;

    @Size(max = 40)
    private String discountCode;

    @NotNull(message = "Choose a payment method")
    private PaymentMethod paymentMethod;
}
