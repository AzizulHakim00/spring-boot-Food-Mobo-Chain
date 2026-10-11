package com.safayet.foodmobochain.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

/** One active verification link per account; plaintext tokens never enter MongoDB. */
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Document(collection = "emailVerificationTokens")
public class EmailVerificationToken {
    @Id
    private String id;
    private String userId;
    private String tokenHash;
    private LocalDateTime expiresAt;
    private LocalDateTime lastSentAt;
    @Builder.Default private boolean used = false;
    @Version private Long version;
}
