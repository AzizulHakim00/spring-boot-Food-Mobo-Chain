package com.safayet.foodmobochain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileDTO {
    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100)
    private String fullName;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^(?:\\+?88)?01[3-9]\\d{8}$", message = "Enter a valid Bangladeshi mobile number")
    private String phone;

    @Size(max = 300, message = "Address is too long")
    private String address;
}
