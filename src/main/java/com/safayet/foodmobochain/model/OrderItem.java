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
public class OrderItem {
    @Id
    private String id;

    private String foodItemIdSnapshot;

    private String foodName;

    private String foodImage;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal unitPrice;

    private int quantity;

    private SpiceLevel spiceLevel;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal subtotal;

    @Transient
    private CustomerOrder order;

}
