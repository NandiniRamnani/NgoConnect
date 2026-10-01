package app.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "event_enrollments")
public class EventEnrollment {
    @Id private String id;
    private String eventId;
    private String userId;
    private String userEmail;
    private String userName;
    private Instant enrolledAt;
    // null = not yet marked, true = attended, false = absent
    private Boolean attended;
    private boolean absenceNotificationSent;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public Instant getEnrolledAt() { return enrolledAt; }
    public void setEnrolledAt(Instant enrolledAt) { this.enrolledAt = enrolledAt; }
    public Boolean getAttended() { return attended; }
    public void setAttended(Boolean attended) { this.attended = attended; }
    public boolean isAbsenceNotificationSent() { return absenceNotificationSent; }
    public void setAbsenceNotificationSent(boolean absenceNotificationSent) { this.absenceNotificationSent = absenceNotificationSent; }
}
