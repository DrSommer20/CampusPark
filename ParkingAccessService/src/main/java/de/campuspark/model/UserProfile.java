package de.campuspark.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Internes Modell für registrierte Nutzer.
 * Wird von UserRegistry verwaltet.
 */
public class UserProfile {

    private final String plate;          // Kennzeichen
    private final String userId;         // interne ID
    private final String role;           // Rolle des Nutzers
    private final String phoneNumber;    // Nummer für SMS
    private final String course;         // Kurs im Style "WWI23A"

    @JsonCreator
    public UserProfile(
            @JsonProperty("plate") String plate,
            @JsonProperty("userId") String userId,
            @JsonProperty("role") String role,
            @JsonProperty("phoneNumber") String phoneNumber,
            @JsonProperty("course") String course
    ) {
        this.plate = plate;
        this.userId = userId;
        this.role = role;
        this.phoneNumber = phoneNumber;
        this.course = course;
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

    public String getCourse(){
        return course;
    }

    @Override
    public String toString() {
        return "UserProfile{" +
                "plate='" + plate + '\'' +
                ", userId='" + userId + '\'' +
                ", role='" + role + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                '}';
    }
}
