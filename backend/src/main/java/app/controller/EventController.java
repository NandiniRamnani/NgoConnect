package app.controller;

import app.dto.AbsenceMarkRequest;
import app.model.Event;
import app.model.EventEnrollment;
import app.service.EventService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    // ── Public ────────────────────────────────────────────────────────────────

    /** GET /api/events?category=Health  →  browse active events */
    @GetMapping("/api/events")
    public List<Event> getActiveEvents(@RequestParam(required = false) String category) {
        return eventService.getActiveEvents(category);
    }

    /** GET /api/events/{id}  →  single event detail */
    @GetMapping("/api/events/{id}")
    public Event getEvent(@PathVariable String id) {
        return eventService.getEventById(id);
    }

    /** GET /api/ngos/{ngoId}/events  →  all events by an NGO */
    @GetMapping("/api/ngos/{ngoId}/events")
    public List<Event> getNgoEvents(@PathVariable String ngoId) {
        return eventService.getEventsByNgo(ngoId);
    }

    // ── NGO-protected ────────────────────────────────────────────────────────

    /** POST /api/ngos/{ngoId}/events  →  create event (NGO auth required) */
    @PostMapping("/api/ngos/{ngoId}/events")
    public Event createEvent(@PathVariable String ngoId,
                             @RequestBody Event event,
                             Authentication auth) {
        return eventService.createEvent(ngoId, auth.getName(), event);
    }

    /** GET /api/events/{eventId}/enrollments  →  see who enrolled (NGO auth) */
    @GetMapping("/api/events/{eventId}/enrollments")
    public List<EventEnrollment> getEnrollments(@PathVariable String eventId,
                                                Authentication auth) {
        return eventService.getEnrollments(eventId, auth.getName());
    }

    /**
     * PATCH /api/events/enrollments/{enrollmentId}/attend?present=true|false
     * NGO marks attendance; if absent, optionally sends custom message.
     */
    @PatchMapping("/api/events/enrollments/{enrollmentId}/attend")
    public EventEnrollment markAttendance(@PathVariable String enrollmentId,
                                          @RequestParam boolean present,
                                          @RequestBody(required = false) AbsenceMarkRequest req,
                                          Authentication auth) {
        return eventService.markAttendance(enrollmentId, present, auth.getName(), req);
    }

    /** DELETE /api/ngos/{ngoId}/events/{eventId}  →  deactivate event */
    @DeleteMapping("/api/ngos/{ngoId}/events/{eventId}")
    public Event deactivateEvent(@PathVariable String ngoId,
                                 @PathVariable String eventId,
                                 Authentication auth) {
        return eventService.deactivateEvent(eventId, auth.getName());
    }

    // ── User-protected ────────────────────────────────────────────────────────

    /** POST /api/events/{eventId}/enroll  →  user enrolls in event */
    @PostMapping("/api/events/{eventId}/enroll")
    public EventEnrollment enroll(@PathVariable String eventId,
                                  Authentication auth) {
        return eventService.enroll(eventId, auth.getName());
    }

    /** GET /api/events/my-enrollments  →  user's enrolled events */
    @GetMapping("/api/events/my-enrollments")
    public List<EventEnrollment> myEnrollments(Authentication auth) {
        return eventService.getMyEnrollments(auth.getName());
    }
}
