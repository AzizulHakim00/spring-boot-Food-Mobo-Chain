package com.safayet.foodmobochain.service;

import com.safayet.foodmobochain.dto.ProfileDTO;
import com.safayet.foodmobochain.dto.RegistrationDTO;
import com.safayet.foodmobochain.dto.SellerRegistrationDTO;
import com.safayet.foodmobochain.model.FoodCart;
import com.safayet.foodmobochain.model.User;
import com.safayet.foodmobochain.model.enums.Role;
import com.safayet.foodmobochain.repository.FoodCartRepository;
import com.safayet.foodmobochain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final FoodCartRepository foodCartRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User registerBuyer(RegistrationDTO dto) {
        String email = normalizeEmail(dto.getEmail());
        ensureEmailAvailable(email);

        return userRepository.save(User.builder()
                .fullName(dto.getFullName().trim())
                .email(email).emailNormalized(email)
                .phone(dto.getPhone().trim())
                .password(passwordEncoder.encode(dto.getPassword()))
                .role(Role.BUYER)
                .enabled(true)
                .build());
    }

    @Transactional
    public User registerSeller(SellerRegistrationDTO dto) {
        String email = normalizeEmail(dto.getEmail());
        ensureEmailAvailable(email);

        User seller = userRepository.save(User.builder()
                .fullName(dto.getFullName().trim())
                .email(email).emailNormalized(email)
                .phone(dto.getPhone().trim())
                .password(passwordEncoder.encode(dto.getPassword()))
                .role(Role.SELLER)
                .enabled(true)
                .build());

        foodCartRepository.save(FoodCart.builder()
                .owner(seller).ownerId(seller.getId())
                .name(dto.getCartName().trim())
                .slug(uniqueCartSlug(dto.getCartName()))
                .description(dto.getDescription().trim())
                .location(dto.getLocation().trim())
                .cuisine(dto.getCuisine().trim())
                .coverImage("/images/carts/cart-default.webp")
                .deliveryFee(dto.getDeliveryFee())
                .estimatedDeliveryMinutes(dto.getEstimatedDeliveryMinutes())
                .open(false)
                .approved(false)
                .build());

        return seller;
    }

    public User getByEmail(String email) {
        return userRepository.findByEmailNormalized(normalizeEmail(email))
                .orElseThrow(() -> new IllegalArgumentException("User account was not found."));
    }

    public User getById(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User account was not found."));
    }

    public User getBuyerByEmail(String email) {
        User user = getByEmail(email);
        if (user.getRole() != Role.BUYER) {
            throw new SecurityException("This action is available to buyer accounts only.");
        }
        return user;
    }

    public ProfileDTO getProfile(User user) {
        return ProfileDTO.builder()
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .address(user.getAddress())
                .build();
    }

    @Transactional
    public void updateProfile(User user, ProfileDTO dto) {
        user.setFullName(dto.getFullName().trim());
        user.setPhone(dto.getPhone().trim());
        user.setAddress(dto.getAddress() == null ? null : dto.getAddress().trim());
        userRepository.save(user);
    }

    public List<User> findAll() {
        return userRepository.findAllByOrderByCreatedAtDesc();
    }

    public long countAll() {
        return userRepository.count();
    }

    @Caching(evict = {
            @CacheEvict(value = "publicCarts", allEntries = true),
            @CacheEvict(value = "featuredFoods", allEntries = true)
    })
    @Transactional
    public void toggleEnabled(String id) {
        User user = getById(id);
        if (user.getRole() == Role.ADMIN) {
            throw new IllegalArgumentException("Administrator accounts cannot be suspended here.");
        }
        user.setEnabled(!user.isEnabled());
        userRepository.save(user);

        if (user.getRole() == Role.SELLER && !user.isEnabled()) {
            foodCartRepository.findByOwnerId(user.getId()).ifPresent(cart -> {
                cart.setOpen(false);
                foodCartRepository.save(cart);
            });
        }
    }

    @Transactional
    public void changePassword(User user, String rawPassword) {
        user.setPassword(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
    }

    private void ensureEmailAvailable(String email) {
        if (userRepository.existsByEmailNormalized(email)) {
            throw new IllegalArgumentException("An account already exists with this email.");
        }
    }

    private String uniqueCartSlug(String value) {
        String base = CatalogService.slugify(value);
        String candidate = base;
        int counter = 2;
        while (foodCartRepository.existsBySlug(candidate)) {
            candidate = base + "-" + counter++;
        }
        return candidate;
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
