package com.safayet.foodmobochain.controller;

import com.safayet.foodmobochain.dto.FoodCartDTO;
import com.safayet.foodmobochain.dto.FoodItemDTO;
import com.safayet.foodmobochain.model.FoodCart;
import com.safayet.foodmobochain.model.User;
import com.safayet.foodmobochain.service.CatalogService;
import com.safayet.foodmobochain.service.CloudinaryImageService;
import com.safayet.foodmobochain.service.OrderService;
import com.safayet.foodmobochain.service.ReportService;
import com.safayet.foodmobochain.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SellerMultipartImageTest {
    @Mock private UserService userService;
    @Mock private CatalogService catalogService;
    @Mock private CloudinaryImageService imageService;
    @Mock private OrderService orderService;
    @Mock private ReportService reportService;
    @InjectMocks private SellerController controller;

    private final User seller = User.builder().id("users:3").email("seller1@foodmobo.local").build();
    private final Authentication auth = UsernamePasswordAuthenticationToken.authenticated(
            "seller1@foodmobo.local", null, List.of(new SimpleGrantedAuthority("ROLE_SELLER")));
    private static final String IMAGE_URL =
            "https://res.cloudinary.com/demo/image/upload/v123/food-mobo-chain/foods/new.png";
    private static final String COVER_URL =
            "https://res.cloudinary.com/demo/image/upload/v123/food-mobo-chain/carts/new.png";

    @Test
    void newMenuFoodUploadsAndSavesInOneControllerRequest() {
        when(userService.getByEmail("seller1@foodmobo.local")).thenReturn(seller);
        MockMultipartFile file = image();
        when(imageService.upload(file, "foods")).thenReturn(IMAGE_URL);
        FoodItemDTO dto = foodDto(null);
        BindingResult errors = new BeanPropertyBindingResult(dto, "foodForm");

        String page = controller.createFood(auth, dto, errors, file,
                new ExtendedModelMap(), new RedirectAttributesModelMap());

        assertEquals("redirect:/seller/menu", page);
        assertFalse(errors.hasErrors());
        assertEquals(IMAGE_URL, dto.getImage());
        verify(catalogService).createFood(seller, dto);
    }

    @Test
    void cartCoverImageUploadsAndIsPersistedInSameRequest() {
        when(userService.getByEmail("seller1@foodmobo.local")).thenReturn(seller);
        MockMultipartFile file = image();
        when(imageService.upload(file, "carts")).thenReturn(COVER_URL);
        FoodCartDTO dto = FoodCartDTO.builder().name("Dhaka Biryani House")
                .description("Original description with plenty of details for customers.")
                .location("Dhaka").cuisine("Bangladeshi")
                .coverImage("/images/carts/old.webp").deliveryFee(new BigDecimal("30.00"))
                .estimatedDeliveryMinutes(35).build();
        BindingResult errors = new BeanPropertyBindingResult(dto, "foodCartForm");

        String page = controller.updateFoodCart(auth, dto, errors, file,
                new ExtendedModelMap(), new RedirectAttributesModelMap());

        assertEquals("redirect:/seller/food-cart", page);
        assertFalse(errors.hasErrors());
        assertEquals(COVER_URL, dto.getCoverImage());
        verify(catalogService).updateSellerCart(seller, dto);
    }

    @Test
    void cloudinaryFailureDisplaysFormErrorWithoutCreatingFood() {
        MockMultipartFile file = image();
        when(imageService.upload(file, "foods")).thenThrow(new IllegalStateException("Image hosting unavailable"));
        FoodItemDTO dto = foodDto(null);
        BindingResult errors = new BeanPropertyBindingResult(dto, "foodForm");

        String page = controller.createFood(auth, dto, errors, file,
                new ExtendedModelMap(), new RedirectAttributesModelMap());

        assertEquals("seller/food-form", page);
        assertTrue(errors.hasFieldErrors("image"));
        verify(catalogService, never()).createFood(any(), any());
    }

    @Test
    void foodWithoutAnyImageDisplaysFieldErrorRatherThanSaving() {
        FoodItemDTO dto = foodDto(null);
        BindingResult errors = new BeanPropertyBindingResult(dto, "foodForm");

        String page = controller.createFood(auth, dto, errors, null,
                new ExtendedModelMap(), new RedirectAttributesModelMap());

        assertEquals("seller/food-form", page);
        assertTrue(errors.hasFieldErrors("image"));
        verify(catalogService, never()).createFood(any(), any());
    }

    @Test
    void editingMenuItemRetainsExistingImageWithoutAFile() {
        when(userService.getByEmail("seller1@foodmobo.local")).thenReturn(seller);
        FoodItemDTO dto = foodDto("/images/foods/beef-rice.webp");
        BindingResult errors = new BeanPropertyBindingResult(dto, "foodForm");

        String page = controller.updateFood(auth, "foodItems:1", dto, errors, null,
                new ExtendedModelMap(), new RedirectAttributesModelMap());

        assertEquals("redirect:/seller/menu", page);
        assertFalse(errors.hasErrors());
        verifyNoInteractions(imageService);
        verify(catalogService).updateFood(seller, "foodItems:1", dto);
    }

    private static MockMultipartFile image() {
        return new MockMultipartFile("imageFile", "new.png", "image/png",
                new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
    }

    private static FoodItemDTO foodDto(String image) {
        return FoodItemDTO.builder().name("Demo Food").categoryId("categories:1")
                .description("Freshly cooked food from the demo seller.")
                .price(new BigDecimal("150.00")).image(image).available(true).build();
    }
}
