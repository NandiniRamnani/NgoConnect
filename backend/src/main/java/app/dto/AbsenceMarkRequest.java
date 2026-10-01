package app.dto;

public class AbsenceMarkRequest {
    private String message; // optional custom message from NGO to the absent user

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
