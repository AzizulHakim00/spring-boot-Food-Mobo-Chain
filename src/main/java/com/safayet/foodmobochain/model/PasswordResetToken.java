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
@Document(collection = "passwordResetTokens")
public class PasswordResetToken {
    @Id
    private String id;

    private String userId;

    private String tokenHash;

    private LocalDateTime expiresAt;

    @Builder.Default
    private boolean used = false;

    @CreatedDate
    private LocalDateTime createdAt;

    @Transient
    private User user;

    public void setUser(User value) {
        this.user = value;
        this.userId = value == null ? null : value.getId();
    }

}
