package com.azizul.foodmobochain.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ChangePasswordDTO {
    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
    @Pattern(regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d).+$",
            message = "Use uppercase, lowercase and a number")
    private String newPassword;

    @NotBlank(message = "Confirm your new password")
    private String confirmPassword;

    @AssertTrue(message = "New passwords do not match")
    public boolean isPasswordMatching() {
        return newPassword != null && newPassword.equals(confirmPassword);
    }
}
