package de.campuspark.service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import de.campuspark.logic.SpotInfo;
import de.campuspark.model.UserProfile;

public class SpotAllocator {
    
    private static final Map<String, SpotInfo> spots = new ConcurrentHashMap<>();

    public static String getFreeSpotCount(){
        return Long.toString(spots.values().stream()
                .filter(s -> s.getState() == SpotInfo.State.free).count());
    }
    public static SpotInfo reserveSpotForUser(UserProfile user) {
        // Suche ersten Platz mit Status FREE
        Optional<SpotInfo> freeSpot = spots.values().stream()
                .filter(s -> s.getState() == SpotInfo.State.free)
                .findFirst();

        if (freeSpot.isPresent()) {
            SpotInfo spot = freeSpot.get();
            spot.reserveFor(user.getPlate());
            return spot;
        }
        return null; // Kein Platz frei
    }

    /**
     * 2. SENSOR-LOGIK: Verarbeitet Updates vom MQTT und korrigiert Zuweisungen.
     */
    public static SpotInfo handleSensorUpdate(String spotId, boolean isSensorOccupied) {
        SpotInfo currentSpot = spots.computeIfAbsent(spotId, id -> {
            SpotInfo s = new SpotInfo(id);
            // Bei Neuerstellung (Discovery) auch einmal senden!
            return s;
        });
        
        boolean isSpotLogicallyOccupied = (currentSpot.getState() == SpotInfo.State.occupied);
        if (isSensorOccupied != isSpotLogicallyOccupied) {
                
            if (isSensorOccupied) {
                System.out.println("In isSensor Occupied car arrival");
                handleCarArrival(currentSpot);

            } else {
                handleCarDeparture(currentSpot);
            }
        }
        else{
            System.out.println("[MANAGER] Sensor status (" + isSensorOccupied + ") matches logical status. No change.");
        }

        return currentSpot;
    }

    private static void handleCarArrival(SpotInfo spot) {
        // Fall A: Der Parkplatz war bereits für jemanden RESERVIERT
        if (spot.getState() == SpotInfo.State.reserved) {
            String plate = spot.getAssignedPlate();
            spot.occupyBy(plate); // Status ändern zu OCCUPIED
            System.out.println("[MANAGER] Success! User " + plate + " parked correctly on " + spot.getSpotId());
        } 
        // Fall B: Der Parkplatz war FREI (Falschparker oder nicht getrackter User)
        else if (spot.getState() == SpotInfo.State.free) {
            String potentialWrongParker = findPendingUser();

            if (potentialWrongParker != null) {
                // Wir nehmen an, dass es dieser User ist
                System.out.println("[MANAGER] WARNING: User " + potentialWrongParker + " parked on wrong spot " + spot.getSpotId());
                freeReservationForUser(potentialWrongParker);
                spot.occupyBy(potentialWrongParker);
            } else {
                System.out.println("[MANAGER] WARNING: User UNKNOWN parked on wrong spot " + spot.getSpotId());
                
                spot.occupyBy("UNKNOWN");
                System.out.println(spot);
            }
        }
    }

    private static void handleCarDeparture(SpotInfo spot) {
        // Einfach: Wenn Sensor frei meldet, ist der Platz frei.
        // Die Zuordnung zum User wird gelöscht.
        if (spot.getState() == SpotInfo.State.occupied) {
            System.out.println("[MANAGER] Spot " + spot.getSpotId() + " is now free. User " + spot.getAssignedPlate() + " left.");
        }
        spot.setFree();
    }

    // --- Hilfsmethoden ---

    /**
     * Sucht nach einem Kennzeichen, das aktuell irgendwo den Status RESERVED hat.
     * (Einfache Heuristik: Wenn mehrere reserviert sind, nehmen wir den ersten. 
     * In Produktion bräuchte man Zeitstempel, um den 'wahrscheinlichsten' zu finden).
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
     */
    private static void freeReservationForUser(String plate) {
        spots.values().stream()
                .filter(s -> s.getState() == SpotInfo.State.reserved && plate.equals(s.getAssignedPlate()))
                .forEach(s -> {
                    System.out.println("[MANAGER] Auto-releasing reservation on " + s.getSpotId() + " (User took another spot)");
                    s.setFree();
                });
    }
}