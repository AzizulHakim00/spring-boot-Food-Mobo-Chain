package com.safayet.foodmobochain.service;

import com.safayet.foodmobochain.model.enums.NotificationType;
import com.safayet.foodmobochain.repository.NotificationRepository;
import com.safayet.foodmobochain.repository.UserRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationLinkPolicyTest {
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final NotificationService service = new NotificationService(notifications, users);

    @Test
    void rejectsBrowserBackslashAndEncodedExternalLinks() {
        for (String url : new String[] {
                "//outside.example/path",
                "/\\outside.example/path",
                "/%5c%5coutside.example/path",
                "/%2Foutside.example/path",
                "/orders/\nmalicious"
        }) {
            assertThatThrownBy(() -> service.broadcast(NotificationType.SYSTEM, "Notice", "Test", url))
                    .as("Unsafe notification link: %s", url)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(notifications, users);
    }

    @Test
    void validInternalOrderPathIsAllowed() {
        when(users.findByEnabledTrue()).thenReturn(java.util.List.of());
        assertThat(service.broadcast(NotificationType.SYSTEM, "Order update", "Ready", "/orders/FMC-123"))
                .isZero();
    }
}
