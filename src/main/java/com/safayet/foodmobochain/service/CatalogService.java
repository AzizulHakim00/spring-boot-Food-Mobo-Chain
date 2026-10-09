package com.safayet.foodmobochain.service;

import com.safayet.foodmobochain.dto.FoodCartDTO;
import com.safayet.foodmobochain.dto.FoodItemDTO;
import com.safayet.foodmobochain.model.Category;
import com.safayet.foodmobochain.model.FoodCart;
import com.safayet.foodmobochain.model.FoodItem;
import com.safayet.foodmobochain.model.Review;
import com.safayet.foodmobochain.model.User;
import com.safayet.foodmobochain.model.enums.NotificationType;
import com.safayet.foodmobochain.repository.CategoryRepository;
import com.safayet.foodmobochain.repository.FoodCartRepository;
import com.safayet.foodmobochain.repository.FoodItemRepository;
import com.safayet.foodmobochain.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import java.util.ArrayList;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final FoodCartRepository foodCartRepository;
    private final FoodItemRepository foodItemRepository;
    private final CategoryRepository categoryRepository;
    private final ReviewRepository reviewRepository;
    private final NotificationService notificationService;
    private final Relations relations;
    private final MongoTemplate mongoTemplate;
    private final ImageUrlPolicy imageUrlPolicy;

    // ---------- Public catalog ----------

    @Cacheable("publicCarts")
    public List<FoodCart> carts() {
        return relations.carts(foodCartRepository.findByApprovedTrueOrderByNameAsc()).stream()
                .filter(c -> c.getOwner() != null && c.getOwner().isEnabled()).toList();
    }

    public FoodCart cart(String slug) {
        FoodCart cart = foodCartRepository.findBySlug(slug).map(relations::cart)
                .orElseThrow(() -> new IllegalArgumentException("Food cart was not found."));
        if (!cart.isApproved() || cart.getOwner() == null || !cart.getOwner().isEnabled()) {
            throw new IllegalArgumentException("Food cart was not found.");
        }
        return cart;
    }

    public List<FoodItem> menu(FoodCart cart) {
        return relations.foods(foodItemRepository.findByFoodCartIdAndArchivedFalseOrderByNameAsc(cart.getId())).stream()
                .filter(FoodItem::isAvailable)
                .filter(food -> food.getCategory().isActive())
                .toList();
    }

    public Page<FoodItem> searchFoods(String query, String category, String cartId,
                                      BigDecimal minPrice, BigDecimal maxPrice,
                                      String sort, int page, int size) {
        // Two-stage access policy: only enabled/approved sellers and active categories may be queried.
        List<FoodCart> allowedCarts = carts().stream().filter(FoodCart::isOpen).toList();
        List<String> visibleCartIds = allowedCarts.stream().map(FoodCart::getId).toList();
        List<String> activeCategoryIds = categories().stream().map(Category::getId).toList();
        Sort sorting = switch (sort == null ? "popular" : sort) {
            case "priceLow", "price-low" -> Sort.by(Sort.Direction.ASC, "price");
            case "priceHigh", "price-high" -> Sort.by(Sort.Direction.DESC, "price");
            case "newest" -> Sort.by(Sort.Direction.DESC, "createdAt");
            default -> Sort.by(Sort.Order.desc("featured"), Sort.Order.desc("createdAt"));
        };
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(48, Math.max(1, size)), sorting);
        if (visibleCartIds.isEmpty() || activeCategoryIds.isEmpty()) return Page.empty(pageable);
        Category chosen = null;
        if (category != null && !category.isBlank()) {
            chosen = categoryRepository.findBySlug(category).orElse(null);
            if (chosen == null || !chosen.isActive()) return Page.empty(pageable);
        }
        if (cartId != null && !visibleCartIds.contains(cartId)) return Page.empty(pageable);
        Criteria restrictions = Criteria.where("archived").is(false).and("available").is(true)
                .and("foodCartId").in(cartId == null ? visibleCartIds : List.of(cartId));
        if (chosen != null) restrictions.and("categoryId").is(chosen.getId());
        else restrictions.and("categoryId").in(activeCategoryIds);
        if (minPrice != null || maxPrice != null) {
            Criteria priceCriterion = restrictions.and("price");
            if (minPrice != null) priceCriterion.gte(minPrice);
            if (maxPrice != null) priceCriterion.lte(maxPrice);
        }
        String phrase = blankToNull(query);
        if (phrase != null) {
            Pattern regex = Pattern.compile(Pattern.quote(phrase), Pattern.CASE_INSENSITIVE);
            List<String> matchingCarts = allowedCarts.stream()
                    .filter(c -> regex.matcher(c.getName()).find()).map(FoodCart::getId).toList();
            restrictions = new Criteria().andOperator(restrictions,
                    new Criteria().orOperator(Criteria.where("name").regex(regex),
                            Criteria.where("description").regex(regex), Criteria.where("foodCartId").in(matchingCarts)));
        }
        Query mongoQuery = new Query(restrictions);
        long total = mongoTemplate.count(mongoQuery, FoodItem.class);
        mongoQuery.with(pageable);
        List<FoodItem> rows = relations.foods(mongoTemplate.find(mongoQuery, FoodItem.class));
        return new PageImpl<>(rows, pageable, total);
    }

    public FoodItem food(String id) {
        FoodItem food = foodItemRepository.findById(id).map(relations::food)
                .orElseThrow(() -> new IllegalArgumentException("Food item was not found."));
        if (food.isArchived() || !food.isAvailable() || food.getCategory() == null || !food.getCategory().isActive() || food.getFoodCart() == null || !food.getFoodCart().isApproved() || food.getFoodCart().getOwner() == null || !food.getFoodCart().getOwner().isEnabled()) {
            throw new IllegalArgumentException("Food item was not found.");
        }
        return food;
    }

    @Cacheable("featuredFoods")
    public List<FoodItem> featuredFoods() {
        return relations.foods(foodItemRepository.findByFeaturedTrueAndAvailableTrueAndArchivedFalseOrderByCreatedAtDesc()).stream()
                .filter(CartService::orderable).limit(8).toList();
    }

    @Cacheable("categories")
    public List<Category> categories() {
        return categoryRepository.findByActiveTrueOrderByNameAsc();
    }

    public List<Review> foodReviews(FoodItem food) {
        return relations.reviews(reviewRepository.findByFoodItemIdAndApprovedTrueAndHiddenFalseOrderByCreatedAtDesc(food.getId()));
    }

    public List<Review> cartReviews(FoodCart cart) {
        return relations.reviews(reviewRepository.findByFoodCartIdAndApprovedTrueAndHiddenFalseOrderByCreatedAtDesc(cart.getId()));
    }

    public double foodRating(FoodItem food) {
        return roundRating(average(reviewRepository.findByFoodItemIdAndApprovedTrueAndHiddenFalseOrderByCreatedAtDesc(food.getId())));
    }

    public double cartRating(FoodCart cart) {
        return roundRating(average(reviewRepository.findByFoodCartIdAndApprovedTrueAndHiddenFalseOrderByCreatedAtDesc(cart.getId())));
    }

    // ---------- Seller catalog ----------

    public FoodCart sellerCart(User seller) {
        return foodCartRepository.findByOwnerId(seller.getId()).map(relations::cart)
                .orElseThrow(() -> new IllegalArgumentException("Seller food cart was not found."));
    }

    public List<FoodItem> sellerMenu(User seller) {
        return relations.foods(foodItemRepository.findByFoodCartIdAndArchivedFalseOrderByNameAsc(sellerCart(seller).getId()));
    }

    public FoodCartDTO sellerCartDto(User seller) {
        FoodCart cart = sellerCart(seller);
        return FoodCartDTO.builder()
                .name(cart.getName())
                .description(cart.getDescription())
                .location(cart.getLocation())
                .cuisine(cart.getCuisine())
                .coverImage(cart.getCoverImage())
                .deliveryFee(cart.getDeliveryFee())
                .estimatedDeliveryMinutes(cart.getEstimatedDeliveryMinutes())
                .build();
    }

    @Caching(evict = {
            @CacheEvict(value = "publicCarts", allEntries = true),
            @CacheEvict(value = "featuredFoods", allEntries = true),
            @CacheEvict(value = "categories", allEntries = true)
    })
    @Transactional
    public void updateSellerCart(User seller, FoodCartDTO dto) {
        FoodCart cart = sellerCart(seller);
        if (!cart.getName().equalsIgnoreCase(dto.getName().trim())) {
            cart.setSlug(uniqueCartSlug(dto.getName()));
        }
        cart.setName(dto.getName().trim());
        cart.setDescription(dto.getDescription().trim());
        cart.setLocation(dto.getLocation().trim());
        cart.setCuisine(dto.getCuisine().trim());
        imageUrlPolicy.requireTrusted(dto.getCoverImage());
        cart.setCoverImage(dto.getCoverImage().trim());
        cart.setDeliveryFee(dto.getDeliveryFee());
        cart.setEstimatedDeliveryMinutes(dto.getEstimatedDeliveryMinutes());
        foodCartRepository.save(cart);
    }

    @Caching(evict = {
            @CacheEvict(value = "publicCarts", allEntries = true),
            @CacheEvict(value = "featuredFoods", allEntries = true),
            @CacheEvict(value = "categories", allEntries = true)
    })
    @Transactional
    public void toggleSellerCartOpen(User seller) {
        FoodCart cart = sellerCart(seller);
        if (!cart.isApproved() || cart.getOwner() == null || !cart.getOwner().isEnabled()) {
            throw new IllegalArgumentException("Your food cart must be approved by an administrator before it can open.");
        }
        cart.setOpen(!cart.isOpen());
        foodCartRepository.save(cart);
    }

    @Caching(evict = {
            @CacheEvict(value = "publicCarts", allEntries = true),
            @CacheEvict(value = "featuredFoods", allEntries = true),
            @CacheEvict(value = "categories", allEntries = true)
    })
    @Transactional
    public FoodItem createFood(User seller, FoodItemDTO dto) {
        FoodCart cart = sellerCart(seller);
        Category category = activeCategory(dto.getCategoryId());
        imageUrlPolicy.requireTrusted(dto.getImage());
        return foodItemRepository.save(FoodItem.builder()
                .foodCart(cart).foodCartId(cart.getId())
                .category(category).categoryId(category.getId())
                .name(dto.getName().trim())
                .slug(slugify(dto.getName()))
                .description(dto.getDescription().trim())
                .price(dto.getPrice())
                .image(dto.getImage().trim())
                .available(dto.isAvailable())
                .featured(dto.isFeatured())
                .spicySupported(dto.isSpicySupported())
                .build());
    }

    public FoodItem sellerFood(User seller, String foodId) {
        FoodItem food = foodItemRepository.findById(foodId).map(relations::food)
                .orElseThrow(() -> new IllegalArgumentException("Food item was not found."));
        if (!food.getFoodCart().getOwner().getId().equals(seller.getId())) {
            throw new SecurityException("You cannot manage this food item.");
        }
        return food;
    }

    public FoodItemDTO sellerFoodDto(User seller, String foodId) {
        FoodItem food = sellerFood(seller, foodId);
        return FoodItemDTO.builder()
                .name(food.getName())
                .categoryId(food.getCategory().getId())
                .description(food.getDescription())
                .price(food.getPrice())
                .image(food.getImage())
                .available(food.isAvailable())
                .featured(food.isFeatured())
                .spicySupported(food.isSpicySupported())
                .build();
    }

    @Caching(evict = {
            @CacheEvict(value = "publicCarts", allEntries = true),
            @CacheEvict(value = "featuredFoods", allEntries = true),
            @CacheEvict(value = "categories", allEntries = true)
    })
    @Transactional
    public void updateFood(User seller, String foodId, FoodItemDTO dto) {
        FoodItem food = sellerFood(seller, foodId);
        Category category = activeCategory(dto.getCategoryId());
        food.setName(dto.getName().trim());
        food.setSlug(slugify(dto.getName()));
        food.setCategory(category);
        food.setDescription(dto.getDescription().trim());
        food.setPrice(dto.getPrice());
        imageUrlPolicy.requireTrusted(dto.getImage());
        food.setImage(dto.getImage().trim());
        food.setAvailable(dto.isAvailable());
        food.setFeatured(dto.isFeatured());
        food.setSpicySupported(dto.isSpicySupported());
        foodItemRepository.save(food);
    }

    @Caching(evict = {
            @CacheEvict(value = "publicCarts", allEntries = true),
            @CacheEvict(value = "featuredFoods", allEntries = true),
            @CacheEvict(value = "categories", allEntries = true)
    })
    @Transactional
    public void archiveFood(User seller, String foodId) {
        FoodItem food = sellerFood(seller, foodId);
        food.setArchived(true);
        food.setAvailable(false);
        food.setFeatured(false);
        foodItemRepository.save(food);
    }

    @Caching(evict = {
            @CacheEvict(value = "publicCarts", allEntries = true),
            @CacheEvict(value = "featuredFoods", allEntries = true),
            @CacheEvict(value = "categories", allEntries = true)
    })
    @Transactional
    public void toggleSellerFoodAvailability(User seller, String foodId) {
        FoodItem food = sellerFood(seller, foodId);
        food.setAvailable(!food.isAvailable());
        foodItemRepository.save(food);
    }

    // ---------- Admin catalog ----------

    public List<FoodCart> allCarts() {
        return relations.carts(foodCartRepository.findAllByOrderByCreatedAtDesc());
    }

    public List<FoodItem> allFoods() {
        return relations.foods(foodItemRepository.findAllByOrderByNameAsc());
    }

    public List<Category> allCategories() {
        return categoryRepository.findAllByOrderByNameAsc();
    }

    @Caching(evict = {
            @CacheEvict(value = "publicCarts", allEntries = true),
            @CacheEvict(value = "featuredFoods", allEntries = true),
            @CacheEvict(value = "categories", allEntries = true)
    })
    @Transactional
    public void toggleCartApproval(String cartId) {
        FoodCart cart = foodCartRepository.findById(cartId).map(relations::cart)
                .orElseThrow(() -> new IllegalArgumentException("Food cart was not found."));
        cart.setApproved(!cart.isApproved());
        if (!cart.isApproved() || cart.getOwner() == null || !cart.getOwner().isEnabled()) {
            cart.setOpen(false);
        }
        foodCartRepository.save(cart);
        notificationService.send(cart.getOwner(), NotificationType.ACCOUNT,
                cart.isApproved() ? "Food cart approved" : "Food cart suspended",
                cart.isApproved()
                        ? "Your food cart has been approved. You can now open it for orders."
                        : "Your food cart has been suspended by an administrator.",
                "/seller/food-cart");
    }

    @Caching(evict = {
            @CacheEvict(value = "publicCarts", allEntries = true),
            @CacheEvict(value = "featuredFoods", allEntries = true),
            @CacheEvict(value = "categories", allEntries = true)
    })
    @Transactional
    public void updateFoodPrice(String foodId, BigDecimal price) {
        if (price == null || price.signum() <= 0 || price.compareTo(new BigDecimal("100000")) > 0) {
            throw new IllegalArgumentException("Enter a valid food price.");
        }
        FoodItem food = foodItemRepository.findById(foodId)
                .orElseThrow(() -> new IllegalArgumentException("Food item was not found."));
        food.setPrice(price);
        foodItemRepository.save(food);
    }

    @Caching(evict = {
            @CacheEvict(value = "publicCarts", allEntries = true),
            @CacheEvict(value = "featuredFoods", allEntries = true),
            @CacheEvict(value = "categories", allEntries = true)
    })
    @Transactional
    public void toggleFoodAvailability(String foodId) {
        FoodItem food = foodItemRepository.findById(foodId)
                .orElseThrow(() -> new IllegalArgumentException("Food item was not found."));
        if (food.isArchived()) {
            throw new IllegalArgumentException("Restore this food item before changing availability.");
        }
        food.setAvailable(!food.isAvailable());
        foodItemRepository.save(food);
    }

    @Caching(evict = {
            @CacheEvict(value = "publicCarts", allEntries = true),
            @CacheEvict(value = "featuredFoods", allEntries = true),
            @CacheEvict(value = "categories", allEntries = true)
    })
    @Transactional
    public void toggleFoodFeatured(String foodId) {
        FoodItem food = foodItemRepository.findById(foodId)
                .orElseThrow(() -> new IllegalArgumentException("Food item was not found."));
        if (food.isArchived()) {
            throw new IllegalArgumentException("Restore this food item before changing featured status.");
        }
        food.setFeatured(!food.isFeatured());
        foodItemRepository.save(food);
    }

    @Caching(evict = {
            @CacheEvict(value = "publicCarts", allEntries = true),
            @CacheEvict(value = "featuredFoods", allEntries = true),
            @CacheEvict(value = "categories", allEntries = true)
    })
    @Transactional
    public void toggleFoodArchived(String foodId) {
        FoodItem food = foodItemRepository.findById(foodId)
                .orElseThrow(() -> new IllegalArgumentException("Food item was not found."));
        food.setArchived(!food.isArchived());
        if (food.isArchived()) {
            food.setAvailable(false);
            food.setFeatured(false);
        }
        foodItemRepository.save(food);
    }

    @Caching(evict = {
            @CacheEvict(value = "publicCarts", allEntries = true),
            @CacheEvict(value = "featuredFoods", allEntries = true),
            @CacheEvict(value = "categories", allEntries = true)
    })
    @Transactional
    public Category createCategory(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.length() < 2) {
            throw new IllegalArgumentException("Category name is too short.");
        }
        if (categoryRepository.findByNameNormalized(clean.toLowerCase(Locale.ROOT)).isPresent()) {
            throw new IllegalArgumentException("This category already exists.");
        }
        String slug = slugify(clean);
        if (categoryRepository.existsBySlug(slug)) {
            throw new IllegalArgumentException("A category with this slug already exists.");
        }
        return categoryRepository.save(Category.builder()
                .name(clean).nameNormalized(clean.toLowerCase(Locale.ROOT))
                .slug(slug)
                .image("/images/ui/category-default.svg")
                .active(true)
                .build());
    }

    @Caching(evict = {
            @CacheEvict(value = "publicCarts", allEntries = true),
            @CacheEvict(value = "featuredFoods", allEntries = true),
            @CacheEvict(value = "categories", allEntries = true)
    })
    @Transactional
    public void toggleCategory(String id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category was not found."));
        category.setActive(!category.isActive());
        categoryRepository.save(category);
    }

    @Caching(evict = {
            @CacheEvict(value = "categories", allEntries = true),
            @CacheEvict(value = "featuredFoods", allEntries = true),
            @CacheEvict(value = "publicCarts", allEntries = true)
    })
    @Transactional
    public void updateCategoryImage(String id, String imageUrl) {
        imageUrlPolicy.requireTrusted(imageUrl);
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category was not found"));
        category.setImage(imageUrl);
        categoryRepository.save(category);
    }

    public static String slugify(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        return normalized.isBlank() ? "item" : normalized;
    }

    private Category activeCategory(String id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category was not found."));
        if (!category.isActive()) {
            throw new IllegalArgumentException("Choose an active food category.");
        }
        return category;
    }

    private String uniqueCartSlug(String value) {
        String base = slugify(value);
        String candidate = base;
        int counter = 2;
        while (foodCartRepository.existsBySlug(candidate)) {
            candidate = base + "-" + counter++;
        }
        return candidate;
    }

    private double average(List<Review> reviews) {
        return reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);
    }

    private double roundRating(Double value) {
        return value == null ? 0.0 : Math.round(value * 10.0) / 10.0;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
