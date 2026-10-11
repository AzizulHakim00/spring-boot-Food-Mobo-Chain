package com.azizul.foodmobochain.model;

import com.azizul.foodmobochain.model.enums.Role;
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
@Document(collection = "users")
public class User {
    @Id
    private String id;

    private String fullName;

    private String email;

    private String emailNormalized;

    /**
     * null = previously existing account (grandfathered to preserve demo access).
     * false = newly registered and waiting for email ownership proof.
     * true = explicitly verified.
     */
    private Boolean emailVerified;

    private String phone;

    private String password;

    private String address;

    @Builder.Default
    private Role role = Role.BUYER;

    @Builder.Default
    private boolean enabled = true;

    /** Increment when account credentials/access change to revoke earlier JWTs. */
    @Builder.Default
    private long sessionVersion = 0L;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

}
