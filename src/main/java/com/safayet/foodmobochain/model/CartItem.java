package com.safayet.foodmobochain.model;

import com.safayet.foodmobochain.model.enums.SpiceLevel;
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
public class CartItem {
    @Id
    private String id;

    private String foodItemId;

    private int quantity;

    @Builder.Default
    private SpiceLevel spiceLevel = SpiceLevel.REGULAR;

    @Transient
    private FoodItem foodItem;

    public void setFoodItem(FoodItem value) {
        this.foodItem = value;
        this.foodItemId = value == null ? null : value.getId();
    }

}
