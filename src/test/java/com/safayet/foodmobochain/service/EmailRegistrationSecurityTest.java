package com.safayet.foodmobochain.service;

import com.safayet.foodmobochain.dto.*;
import com.safayet.foodmobochain.model.*;
import com.safayet.foodmobochain.model.enums.Role;
import com.safayet.foodmobochain.repository.*;
import com.safayet.foodmobochain.security.AccountUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailRegistrationSecurityTest {
    private final UserRepository users = mock(UserRepository.class);
    private final FoodCartRepository carts = mock(FoodCartRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final UserService service = new UserService(users, carts, encoder);

    @Test void buyerHasNoLoginAccessUntilVerified() {
        when(encoder.encode(anyString())).thenReturn("encoded-password");
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));
        RegistrationDTO dto = RegistrationDTO.builder().fullName("New Buyer").email("NEW@EXAMPLE.TEST")
                .phone("01712345678").password("StrongPassword123").build();
        User pending = service.registerBuyer(dto);
        assertThat(pending.getEmail()).isEqualTo("new@example.test");
        assertThat(pending.getEmailVerified()).isFalse();
        assertThat(new AccountUserDetails(pending).isEnabled()).isFalse();
        pending.setEmailVerified(true);
        assertThat(new AccountUserDetails(pending).isEnabled()).isTrue();
    }

    @Test void sellerMustVerifyAndStillNeedsCartApproval() {
        when(encoder.encode(anyString())).thenReturn("encoded-password");
        when(users.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("users:new-seller");
            return u;
        });
        SellerRegistrationDTO dto = SellerRegistrationDTO.builder().fullName("New Seller")
                .email("seller@example.test").phone("01712345678")
                .password("StrongPassword123").cartName("Test Food Cart")
                .cuisine("Bangladeshi").location("Dhaka")
                .description("Fresh food from a new seller")
                .deliveryFee(BigDecimal.valueOf(20)).estimatedDeliveryMinutes(30).build();
        User seller = service.registerSeller(dto);
        assertThat(seller.getRole()).isEqualTo(Role.SELLER);
        assertThat(seller.getEmailVerified()).isFalse();
        assertThat(new AccountUserDetails(seller).isEnabled()).isFalse();
        var cap = org.mockito.ArgumentCaptor.forClass(FoodCart.class);
        verify(carts).save(cap.capture());
        assertThat(cap.getValue().isApproved()).isFalse();
        assertThat(cap.getValue().isOpen()).isFalse();
    }

    @Test void existingNullVerificationFlagRemainsEnabled() {
        User existing = User.builder().email("legacy@example.test")
                .password("existing-hash").enabled(true).build();
        assertThat(existing.getEmailVerified()).isNull();
        assertThat(new AccountUserDetails(existing).isEnabled()).isTrue();
    }
    @Test void reservedLocalDemoEmailCannotBeUsedForNewRegistration() {
        RegistrationDTO dto = RegistrationDTO.builder()
                .fullName("Someone").email("fake@foodmobo.local").phone("01712345678")
                .password("StrongPassword123").confirmPassword("StrongPassword123").build();
        assertThatThrownBy(() -> service.registerBuyer(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("receive verification");
        verifyNoInteractions(users, carts);
    }

}
