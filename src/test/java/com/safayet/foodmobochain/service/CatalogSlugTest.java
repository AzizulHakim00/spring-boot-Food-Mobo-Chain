package com.safayet.foodmobochain.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogSlugTest {
    @Test
    void createsCleanUrlSlug() {
        assertThat(CatalogService.slugify("Dhaka's Best Food Cart!"))
                .isEqualTo("dhaka-s-best-food-cart");
    }
}
