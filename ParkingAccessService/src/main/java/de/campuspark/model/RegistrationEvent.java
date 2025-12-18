package de.campuspark.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Event zur Registrierung eines neuen Nutzers / Kennzeichens.
 * Gesendet vom Web-Service an das MQTT Topic "parking/registration/plate".
 */
public class RegistrationEvent {

    private final String plate;        // Kennzeichen
    private final String role;         // z.B. student, mitarbeiter, professor, behindert
    private final String phoneNumber;  // für SMS-Notifications
    private final String timestamp;    // Zeitpunkt der Registrierung (ISO8601)
    private final String course;       // Kurs im Style ""WWI23A" falls Student 

    @JsonCreator
    public RegistrationEvent(
            @JsonProperty("plate") String plate,
            @JsonProperty("role") String role,
            @JsonProperty("phoneNumber") String phoneNumber,
            @JsonProperty("timestamp") String timestamp,
            @JsonProperty("course") String course
    ) {
        this.plate = plate;
        this.role = role;
        this.phoneNumber = phoneNumber;
        this.timestamp = timestamp;
        this.course = course;
    }

    public String getPlate() {
        return plate;
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

    public String getCourse(){
        return this.course;
    }

    @Override
    public String toString() {
        return "RegistrationEvent{" +
                "plate='" + plate + '\'' +
                ", role='" + role + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", course='" + course + '\'' +
                ", timestamp='" + timestamp + '\'' +
                '}';
    }
}
