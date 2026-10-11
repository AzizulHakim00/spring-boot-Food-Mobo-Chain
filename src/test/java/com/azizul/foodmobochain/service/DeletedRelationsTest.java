package com.azizul.foodmobochain.service;

import com.azizul.foodmobochain.model.*;
import com.azizul.foodmobochain.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeletedRelationsTest {
    @Mock private FoodCartRepository carts;
    @Mock private FoodItemRepository foods;
    @Mock private CategoryRepository categories;
    @Mock private ReviewRepository reviews;
    @Mock private NotificationService notifications;
    @Mock private Relations relations;
    @Mock private MongoTemplate mongoTemplate;
    @Mock private ImageUrlPolicy images;
    @InjectMocks private CatalogService catalog;

    @Test
    void deletedCategoryDoesNotCrashPublicFoodCartMenu() {
        FoodCart vendor = FoodCart.builder().id("foodCarts:1").build();
        FoodItem broken = FoodItem.builder().id("foodItems:deleted-category").name("Old item").build();
        FoodItem valid = FoodItem.builder().id("foodItems:2").name("Healthy item").available(true).build();
        valid.setCategory(Category.builder().id("categories:1").active(true).build());
        List<FoodItem> found = List.of(broken, valid);
        when(foods.findByFoodCartIdAndArchivedFalseOrderByNameAsc(vendor.getId())).thenReturn(found);
        when(relations.foods(found)).thenReturn(found);

        assertThat(catalog.menu(vendor)).containsExactly(valid);
    }

    @Test
    void missingSellerCannotBeFavoritedAndReturnsFriendlyError() {
        FavoriteFoodRepository favoriteFoods = mock(FavoriteFoodRepository.class);
        FavoriteCartRepository favoriteCarts = mock(FavoriteCartRepository.class);
        FavoriteService favorites = new FavoriteService(
                favoriteFoods, favoriteCarts, foods, carts, relations);
        User buyer = User.builder().id("users:buyer").build();
        FoodCart orphan = FoodCart.builder().id("foodCarts:orphan").approved(true).build();
        when(carts.findById(orphan.getId())).thenReturn(Optional.of(orphan));
        when(relations.cart(orphan)).thenReturn(orphan);

        assertThatThrownBy(() -> favorites.toggleCart(buyer, orphan.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not available");
        verifyNoInteractions(favoriteCarts);
    }
}
