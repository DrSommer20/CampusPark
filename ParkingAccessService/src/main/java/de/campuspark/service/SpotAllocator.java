package de.campuspark.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.campuspark.logic.ParkingTopology;
import de.campuspark.logic.SpotInfo;
import de.campuspark.model.UserProfile;

/**
 * Zentrale Komponente für die Parkplatz-Zuweisung und Statusverwaltung.
 * Diese Klasse hält den aktuellen State aller Parkplätze (In-Memory) und entscheidet,
 * welcher Parkplatz einem User zugewiesen wird. Dabei werden physische Abhängigkeiten
 * (Zuparken/Stacking) sowie strategische Ziele (Kurzparker vorne) berücksichtigt.
 * Logging erfolgt nun strukturiert über MQTT.
 */
public class SpotAllocator {
    
    /**
     * Map aller Parkplätze, indiziert über die Spot-ID (z.B. "L1-P0").
     */
    private static final Map<String, SpotInfo> spots = new ConcurrentHashMap<>();

    /**
     * Liefert die Anzahl der aktuell freien Parkplätze zurück.
     * @return Anzahl der Spots im Status FREE.
     */
    public static String getFreeSpotCount(){
        return Long.toString(spots.values().stream()
                .filter(s -> s.getState() == SpotInfo.State.free).count());
    }
    
    /**
     * Findet den optimalen Platz für einen User.
     * Ablauf:
     * 1. Ermittlung der Parkdauer via CalendarService.
     * 2. Filterung physikalisch nicht nutzbarer Plätze (Backfill-Regeln).
     * 3. Berechnung eines Scores (Penalty-System) für alle Kandidaten.
     * 4. Reservierung des Platzes mit dem geringsten Score.
     */
    public static SpotInfo reserveSpotForUser(UserProfile user) {
        Instant endTime = CalendarService.getEstimatedEndTime(user);
        long durationHours = (endTime != null) ? Duration.between(Instant.now(), endTime).toHours() : 1;
        
        // Strategische Entscheidung: Ab wann gilt jemand als Langparker?
        boolean isLongTerm = durationHours > 4;

        SpotInfo selectedSpot = spots.values().stream()
            .filter(s -> s.getState() == SpotInfo.State.free)
            // Prüfen, ob der Platz belegt werden darf (Stacking-Regel)
            .filter(SpotAllocator::obeysBackfillRules)
            // Den Platz mit den geringsten "Schmerzen" (Kosten) finden
            .min(Comparator.comparingDouble(spot -> calculateScore(spot, isLongTerm)))
            .map(spot -> {
                spot.reserveFor(user.getPlate());
                return spot;
            })
            .orElse(null);

        if (selectedSpot != null) {
            MqttLogger.info("SpotAllocator", "Assigned spot to user " + user.getPlate(), selectedSpot.getSpotId());
        } else {
            MqttLogger.warn("SpotAllocator", "No spot available for user " + user.getPlate(), null);
        }

        return selectedSpot;
    }

    /**
     * Verarbeitet Updates vom MQTT und korrigiert Zuweisungen.
     * Synchronisiert den logischen Status (Software) mit dem physischen Status (Sensor).
     * Erkennt Ankünfte und Abfahrten.
     */
    public static SpotInfo handleSensorUpdate(String spotId, boolean isSensorOccupied) {
        SpotInfo currentSpot = spots.computeIfAbsent(spotId, id -> {
            SpotInfo s = new SpotInfo(id);
            // Bei Neuerstellung (Discovery) auch einmal loggen
            MqttLogger.info("SpotAllocator", "New spot discovered via MQTT", id);
            return s;
        });
        
        boolean isSpotLogicallyOccupied = (currentSpot.getState() == SpotInfo.State.occupied);
        
        // Nur reagieren, wenn sich der physische Zustand vom logischen unterscheidet
        if (isSensorOccupied != isSpotLogicallyOccupied) {
                
            if (isSensorOccupied) {
                MqttLogger.info("SpotAllocator", "Sensor detected car arrival", spotId);
                handleCarArrival(currentSpot);

            } else {
                handleCarDeparture(currentSpot);
            }
        }
        else{
            // Optional: Das kann viel Traffic erzeugen, evtl. auskommentieren für Produktion
            MqttLogger.info("SpotAllocator", "Sensor status matches logical status. No change.", spotId);
        }

        return currentSpot;
    }

    /**
     * Verarbeitet das physische Parken eines Autos (Sensor wechselt auf belegt).
     * Unterscheidet zwei Fälle:
     * A) Reguläre Ankunft (Platz war reserviert).
     * B) Falschparker (Platz war frei -> Suche nach ausstehender Reservierung).
     */
    private static void handleCarArrival(SpotInfo spot) {
        // Fall A: Der Parkplatz war bereits für jemanden RESERVIERT
        if (spot.getState() == SpotInfo.State.reserved) {
            String plate = spot.getAssignedPlate();
            spot.occupyBy(plate); // Status ändern zu OCCUPIED
            
            MqttLogger.info("SpotAllocator", "Success: User " + plate + " parked correctly", spot.getSpotId());
        } 
        // Fall B: Der Parkplatz war FREI (Falschparker oder nicht getrackter User)
        else if (spot.getState() == SpotInfo.State.free) {
            String potentialWrongParker = findPendingUser();

            if (potentialWrongParker != null) {
                // Wir nehmen an, dass es dieser User ist, der sich verfahren hat
                MqttLogger.warn("SpotAllocator", "User " + potentialWrongParker + " parked on wrong spot (Target was elsewhere)", spot.getSpotId());
                
                freeReservationForUser(potentialWrongParker); // Alte Reservierung lösen
                spot.occupyBy(potentialWrongParker);          // Neue Position setzen
            } else {
                MqttLogger.warn("SpotAllocator", "UNKNOWN User parked on spot (No pending reservation found)", spot.getSpotId());
                
                spot.occupyBy("UNKNOWN");
            }
        }
    }

    /**
     * Verarbeitet das Wegfahren eines Autos (Sensor wechselt auf frei).
     * Gibt den Parkplatz logisch wieder frei und löscht die User-Zuordnung.
     */
    private static void handleCarDeparture(SpotInfo spot) {
        if (spot.getState() == SpotInfo.State.occupied) {
            MqttLogger.info("SpotAllocator", "Spot is now free. User " + spot.getAssignedPlate() + " left.", spot.getSpotId());
        }
        spot.setFree();
    }

    // --- Hilfsmethoden ---

    /**
     * Sucht nach einem Kennzeichen, das aktuell irgendwo den Status RESERVED hat.
     * (Wenn mehrere reserviert sind, nehmen wir den ersten)
     */
    private static String findPendingUser() {
        return spots.values().stream()
                .filter(s -> s.getState() == SpotInfo.State.reserved)
                .map(SpotInfo::getAssignedPlate)
                .findFirst()
                .orElse(null);
    }

    /**
     * Sucht den Platz, der für diesen User reserviert war, und gibt ihn frei.
     * Wird genutzt, wenn ein User sich auf einen falschen Platz gestellt hat.
     */
    private static void freeReservationForUser(String plate) {
        spots.values().stream()
                .filter(s -> s.getState() == SpotInfo.State.reserved && plate.equals(s.getAssignedPlate()))
                .forEach(s -> {
                    MqttLogger.info("SpotAllocator", "Auto-releasing reservation (User took another spot)", s.getSpotId());
                    s.setFree();
                });
    }

    /**
     * Prüft die physikalische Auffüll-Logik (Backfill) für Stack-Lanes.
     * Regel: In einer Stack-Lane (Fahrgasse) darf ein Platz nur belegt werden,
     * wenn der Platz dahinter bereits belegt ist (oder nicht existiert).
     */
    private static boolean obeysBackfillRules(SpotInfo spot) {
        // Regel gilt nur für Stack Lanes
        if (!ParkingTopology.isStackLane(spot.getLane())) {
            return true;
        }

        // Suche dynamisch nach dem Platz dahinter (Position + 1)
        String spotBehindId = ParkingTopology.createSpotId(spot.getLane(), spot.getPos() + 1);
        SpotInfo spotBehind = spots.get(spotBehindId);

        // Wenn der Platz dahinter EXISTIERT und FREI ist -> Verboten hier zu parken.
        if (spotBehind != null && spotBehind.getState() == SpotInfo.State.free) {
            return false;
        }
        return true;
    }

    /**
     * Berechnet einen Score ("Kosten") für einen Parkplatz basierend auf Strategie.
     * Niedriger Score = Besserer Platz für diesen User.
     * Kriterien:
     * - Distanz zum Gate (Basis-Score)
     * - Parkdauer (Langparker sollen nach hinten/Stacking)
     * - Blockade-Risiko (Vermeidung von unnötigem Zuparken freier Plätze)
     */
    private static double calculateScore(SpotInfo spot, boolean isLongTerm) {
        double score = 0.0;
        int lane = spot.getLane();
        int pos = spot.getPos(); 
        
        // Prüfen, ob ich ein Blockierer bin 
        boolean isBlocker = (ParkingTopology.getBlockedLane(lane) != -1);

        // Berechnungs des Distanz zum Tor (Vorne = geringer Score)
        score += pos * 10; 

        // Langzeitparker Logik:
        // Sie bekommen einen massiven Bonus für weit hinten liegende Plätze
        // Sie bekommen eine massive Strafe, wenn sie Blockierer spielen
        if (isLongTerm) {
            score -= (pos * 25); 
            if (isBlocker) score += 1000.0;
        }

        // Kollateralschaden-Logik:
        // Verhindert das Zuparken von Plätzen, die noch frei sind
        if (isBlocker) {
            List<String> blockedIds = ParkingTopology.getBlockedSpotIds(lane, pos);
            long freeBlockedSpots = blockedIds.stream()
                .map(id -> spots.get(id)) // SpotInfo holen
                .filter(s -> s != null && s.getState() == SpotInfo.State.free) // Ist er frei?
                .count();

            // Massive Strafe pro blockiertem freien Platz
            score += (freeBlockedSpots * 1000.0);
        }

        return score;
    }

    public static SpotInfo[] findBlockersforPlate(String plate) {
        // 1. Suche den Spot des Users
        SpotInfo currentSpot = spots.values().stream()
             .filter(s -> s.getState() != SpotInfo.State.free) 
             .filter(s -> plate.equals(s.getAssignedPlate()))  
             .findFirst()
             .orElse(null);

        if (currentSpot == null) {
            return new SpotInfo[0];
        }

        // Hole theoretische Blockierer-IDs aus der Topologie
        List<String> blockingIds = ParkingTopology.getBlockingSpots(currentSpot.getLane(), currentSpot.getPos());
        
        // Hole die echten Objekte und filtere nur die BELEGTEN heraus
        return blockingIds.stream()
            .map(id -> spots.get(id)) 
            .filter(spot -> spot != null)          
            .filter(spot -> spot.getState() != SpotInfo.State.free)
            .toArray(SpotInfo[]::new);             
    }
}
