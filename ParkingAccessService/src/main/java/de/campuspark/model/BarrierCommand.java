package de.campuspark.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Event zur Steuerung der Schranke.
 * Wird vom ParkingAccessService auf das Topic "parking/access/barrier" publiziert.
 */
public class BarrierCommand {

    private final String gateId;   // z.B. "Einfahrt-Nord"
    private final String plate;    // Kennzeichen des Fahrzeugs
    private final String action;   // OPEN oder DENY
    private final String reason;   // optionaler Grund (z.B. unknown_plate)

    @JsonCreator
    public BarrierCommand(
            @JsonProperty("gateId") String gateId,
            @JsonProperty("plate") String plate,
            @JsonProperty("action") String action,
            @JsonProperty("reason") String reason
    ) {
        this.gateId = gateId;
        this.plate = plate;
        this.action = action;
        this.reason = reason;
    }

    public String getGateId() {
        return gateId;
    }

    public String getPlate() {
        return plate;
    }

    public String getAction() {
        return action;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "BarrierCommand{" +
                "gateId='" + gateId + '\'' +
                ", plate='" + plate + '\'' +
                ", action='" + action + '\'' +
                ", reason='" + reason + '\'' +
                '}';
    }
}
