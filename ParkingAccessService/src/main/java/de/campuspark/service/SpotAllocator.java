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
 * <p>
 * Diese Klasse hält den aktuellen State aller Parkplätze (In-Memory) und entscheidet,
 * welcher Parkplatz einem User zugewiesen wird. Dabei werden physische Abhängigkeiten
 * (Zuparken/Stacking) sowie strategische Ziele (Kurzparker vorne) berücksichtigt.
 */
public class SpotAllocator {
    
    /**
     * Thread-safe Map aller Parkplätze, indiziert über die Spot-ID (z.B. "L1-P0").
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
     * Kernlogik der Zuweisung: Findet den optimalen Platz für einen User.
     * <p>
     * Ablauf:
     * 1. Ermittlung der Parkdauer via CalendarService.
     * 2. Filterung physikalisch nicht nutzbarer Plätze (Backfill-Regeln).
     * 3. Berechnung eines Scores (Penalty-System) für alle Kandidaten.
     * 4. Reservierung des Platzes mit dem geringsten Score.
     * * @param user Das Profil des Users (für Kalender-Check und Kennzeichen).
     * @return Das reservierte SpotInfo-Objekt oder null, wenn kein Platz verfügbar.
     */
    public static SpotInfo reserveSpotForUser(UserProfile user) {
        Instant endTime = CalendarService.getEstimatedEndTime(user);
        long durationHours = (endTime != null) ? Duration.between(Instant.now(), endTime).toHours() : 1;
        
        // Strategische Entscheidung: Ab wann gilt jemand als Langparker?
        boolean isLongTerm = durationHours > 4;

        return spots.values().stream()
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
    }

    /**
     * 2. SENSOR-LOGIK: Verarbeitet Updates vom MQTT und korrigiert Zuweisungen.
     * <p>
     * Synchronisiert den logischen Status (Software) mit dem physischen Status (Sensor).
     * Erkennt Ankünfte und Abfahrten.
     * * @param spotId Die ID des Sensors/Parkplatzes.
     * @param isSensorOccupied True, wenn ein Auto auf dem Sensor steht.
     * @return Das aktualisierte SpotInfo Objekt.
     */
    public static SpotInfo handleSensorUpdate(String spotId, boolean isSensorOccupied) {
        SpotInfo currentSpot = spots.computeIfAbsent(spotId, id -> {
            SpotInfo s = new SpotInfo(id);
            // Bei Neuerstellung (Discovery) auch einmal senden!
            return s;
        });
        
        boolean isSpotLogicallyOccupied = (currentSpot.getState() == SpotInfo.State.occupied);
        
        // Nur reagieren, wenn sich der physische Zustand vom logischen unterscheidet
        if (isSensorOccupied != isSpotLogicallyOccupied) {
                
            if (isSensorOccupied) {
                System.out.println("In isSensor Occupied car arrival");
                handleCarArrival(currentSpot);

            } else {
                handleCarDeparture(currentSpot);
            }
        }
        else{
            System.out.println("[ADMIN] Sensor status (" + isSensorOccupied + ") matches logical status. No change.");
        }

        return currentSpot;
    }

    /**
     * Verarbeitet das physische Parken eines Autos (Sensor wechselt auf belegt).
     * <p>
     * Unterscheidet zwei Fälle:
     * A) Reguläre Ankunft (Platz war reserviert).
     * B) Falschparker (Platz war frei -> Suche nach verwaister Reservierung).
     */
    private static void handleCarArrival(SpotInfo spot) {
        // Fall A: Der Parkplatz war bereits für jemanden RESERVIERT
        if (spot.getState() == SpotInfo.State.reserved) {
            String plate = spot.getAssignedPlate();
            spot.occupyBy(plate); // Status ändern zu OCCUPIED
            System.out.println("[ADMIN] Success! User " + plate + " parked correctly on " + spot.getSpotId());
        } 
        // Fall B: Der Parkplatz war FREI (Falschparker oder nicht getrackter User)
        else if (spot.getState() == SpotInfo.State.free) {
            String potentialWrongParker = findPendingUser();

            if (potentialWrongParker != null) {
                // Wir nehmen an, dass es dieser User ist, der sich verfahren hat
                System.out.println("[ADMIN] WARNING: User " + potentialWrongParker + " parked on wrong spot " + spot.getSpotId());
                freeReservationForUser(potentialWrongParker); // Alte Reservierung lösen
                spot.occupyBy(potentialWrongParker);          // Neue Position setzen
            } else {
                System.out.println("[ADMIN] WARNING: User UNKNOWN parked on wrong spot " + spot.getSpotId());
                
                spot.occupyBy("UNKNOWN");
                System.out.println(spot);
            }
        }
    }

    /**
     * Verarbeitet das Wegfahren eines Autos (Sensor wechselt auf frei).
     * Gibt den Parkplatz logisch wieder frei und löscht die User-Zuordnung.
     * * @param spot Der betroffene Parkplatz.
     */
    private static void handleCarDeparture(SpotInfo spot) {
        if (spot.getState() == SpotInfo.State.occupied) {
            System.out.println("[ADMIN] Spot " + spot.getSpotId() + " is now free. User " + spot.getAssignedPlate() + " left.");
        }
        spot.setFree();
    }

    // --- Hilfsmethoden ---

    /**
     * Sucht nach einem Kennzeichen, das aktuell irgendwo den Status RESERVED hat.
     * (Einfache Heuristik: Wenn mehrere reserviert sind, nehmen wir den ersten. 
     * In Produktion bräuchte man Zeitstempel, um den 'wahrscheinlichsten' zu finden).
     * * @return Kennzeichen des Users oder null.
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
                    System.out.println("[ADMIN] Auto-releasing reservation on " + s.getSpotId() + " (User took another spot)");
                    s.setFree();
                });
    }

    /**
     * Prüft die physikalische Auffüll-Logik (Backfill) für Stack-Lanes.
     * <p>
     * Regel: In einer Stack-Lane (Fahrgasse) darf ein Platz nur belegt werden,
     * wenn der Platz *dahinter* bereits belegt ist (oder nicht existiert).
     * Sonst würde der hintere Platz unerreichbar werden.
     */
    private static boolean obeysBackfillRules(SpotInfo spot) {
        // Regel gilt nur für Stack Lanes (definiert in Topology)
        if (!ParkingTopology.isStackLane(spot.getLane())) {
            return true;
        }

        // Suche dynamisch nach dem Platz dahinter (Position + 1)
        String spotBehindId = ParkingTopology.createSpotId(spot.getLane(), spot.getPosition() + 1);
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
     * <p>
     * Kriterien:
     * - Distanz zum Gate (Basis-Score)
     * - Parkdauer (Langparker sollen nach hinten/Stacking)
     * - Blockade-Risiko (Vermeidung von unnötigem Zuparken freier Plätze)
     */
    private static double calculateScore(SpotInfo spot, boolean isLongTerm) {
        double score = 0.0;
        int lane = spot.getLane();
        int pos = spot.getPos(); // Annahme: Methode heißt getPosition(), passend zur Logic

        // Prüfen, ob ich ein Blockierer bin (via Topology)
        boolean isBlocker = (ParkingTopology.getBlockedLane(lane) != -1);

        // Berechnungs des Distanz zum Tor (Vorne = geringer Score)
        score += pos * 10; 

        // Langzeitparker Logik:
        // Sie bekommen einen massiven Bonus für weit hinten liegende Plätze (-Score).
        // Sie bekommen eine massive Strafe, wenn sie Blockierer spielen (+Score).
        if (isLongTerm) {
            score -= (pos * 25); 
            if (isBlocker) score += 1000.0;
        }

        // Kollateralschaden-Logik:
        // Verhindert das Zuparken von Plätzen, die noch frei sind.
        // Wenn ich hier parke, wie viele freie Plätze mache ich unerreichbar?
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
}