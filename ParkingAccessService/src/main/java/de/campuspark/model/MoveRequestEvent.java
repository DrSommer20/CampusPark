package de.campuspark.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Event: Ein Nutzer soll umparken.
 * Wird vom Web-Service an das Topic "parking/move/request" gesendet.
 */
public class MoveRequestEvent {

    private final String plate;        // Kennzeichen
    private final String requestedBy;  // z.B. "system", "security", "admin"
    private final String timestamp;    // Zeit der Aufforderung (ISO8601)

    @JsonCreator
    public MoveRequestEvent(
            @JsonProperty("plate") String plate,
            @JsonProperty("requestedBy") String requestedBy,
            @JsonProperty("timestamp") String timestamp
    ) {
        this.plate = plate;
        this.requestedBy = requestedBy;
        this.timestamp = timestamp;
    }

    public String getPlate() {
        return plate;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public String getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "MoveRequestEvent{" +
                "plate='" + plate + '\'' +
                ", requestedBy='" + requestedBy + '\'' +
                ", timestamp='" + timestamp + '\'' +
                '}';
    }
}
