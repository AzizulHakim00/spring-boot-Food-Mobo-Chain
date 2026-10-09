package com.safayet.foodmobochain.model;

import com.safayet.foodmobochain.model.enums.DiscountType;
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
@Document(collection = "discounts")
public class Discount {
    @Id
    private String id;

    private String code;

    private String codeNormalized;

    private String name;

    private String description;

    private DiscountType type;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal value;

    @Field(targetType = FieldType.DECIMAL128)
    @Builder.Default
    private BigDecimal minimumOrder = BigDecimal.ZERO;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal maximumDiscount;

    private LocalDateTime startsAt;

    private LocalDateTime endsAt;

    @Builder.Default
    private boolean active = true;

}
