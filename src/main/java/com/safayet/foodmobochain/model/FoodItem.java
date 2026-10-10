package com.safayet.foodmobochain.model;


import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "foodItems")
public class FoodItem {
    @Id
    private String id;

    private String foodCartId;

    private String categoryId;

    private String name;

    private String slug;

    private String description;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal price;

    private String image;

    @Builder.Default
    private boolean available = true;

    @Builder.Default
    private boolean featured = false;

    @Builder.Default
    private boolean spicySupported = false;

    @Builder.Default
    private boolean archived = false;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Transient
    private FoodCart foodCart;

    @Transient
    private Category category;

    public void setFoodCart(FoodCart value) {
        this.foodCart = value;
        this.foodCartId = value == null ? null : value.getId();
    }

    public void setCategory(Category value) {
        this.category = value;
        this.categoryId = value == null ? null : value.getId();
    }

}
