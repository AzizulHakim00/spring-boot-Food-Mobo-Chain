package com.safayet.foodmobochain.model;

import com.safayet.foodmobochain.model.enums.PaymentMethod;
import com.safayet.foodmobochain.model.enums.PaymentStatus;
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
public class Payment {
    @Id
    private String id;

    private PaymentMethod method;

    private PaymentStatus status;

    private String gateway;

    private String transactionId;

    private String bankTransactionId;

    private String sessionKey;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal amount;

    private LocalDateTime paidAt;

    private LocalDateTime createdAt;

    @Transient
    private CustomerOrder order;

}
