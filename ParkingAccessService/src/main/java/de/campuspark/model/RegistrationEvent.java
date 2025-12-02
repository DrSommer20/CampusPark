package de.campuspark.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Event zur Registrierung eines neuen Nutzers / Kennzeichens.
 * Gesendet vom Web-Service an das MQTT Topic "parking/registration/plate".
 */
public class RegistrationEvent {

    private final String plate;        // Kennzeichen
    private final String userId;       // interne Benutzer-ID
    private final String role;         // z.B. student, mitarbeiter, professor, behindert
    private final String phoneNumber;  // für SMS-Notifications
    private final String timestamp;    // Zeitpunkt der Registrierung (ISO8601)

    @JsonCreator
    public RegistrationEvent(
            @JsonProperty("plate") String plate,
            @JsonProperty("userId") String userId,
            @JsonProperty("role") String role,
            @JsonProperty("phoneNumber") String phoneNumber,
            @JsonProperty("timestamp") String timestamp
    ) {
        this.plate = plate;
        this.userId = userId;
        this.role = role;
        this.phoneNumber = phoneNumber;
        this.timestamp = timestamp;
    }

    public String getPlate() {
        return plate;
    }

    public String getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "RegistrationEvent{" +
                "plate='" + plate + '\'' +
                ", userId='" + userId + '\'' +
                ", role='" + role + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", timestamp='" + timestamp + '\'' +
                '}';
    }
}
