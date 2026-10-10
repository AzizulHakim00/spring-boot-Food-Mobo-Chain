package com.safayet.foodmobochain.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/** Only public catalog data is cached; orders, authentication, and payments are not. */
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager caffeine = new CaffeineCacheManager();
        caffeine.setCacheNames(List.of("publicCarts", "featuredFoods", "categories"));
        caffeine.setCaffeine(Caffeine.newBuilder().maximumSize(200).expireAfterWrite(Duration.ofMinutes(3)));
        // Evictions happen after successful MongoDB transaction commits rather than before.
        return new TransactionAwareCacheManagerProxy(caffeine);
    }
}
