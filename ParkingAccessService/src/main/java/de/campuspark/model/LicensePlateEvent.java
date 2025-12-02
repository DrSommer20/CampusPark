package de.campuspark.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Event, das vom Raspberry Pi Zero (ALPR Service) erzeugt wird.
 * Es enthält ein erfasstes Kennzeichen + Kontextinformationen zur Einfahrt.
 */
public class LicensePlateEvent {

    private final String plate;       // Das erkannte Kennzeichen
    private final String gateId;      // Name/ID der Einfahrt (z.B. "Einfahrt-Nord")
    private final String timestamp;   // ISO8601 Zeitstempel der Erkennung
    private final double confidence;  // Wahrscheinlichkeit/Qualität (0.0 - 1.0)

    @JsonCreator
    public LicensePlateEvent(
            @JsonProperty("plate") String plate,
            @JsonProperty("gateId") String gateId,
            @JsonProperty("timestamp") String timestamp,
            @JsonProperty("confidence") double confidence) {

        this.plate = plate;
        this.gateId = gateId;
        this.timestamp = timestamp;
        this.confidence = confidence;
    }

    public String getPlate() {
        return plate;
    }

    public String getGateId() {
        return gateId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public double getConfidence() {
        return confidence;
    }

    @Override
    public String toString() {
        return "LicensePlateEvent{" +
                "plate='" + plate + '\'' +
                ", gateId='" + gateId + '\'' +
                ", timestamp='" + timestamp + '\'' +
                ", confidence=" + confidence +
                '}';
    }
}
