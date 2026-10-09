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
@Document(collection = "favoriteCarts")
public class FavoriteCart {
    @Id
    private String id;

    private String userId;

    private String foodCartId;

    @Transient
    private User user;

    @Transient
    private FoodCart foodCart;

    public void setUser(User value) {
        this.user = value;
        this.userId = value == null ? null : value.getId();
    }

    public void setFoodCart(FoodCart value) {
        this.foodCart = value;
        this.foodCartId = value == null ? null : value.getId();
    }

}
