package com.safayet.foodmobochain.service;

import com.safayet.foodmobochain.model.Notification;
import com.safayet.foodmobochain.model.User;
import com.safayet.foodmobochain.model.enums.NotificationType;
import com.safayet.foodmobochain.repository.NotificationRepository;
import com.safayet.foodmobochain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional
    public void send(User user, NotificationType type, String title, String message, String link) {
        notificationRepository.save(Notification.builder()
                .user(user).userId(user.getId())
                .type(type)
                .title(title)
                .message(message)
                .link(link)
                .read(false)
                .build());
    }

    public List<Notification> recent(User user) {
        return notificationRepository.findTop8ByUserIdOrderByCreatedAtDesc(user.getId());
    }

    public List<Notification> all(User user) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    public long unreadCount(User user) {
        return notificationRepository.countByUserIdAndReadFalse(user.getId());
    }

    @Transactional
    public void markRead(User user, String id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification was not found."));
        if (!notification.getUserId().equals(user.getId())) {
            throw new SecurityException("You cannot access this notification.");
        }
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public int broadcast(NotificationType type, String title, String message, String link) {
        String cleanTitle = title == null ? "" : title.trim();
        String cleanMessage = message == null ? "" : message.trim();
        String cleanLink = normalizeInternalLink(link);
        if (cleanTitle.isBlank() || cleanTitle.length() > 160) {
            throw new IllegalArgumentException("Notification title must be 1 to 160 characters.");
        }
        if (cleanMessage.isBlank() || cleanMessage.length() > 600) {
            throw new IllegalArgumentException("Notification message must be 1 to 600 characters.");
        }

        List<User> users = userRepository.findByEnabledTrue();
        List<Notification> notifications = users.stream()
                .map(user -> Notification.builder()
                        .user(user).userId(user.getId())
                        .type(type)
                        .title(cleanTitle)
                        .message(cleanMessage)
                        .link(cleanLink)
                        .read(false)
                        .build())
                .toList();
        notificationRepository.saveAll(notifications);
        return notifications.size();
    }

    private String normalizeInternalLink(String link) {
        if (link == null || link.isBlank()) {
            return null;
        }
        String clean = link.trim();
        if (!clean.startsWith("/") || clean.startsWith("//") || clean.contains("\r") || clean.contains("\n")) {
            throw new IllegalArgumentException("Notification links must be internal paths such as /foods.");
        }
        return clean;
    }
}
