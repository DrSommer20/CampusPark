package de.campuspark.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Event für die Zuweisung eines Parkplatzes an ein Fahrzeug.
 * Wird vom ParkingAccessService publiziert auf "parking/access/allocation".
 * Der ESP32 am Display/Barrier liest diesen Wert und zeigt den Spot bzw. die Richtung an.
 */
public class AllocationEvent {

    private final String plate;       // erkannter Nutzer / Kennzeichen
    private final String gateId;      // Einfahrt aus der das Fahrzeug kommt
    private final String spotId;      // zugewiesener Parkplatz (z.B. "A-12")
    @JsonCreator
    public AllocationEvent(
            @JsonProperty("plate") String plate,
            @JsonProperty("gateId") String gateId,
            @JsonProperty("spotId") String spotId
    ) {
        this.plate = plate;
        this.gateId = gateId;
        this.spotId = spotId;
    }

    public String getPlate() {
        return plate;
    }

    public String getGateId() {
        return gateId;
    }

    public String getSpotId() {
        return spotId;
    }


    @Override
    public String toString() {
        return "AllocationEvent{" +
                "plate='" + plate + '\'' +
                ", gateId='" + gateId + '\'' +
                ", spotId='" + spotId + '\'' +
                '}';
    }
}
