package de.campuspark.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import biweekly.Biweekly;
import biweekly.ICalendar;
import biweekly.component.VEvent;
import de.campuspark.model.UserProfile;
import de.campuspark.util.Config;

public class CalendarService {

    public static DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private static boolean isEventOnDate(VEvent event, LocalDate targetDate) {
        if (event.getDateStart() == null) return false;

        Date start = event.getDateStart().getValue();
        LocalDate eventDate = start.toInstant()
                                   .atZone(ZoneId.systemDefault())
                                   .toLocalDate();

        return eventDate.equals(targetDate);
    }

    public static Instant getEstimatedEndTime(UserProfile user){
        String courseUrl = String.format(Config.STUV_BASE_URL, user.getCourse());
        try (InputStream in = URI.create(courseUrl).toURL().openStream()) {
            ICalendar ical = Biweekly.parse(in).first();
            LocalDate today = LocalDate.now();

            return ical.getEvents().stream()
                .filter(event -> isEventOnDate(event, today))
                .filter(event -> !shouldIgnore(event))
                .map(event -> event.getDateEnd().getValue().toInstant())
                .max(Instant::compareTo)
                .orElse(Instant.now());
        
        } catch (IOException e) {
            MqttLogger.error("Calendar", "Network Error: Could not reach stuv.app for " + user.getCourse());
            return null;
        }
    }

    private static boolean shouldIgnore(VEvent event) {
        String summary = (event.getSummary() != null) ? event.getSummary().getValue().toLowerCase() : "";
        String location = (event.getLocation() != null) ? event.getLocation().getValue().toLowerCase() : "";

        return summary.toLowerCase().contains("stuv") || location.isBlank();
    }

    // Quick test main method
    public static void main(String[] args) {
        Instant time = CalendarService.getEstimatedEndTime(new UserProfile("TEST", "01", "Student", "01234", "WWI23A"));
        System.out.println(time.atZone(ZoneId.systemDefault()).toLocalDateTime().format(timeFormatter));
    }
}