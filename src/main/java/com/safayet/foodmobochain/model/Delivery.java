package com.safayet.foodmobochain.model;

import com.safayet.foodmobochain.model.enums.DeliveryStatus;
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
public class Delivery {
    @Id
    private String id;

    @Builder.Default
    private DeliveryStatus status = DeliveryStatus.WAITING;

    private String address;

    private String contactNumber;

    private int estimatedMinutes;

    private LocalDateTime dispatchedAt;

    private LocalDateTime deliveredAt;

    @Transient
    private CustomerOrder order;

}
