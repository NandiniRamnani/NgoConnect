package app.service;

import app.dto.AbsenceMarkRequest;
import app.model.*;
import app.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final EventEnrollmentRepository enrollmentRepository;
    private final NgoRepository ngoRepository;
    private final AccountRepository accountRepository;
    private final UserNotificationRepository notificationRepository;

    public EventService(EventRepository eventRepository,
                        EventEnrollmentRepository enrollmentRepository,
                        NgoRepository ngoRepository,
                        AccountRepository accountRepository,
                        UserNotificationRepository notificationRepository) {
        this.eventRepository = eventRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.ngoRepository = ngoRepository;
        this.accountRepository = accountRepository;
        this.notificationRepository = notificationRepository;
    }

    // ── NGO: Create event ────────────────────────────────────────────────────
    public Event createEvent(String ngoId, String ngoEmail, Event event) {
        Ngo ngo = assertNgoOwner(ngoId, ngoEmail);
        if (event.getTitle() == null || event.getTitle().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event title is required");
        if (event.getMaxSpots() < 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "maxSpots must be at least 1");

        event.setNgoId(ngoId);
        event.setNgoName(ngo.getNgoName());
        event.setEnrolledCount(0);
        event.setActive(true);
        event.setCreatedAt(Instant.now());
        return eventRepository.save(event);
    }

    // ── Public: Browse events ────────────────────────────────────────────────
    public List<Event> getActiveEvents(String category) {
        if (category == null || category.isBlank())
            return eventRepository.findByActiveTrueOrderByEventDateAsc();
        return eventRepository.findByActiveTrueAndCategoryOrderByEventDateAsc(category);
    }

    public List<Event> getEventsByNgo(String ngoId) {
        return eventRepository.findByNgoIdOrderByCreatedAtDesc(ngoId);
    }

    public Event getEventById(String eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    }

    // ── User: Enroll ─────────────────────────────────────────────────────────
    public EventEnrollment enroll(String eventId, String userEmail) {
        Event event = getEventById(eventId);
        if (!event.isActive())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event is no longer active");

        Account user = accountRepository.findByEmail(userEmail);
        if (user == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User account not found");

        if (enrollmentRepository.existsByEventIdAndUserId(eventId, user.getId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already enrolled in this event");

        if (event.getEnrolledCount() >= event.getMaxSpots())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Event is fully booked");

        EventEnrollment enrollment = new EventEnrollment();
        enrollment.setEventId(eventId);
        enrollment.setUserId(user.getId());
        enrollment.setUserEmail(user.getEmail());
        enrollment.setUserName(user.getFullName());
        enrollment.setEnrolledAt(Instant.now());

        event.setEnrolledCount(event.getEnrolledCount() + 1);
        eventRepository.save(event);

        return enrollmentRepository.save(enrollment);
    }

    // ── NGO: View enrollments ────────────────────────────────────────────────
    public List<EventEnrollment> getEnrollments(String eventId, String ngoEmail) {
        Event event = getEventById(eventId);
        Ngo ngo = ngoRepository.findByEmail(ngoEmail);
        if (ngo == null || !ngo.getId().equals(event.getNgoId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only view your own event's enrollments");
        return enrollmentRepository.findByEventId(eventId);
    }

    // ── NGO: Mark attendance ─────────────────────────────────────────────────
    public EventEnrollment markAttendance(String enrollmentId, boolean attended, String ngoEmail, AbsenceMarkRequest req) {
        EventEnrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment not found"));

        Event event = getEventById(enrollment.getEventId());
        Ngo ngo = ngoRepository.findByEmail(ngoEmail);
        if (ngo == null || !ngo.getId().equals(event.getNgoId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized");

        enrollment.setAttended(attended);

        // If marked absent → create a user notification
        if (!attended && !enrollment.isAbsenceNotificationSent()) {
            UserNotification note = new UserNotification();
            note.setUserId(enrollment.getUserId());
            note.setUserEmail(enrollment.getUserEmail());
            note.setNgoId(ngo.getId());
            note.setNgoName(ngo.getNgoName());
            note.setType("ABSENCE_ALERT");
            note.setEventId(event.getId());
            note.setTitle("We missed you at: " + event.getTitle());
            String customMsg = (req != null && req.getMessage() != null && !req.getMessage().isBlank())
                    ? req.getMessage()
                    : "You were marked absent from the event \"" + event.getTitle() + "\" on " + event.getEventDate() + ". We hope everything is okay!";
            note.setMessage(customMsg);
            note.setRead(false);
            note.setCreatedAt(Instant.now());
            notificationRepository.save(note);
            enrollment.setAbsenceNotificationSent(true);
        }

        return enrollmentRepository.save(enrollment);
    }

    // ── User: My enrolled events ─────────────────────────────────────────────
    public List<EventEnrollment> getMyEnrollments(String userEmail) {
        Account user = accountRepository.findByEmail(userEmail);
        if (user == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found");
        return enrollmentRepository.findByUserId(user.getId());
    }

    // ── NGO: Cancel/deactivate event ────────────────────────────────────────
    public Event deactivateEvent(String eventId, String ngoEmail) {
        Event event = getEventById(eventId);
        assertNgoOwner(event.getNgoId(), ngoEmail);
        event.setActive(false);
        return eventRepository.save(event);
    }

    private Ngo assertNgoOwner(String ngoId, String ngoEmail) {
        Ngo ngo = ngoRepository.findById(ngoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "NGO not found"));
        if (!ngo.getEmail().equalsIgnoreCase(ngoEmail))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only manage your own NGO");
        return ngo;
    }
}
