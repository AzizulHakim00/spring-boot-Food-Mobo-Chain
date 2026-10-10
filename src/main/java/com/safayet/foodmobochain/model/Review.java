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
@Document(collection = "reviews")
public class Review {
    @Id
    private String id;

    private String buyerId;

    private String foodItemId;

    private String foodCartId;

    private int rating;

    private String comment;

    @Builder.Default
    private boolean approved = false;

    @Builder.Default
    private boolean hidden = false;

    @CreatedDate
    private LocalDateTime createdAt;

    @Transient
    private User buyer;

    @Transient
    private FoodItem foodItem;

    @Transient
    private FoodCart foodCart;

    public void setBuyer(User value) {
        this.buyer = value;
        this.buyerId = value == null ? null : value.getId();
    }

    public void setFoodItem(FoodItem value) {
        this.foodItem = value;
        this.foodItemId = value == null ? null : value.getId();
    }

    public void setFoodCart(FoodCart value) {
        this.foodCart = value;
        this.foodCartId = value == null ? null : value.getId();
    }

}
