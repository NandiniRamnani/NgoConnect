package app.model;

import app.enums.NotificationType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "ngo_notifications")
public class NgoNotification {
    @Id private String id;
    private String ngoId, ngoName, title, description, location, resourceDetails, deliveryAddress;
    private NotificationType type;
    private Integer requiredVolunteers;
    private Instant deadline, createdAt;
    private boolean active;
    /** Urgent needs are listed first and badged, so a same-day shortage is not buried under older posts. */
    private boolean urgent;
    public boolean isUrgent() { return urgent; } public void setUrgent(boolean urgent) { this.urgent = urgent; }
    public String getId() { return id; } public void setId(String id) { this.id = id; }
    public String getNgoId() { return ngoId; } public void setNgoId(String ngoId) { this.ngoId = ngoId; }
    public String getNgoName() { return ngoName; } public void setNgoName(String ngoName) { this.ngoName = ngoName; }
    public String getTitle() { return title; } public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }
    public String getLocation() { return location; } public void setLocation(String location) { this.location = location; }
    public String getResourceDetails() { return resourceDetails; } public void setResourceDetails(String resourceDetails) { this.resourceDetails = resourceDetails; }
    public String getDeliveryAddress() { return deliveryAddress; } public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    public NotificationType getType() { return type; } public void setType(NotificationType type) { this.type = type; }
    public Integer getRequiredVolunteers() { return requiredVolunteers; } public void setRequiredVolunteers(Integer requiredVolunteers) { this.requiredVolunteers = requiredVolunteers; }
    public Instant getDeadline() { return deadline; } public void setDeadline(Instant deadline) { this.deadline = deadline; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public boolean isActive() { return active; } public void setActive(boolean active) { this.active = active; }
}
