package app.controller;

import app.dto.AdminAccountResponse;
import app.enums.DonationStatus;
import app.model.Donation;
import app.model.Event;
import app.model.NgoNotification;
import app.repository.DonationRepository;
import app.repository.EventRepository;
import app.repository.NgoNotificationRepository;
import app.service.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

/**
 * Platform-wide admin actions beyond NGO application review — covers users,
 * events, donations, and needs. All paths already require ROLE_ADMIN via the
 * blanket "/api/admin/**" rule in SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminManagementController {

    private final AccountService accountService;
    private final EventRepository eventRepository;
    private final DonationRepository donationRepository;
    private final NgoNotificationRepository notificationRepository;

    public AdminManagementController(AccountService accountService, EventRepository eventRepository,
                                     DonationRepository donationRepository, NgoNotificationRepository notificationRepository) {
        this.accountService = accountService;
        this.eventRepository = eventRepository;
        this.donationRepository = donationRepository;
        this.notificationRepository = notificationRepository;
    }

    // ── Accounts ─────────────────────────────────────────────────────────────
    @GetMapping("/accounts")
    public List<AdminAccountResponse> listAccounts() {
        return accountService.findAllForAdmin();
    }

    @PatchMapping("/accounts/{id}/active")
    public AdminAccountResponse setAccountActive(@PathVariable String id, @RequestBody Map<String, Boolean> body) {
        return accountService.setActive(id, Boolean.TRUE.equals(body.get("active")));
    }

    @DeleteMapping("/accounts/{id}")
    public void deleteAccount(@PathVariable String id) {
        accountService.delete(id);
    }

    // ── Events ───────────────────────────────────────────────────────────────
    @GetMapping("/events")
    public List<Event> listEvents() {
        return eventRepository.findAll();
    }

    @DeleteMapping("/events/{id}")
    public void deleteEvent(@PathVariable String id) {
        if (!eventRepository.existsById(id))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found");
        eventRepository.deleteById(id);
    }

    // ── Donations ────────────────────────────────────────────────────────────
    @GetMapping("/donations")
    public List<Donation> listDonations() {
        return donationRepository.findAll();
    }

    @PatchMapping("/donations/{id}/status")
    public Donation setDonationStatus(@PathVariable String id, @RequestBody Map<String, String> body) {
        Donation donation = donationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Donation not found"));
        try {
            donation.setStatus(DonationStatus.valueOf(body.get("status")));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status");
        }
        return donationRepository.save(donation);
    }

    // ── Needs / notifications ───────────────────────────────────────────────
    @GetMapping("/notifications")
    public List<NgoNotification> listNotifications() {
        return notificationRepository.findAll();
    }

    @DeleteMapping("/notifications/{id}")
    public void deleteNotification(@PathVariable String id) {
        if (!notificationRepository.existsById(id))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
        notificationRepository.deleteById(id);
    }
}
