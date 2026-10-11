package com.azizul.foodmobochain.security;

import com.azizul.foodmobochain.model.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/** Authentication principal keeps account sessionVersion from the same database read.
 *  JWT checks never trust a role claimed inside a token. */
public final class AccountUserDetails extends org.springframework.security.core.userdetails.User {
    private final long sessionVersion;

    public AccountUserDetails(User account) {
        super(account.getEmail(), account.getPassword(),
                account.isEnabled() && !Boolean.FALSE.equals(account.getEmailVerified()),
                true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name())));
        this.sessionVersion = account.getSessionVersion();
    }

    public long sessionVersion() {
        return sessionVersion;
    }
}
