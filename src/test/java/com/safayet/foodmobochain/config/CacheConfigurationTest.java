package com.safayet.foodmobochain.config;

import com.safayet.foodmobochain.service.CatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.cache.annotation.EnableCaching;

import static org.junit.jupiter.api.Assertions.*;

class CacheConfigurationTest {
    @Test
    void caffeineCachingIsEnabledAndLimitedToPublicCatalogData() {
        assertTrue(CacheConfig.class.isAnnotationPresent(EnableCaching.class),
                "Spring Cache interception must be enabled");
        CacheManager manager = new CacheConfig().cacheManager();
        assertInstanceOf(TransactionAwareCacheManagerProxy.class, manager);
        for (String name : new String[]{"publicCarts", "featuredFoods", "categories"}) {
            Cache cache = manager.getCache(name);
            assertNotNull(cache, "Expected Caffeine cache: " + name);
            cache.put("test", "value");
            assertEquals("value", cache.get("test", String.class));
            cache.evict("test");
            assertNull(cache.get("test"));
        }
        assertNull(manager.getCache("orders"), "Orders must never be cached");
        assertNull(manager.getCache("payments"), "Payments must never be cached");
        assertNull(manager.getCache("users"), "User authorization must read current database state");
    }

    @Test
    void publicReadsAreCachedAndCatalogWritesEvictTheSnapshot() throws Exception {
        assertNotNull(CatalogService.class.getMethod("carts").getAnnotation(Cacheable.class));
        assertNotNull(CatalogService.class.getMethod("featuredFoods").getAnnotation(Cacheable.class));
        assertNotNull(CatalogService.class.getMethod("categories").getAnnotation(Cacheable.class));
        assertNotNull(CatalogService.class.getMethod("createFood",
                com.safayet.foodmobochain.model.User.class,
                com.safayet.foodmobochain.dto.FoodItemDTO.class).getAnnotation(Caching.class));
        assertNotNull(CatalogService.class.getMethod("toggleSellerCartOpen",
                com.safayet.foodmobochain.model.User.class).getAnnotation(Caching.class));
    }
}
