package de.campuspark.logic;

import de.campuspark.model.SpotStateMessage;
import de.campuspark.model.UserProfile;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class SpotAllocator {
    
    private static final Map<String, SpotInfo> spots = new ConcurrentHashMap<>();
    
    // Ein Listener (Callback), den wir aufrufen, wenn sich was ändert.
    // Der Consumer bekommt das fertige DTO (SpotStateMessage).
    private static Consumer<SpotStateMessage> stateChangeListener;

    // Setup-Methode, um den MQTT-Publisher zu registrieren
    public static void setOnStateChange(Consumer<SpotStateMessage> listener) {
        stateChangeListener = listener;
    }

    // --- Private Hilfsmethode zum Feuern des Events ---
    private static void notifyChange(SpotInfo spot) {
        if (stateChangeListener != null) {
            SpotStateMessage msg = new SpotStateMessage(
                spot.getSpotId(),
                spot.getState().toString(),
                spot.getAssignedPlate(),
                spot.getArrivalTime(),
                spot.getEstimatedDepartureTime()
            );
            stateChangeListener.accept(msg);
        }
    }

    /**
     * 1. GATE-LOGIK: Findet freien Platz und reserviert ihn sofort.
     */
    public static String reserveSpotForUser(UserProfile user) {
        // Suche ersten Platz mit Status FREE
        Optional<SpotInfo> freeSpot = spots.values().stream()
                .filter(s -> s.getState() == SpotInfo.State.FREE)
                .findFirst();

        if (freeSpot.isPresent()) {
            SpotInfo spot = freeSpot.get();
            spot.reserveFor(user.getPlate());
            notifyChange(spot);
            return spot.getSpotId();
        }
        return null; // Kein Platz frei
    }

    /**
     * 2. SENSOR-LOGIK: Verarbeitet Updates vom MQTT und korrigiert Zuweisungen.
     */
    public static void handleSensorUpdate(String spotId, boolean isSensorOccupied) {
        SpotInfo currentSpot = spots.computeIfAbsent(spotId, id -> {
            SpotInfo s = new SpotInfo(id);
            // Bei Neuerstellung (Discovery) auch einmal senden!
            notifyChange(s); 
            return s;
        });
        
        // Speichern des alten Zustands, um unnötige Publishes zu vermeiden
        SpotInfo.State oldState = currentSpot.getState();
        String oldPlate = currentSpot.getAssignedPlate();

        if (isSensorOccupied) {
            handleCarArrival(currentSpot);
        } else {
            handleCarDeparture(currentSpot);
        }

        // Checken ob sich wirklich was logisch geändert hat
        if (currentSpot.getState() != oldState || 
           (currentSpot.getAssignedPlate() != null && !currentSpot.getAssignedPlate().equals(oldPlate))) {
            
            notifyChange(currentSpot);
        }
    }

    private static void handleCarArrival(SpotInfo spot) {
        // Fall A: Der Parkplatz war bereits für jemanden RESERVIERT
        if (spot.getState() == SpotInfo.State.RESERVED) {
            String plate = spot.getAssignedPlate();
            spot.occupyBy(plate); // Status ändern zu OCCUPIED
            System.out.println("[MANAGER] Success! User " + plate + " parked correctly on " + spot.getSpotId());
        } 
        // Fall B: Der Parkplatz war FREI (Falschparker oder nicht getrackter User)
        else if (spot.getState() == SpotInfo.State.FREE) {
            // Wir müssen herausfinden, wer das sein könnte.
            // Wir suchen nach einem User, der eine Reservierung hat, aber noch nicht geparkt hat.
            String potentialWrongParker = findPendingUser();

            if (potentialWrongParker != null) {
                // Wir nehmen an, dass es dieser User ist
                System.out.println("[MANAGER] WARNING: User " + potentialWrongParker + " parked on wrong spot " + spot.getSpotId());
                
                // 1. Alten reservierten Platz finden und freigeben
                freeReservationForUser(potentialWrongParker);

                // 2. Diesen neuen Platz dem User zuweisen
                spot.occupyBy(potentialWrongParker);
            } else {
                // Niemand erwartet -> Es ist ein unbekanntes Fremdfahrzeug
                spot.occupyBy("UNKNOWN");
                //TODO: Benachritigung auslösen an Admin
            }
        }
        // Fall C: Bereits OCCUPIED (Sensor flattert oder Fehler) -> Ignorieren
    }

    private static void handleCarDeparture(SpotInfo spot) {
        // Einfach: Wenn Sensor frei meldet, ist der Platz frei.
        // Die Zuordnung zum User wird gelöscht.
        if (spot.getState() == SpotInfo.State.OCCUPIED) {
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
                .filter(s -> s.getState() == SpotInfo.State.RESERVED)
                .map(SpotInfo::getAssignedPlate)
                .findFirst()
                .orElse(null);
    }

    /**
     * Sucht den Platz, der für diesen User reserviert war, und gibt ihn frei.
     */
    private static void freeReservationForUser(String plate) {
        spots.values().stream()
                .filter(s -> s.getState() == SpotInfo.State.RESERVED && plate.equals(s.getAssignedPlate()))
                .forEach(s -> {
                    System.out.println("[MANAGER] Auto-releasing reservation on " + s.getSpotId() + " (User took another spot)");
                    s.setFree();
                });
    }
    
    // Debug Methode
    public static void printStatus() {
        System.out.println("--- PARK STATUS ---");
        spots.forEach((k, v) -> System.out.println(k + ": " + v.getState() + " [" + v.getAssignedPlate() + "]"));
        System.out.println("-------------------");
    }
}