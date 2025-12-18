package de.campuspark.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Event für den Notification-Service, damit eine SMS/Push versendet werden kann.
 * Wird vom ParkingAccessService an "parking/notification/" publiziert.
 */
public class NotificationEvent {

    private final String plate;         // Kennzeichen
    private final String userId;        // interne User-ID
    private final String phoneNumber;   // Empfänger-Nummer
    private final String messageType;   // z.B. "RELOCATE", "WARNING", "INFO"
    private final String message;       // tatsächlicher Text
    private final String deadline;      // optional, z.B. "2025-11-25T11:30:00Z"

    @JsonCreator
    public NotificationEvent(
            @JsonProperty("plate") String plate,
            @JsonProperty("userId") String userId,
            @JsonProperty("phoneNumber") String phoneNumber,
            @JsonProperty("messageType") String messageType,
            @JsonProperty("message") String message,
            @JsonProperty("deadline") String deadline
    ) {
        this.plate = plate;
        this.userId = userId;
        this.phoneNumber = phoneNumber;
        this.messageType = messageType;
        this.message = message;
        this.deadline = deadline;
    }

    public String getPlate() {
        return plate;
    }

    public String getUserId() {
        return userId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getMessage() {
        return message;
    }

    public String getDeadline() {
        return deadline;
    }

    @Override
    public String toString() {
        return "NotificationEvent{" +
                "plate='" + plate + '\'' +
                ", userId='" + userId + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", messageType='" + messageType + '\'' +
                ", message='" + message + '\'' +
                ", deadline='" + deadline + '\'' +
                '}';
    }

    /**
     * Convenience factory: erstellt eine Notification für einen MoveRequest.
     */
    public static NotificationEvent fromMoveRequest(MoveRequestEvent moveReq, UserProfile userProfile) {
        return new NotificationEvent(
                moveReq.getPlate(),
                userProfile.getUserId(),
                userProfile.getPhoneNumber(),
                "RELOCATE",
                "Ihr Fahrzeug (" + moveReq.getPlate() + ") soll umgeparkt werden. Grund: " , //TODO: REASON for Relocaction
                null // optional
        );
    }
}
