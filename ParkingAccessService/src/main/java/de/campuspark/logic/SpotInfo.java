package de.campuspark.logic;

import java.time.Instant;

import de.campuspark.model.UserProfile;
import de.campuspark.service.CalendarService;

public class SpotInfo {
    public enum State { free, reserved, occupied }

    private final String spotId;
    private State state;
    private UserProfile user;
    
    private Instant arrivalTime;
    private Instant estimatedDepartureTime;

    public SpotInfo(String spotId) {
        this.spotId = spotId;
        setFree(); 
    }

    public void reserveFor(String plate) {
        this.user = UserRegistry.findByPlate(plate);
        this.state = State.reserved;
        this.arrivalTime = null; 
        this.estimatedDepartureTime = null;
    }

    public void occupyBy(String plate) {
        this.state = State.occupied;
        if(plate == "UNKNOWN") this.user = UserRegistry.DummyUser();
        else this.user = UserRegistry.findByPlate(plate);
        this.arrivalTime = Instant.now();
        if(user != null && ( !user.getCourse().isBlank() || user.getCourse() != null || user.getRole() == "student")){
            this.estimatedDepartureTime = CalendarService.getEstimatedEndTime(user);
        }
    }

    public void setFree() {
        this.state = State.free;
        this.user = null;
        this.arrivalTime = null;
        this.estimatedDepartureTime = null;
    }

    // --- Getter für den Export ---
    public String getSpotId() { 
        return spotId; 
    }

    public State getState() { 
        return state; 
    }

    public String getAssignedPlate() {
        if (user == null || user.getPlate() == null) {
            return "UNKNOWN";
        }
        return user.getPlate();
    }

    public Instant getArrivalTime() { 
        return arrivalTime; 
    }

    public Instant getEstimatedDepartureTime() { 
        return estimatedDepartureTime; 
    }

    public String toString(){
        return "Spot " + spotId + " Status: " + state + " User: " + user.getPlate();
    }
    
}