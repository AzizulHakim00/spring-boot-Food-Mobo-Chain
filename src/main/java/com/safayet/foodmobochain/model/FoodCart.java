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
@Document(collection = "foodCarts")
public class FoodCart {
    @Id
    private String id;

    private String ownerId;

    private String name;

    private String slug;

    private String description;

    private String location;

    private String cuisine;

    private String coverImage;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal deliveryFee;

    private int estimatedDeliveryMinutes;

    @Builder.Default
    private boolean open = true;

    @Builder.Default
    private boolean approved = false;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Transient
    private User owner;

    public void setOwner(User value) {
        this.owner = value;
        this.ownerId = value == null ? null : value.getId();
    }

}
