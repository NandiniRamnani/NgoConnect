package app.controller;

import app.model.UserNotification;
import app.repository.UserNotificationRepository;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users/notifications")
public class UserNotificationController {

    private final UserNotificationRepository notificationRepository;

    public UserNotificationController(UserNotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /** GET /api/users/notifications  →  all notifications for the logged-in user */
    @GetMapping
    public List<UserNotification> myNotifications(Authentication auth) {
        return notificationRepository.findByUserEmailOrderByCreatedAtDesc(auth.getName());
    }

    /** GET /api/users/notifications/unread-count */
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(Authentication auth) {
        return Map.of("unread", notificationRepository.countByUserEmailAndReadFalse(auth.getName()));
    }

    /** PATCH /api/users/notifications/{id}/read  →  mark as read */
    @PatchMapping("/{id}/read")
    public UserNotification markRead(@PathVariable String id, Authentication auth) {
        UserNotification note = notificationRepository.findById(id)
                .filter(n -> n.getUserEmail().equalsIgnoreCase(auth.getName()))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Notification not found"));
        note.setRead(true);
        return notificationRepository.save(note);
    }
}
