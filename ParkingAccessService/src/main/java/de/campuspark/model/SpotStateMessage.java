package de.campuspark.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.campuspark.service.CalendarService;

import java.time.Instant;
import java.time.ZoneId;

// JsonInclude.NON_NULL sorgt dafür, dass 'null' Felder (wie departureTime) im JSON weggelassen werden
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SpotStateMessage {

    public String spotId;
    public String state;      // FREE, RESERVED, OCCUPIED
    public String plate;      // Das Kennzeichen oder null
    public String arrivalTime; // ISO-8601 String
    public String estimatedDepartureTime;

    public SpotStateMessage() {}

    public SpotStateMessage(String spotId, String state, String plate, Instant arrival, Instant departure) {
        this.spotId = spotId;
        this.state = state;
        this.plate = plate;
        this.arrivalTime = arrival != null ? arrival.atZone(ZoneId.systemDefault()).toLocalDateTime().format(CalendarService.timeFormatter) : null;
        this.estimatedDepartureTime = departure != null ? departure.atZone(ZoneId.systemDefault()).toLocalDateTime().format(CalendarService.timeFormatter) : null;
    }
}