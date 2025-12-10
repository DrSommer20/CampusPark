package de.campuspark.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Event zur Aktualisierung des Belegungsstatus eines einzelnen Parkplatzes.
 * Wird von den ESP32-Sensoren auf "parking/raw/spot/<spotId>" publiziert.
 */
public class SpotUpdateEvent {

    private final String spotId;
    private final String status; // 'occupied' oder 'free'
    private final long timestamp; // optional, kann aber nützlich für State Tracking sein

    /**
     * Privater Konstruktor für die Deserialisierung durch Jackson (JSON -> Objekt).
     *
     * HINWEIS: Da der Sensor-Publisher nur einen String sendet ('occupied'/'free'),
     * benötigt der Subscriber im handleSpotUpdate() Handler spezielle Logik,
     * um dieses Objekt zu instanziieren, da Jackson standardmäßig erwartet,
     * dass die gesamte JSON-Struktur in der Payload ist.
     *
     * Ein spezieller 'create' oder 'of' Methodenansatz im Handler ist hier besser.
     * Dieser Konstruktor wird hier für Konsistenz und potenzielle zukünftige
     * JSON-Payloads beibehalten.
     */
    @JsonCreator
    public SpotUpdateEvent(
            @JsonProperty("spotId") String spotId,
            @JsonProperty("status") String status,
            @JsonProperty("timestamp") long timestamp
    ) {
        this.spotId = spotId;
        this.status = status;
        this.timestamp = timestamp;
    }

    // --- Zusätzliche Hilfsmethode für Ihren aktuellen Anwendungsfall ---
    /**
     * Erstellt ein SpotUpdateEvent basierend auf dem MQTT Topic und der String-Payload.
     * @param topic Das empfangene MQTT Topic (z.B. "parking/raw/spot/A-01")
     * @param status Die empfangene String-Payload (z.B. "occupied" oder "free")
     * @return Ein SpotUpdateEvent-Objekt
     */
    public static SpotUpdateEvent of(String topic, String status) {
        // Topic muss dem Format "parking/raw/spot/<spotId>" entsprechen
        String[] parts = topic.split("/");
        String spotId = parts[parts.length - 1]; // Letzter Teil ist die Spot-ID
        long currentTimestamp = System.currentTimeMillis();

        // Optional: Validierung des Status
        if (!status.equals("occupied") && !status.equals("free")) {
            System.err.println("WARNUNG: Unerwarteter Status-Payload: " + status);
        }

        return new SpotUpdateEvent(spotId, status, currentTimestamp);
    }
    
    // --- Getter ---
    public String getSpotId() {
        return spotId;
    }

    public String getStatus() {
        return status;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isOccupied() {
        return "occupied".equalsIgnoreCase(status);
    }
    
    @Override
    public String toString() {
        return "SpotUpdateEvent{" +
                "spotId='" + spotId + '\'' +
                ", status='" + status + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}