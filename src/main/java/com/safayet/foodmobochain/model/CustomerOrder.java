package com.safayet.foodmobochain.model;

import com.safayet.foodmobochain.model.enums.OrderStatus;
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
@Document(collection = "orders")
public class CustomerOrder {
    @Id
    private String id;

    private String orderNumber;

    private String buyerId;

    private String foodCartId;

    private OrderStatus status;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal subtotal;

    @Field(targetType = FieldType.DECIMAL128)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal deliveryFee;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal total;

    private String discountCode;

    private String deliveryAddress;

    private String phone;

    private String note;

    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    private Payment payment;

    private Delivery delivery;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @Transient
    private User buyer;

    @Transient
    private FoodCart foodCart;

    public void setBuyer(User value) {
        this.buyer = value;
        this.buyerId = value == null ? null : value.getId();
    }

    public void setFoodCart(FoodCart value) {
        this.foodCart = value;
        this.foodCartId = value == null ? null : value.getId();
    }

}
