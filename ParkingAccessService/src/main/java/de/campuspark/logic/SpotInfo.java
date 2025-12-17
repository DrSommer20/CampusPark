package de.campuspark.logic;

import java.time.Instant;

public class SpotInfo {
    public enum State { free, reserved, occupied }

    private final String spotId;
    private State state;
    private String assignedPlate;
    
    private Instant arrivalTime;
    private Instant estimatedDepartureTime; // Placeholder für Ihre Kursplan-Logik

    public SpotInfo(String spotId) {
        this.spotId = spotId;
        setFree(); // Initialzustand
    }

    public void reserveFor(String plate) {
        this.state = State.reserved;
        this.assignedPlate = plate;
        // Bei Reservierung gibt es noch keine Ankunftszeit
        this.arrivalTime = null; 
    }

    public void occupyBy(String plate) {
        // Nur wenn wir nicht schon occupied waren, setzen wir die Ankunftszeit neu
        // (verhindert Updates bei Sensor-Flackern)
        if (this.state != State.occupied) {
            this.arrivalTime = Instant.now();
        }
        this.state = State.occupied;
        this.assignedPlate = plate;
    }

    public void setFree() {
        this.state = State.free;
        this.assignedPlate = null;
        this.arrivalTime = null;
        this.estimatedDepartureTime = null;
    }

    // --- Getter für den Export ---
    public String getSpotId() { return spotId; }
    public State getState() { return state; }
    public String getAssignedPlate() { return assignedPlate; }
    public Instant getArrivalTime() { return arrivalTime; }
    public Instant getEstimatedDepartureTime() { return estimatedDepartureTime; }
    
    // TODO: Setter für EstimatedDepartureTime hinzufügen, sobald der Kursplan angebunden ist
}