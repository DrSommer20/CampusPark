package de.campuspark.service;

import de.campuspark.logic.*;
import de.campuspark.model.*;
import de.campuspark.util.Config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.*;

import java.nio.charset.StandardCharsets;

public class ParkingAccessCallback implements MqttCallback {

    private final MqttClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public ParkingAccessCallback(MqttClient client) {
        this.client = client;
    }

    @Override
    public void connectionLost(Throwable cause) {
        // ERROR Log an MQTT senden
        MqttLogger.error("ParkingAccess", "MQTT connection lost: " + cause.getLocalizedMessage());
        cause.printStackTrace();
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);

            // Logging des eingehenden Traffics
            // Hinweis: Wir loggen hier keine SpotID (null), da sie noch unbekannt ist
            // Optional: Wenn das zu viel Spam ist, kann man topics wie "spot/+/state" hier ausschließen.
            if (!topic.startsWith("campus/logs")) { // Logge keine Logs, sonst Endlosschleife!
                MqttLogger.info("ParkingAccess", "Received on " + topic + ": " + payload, null);
            }

            if (topic.equals(Config.TOPIC_REGISTRATION)) {
                handleRegistration(payload);

            } else if (topic.equals(Config.TOPIC_LICENSE_PLATE)) {
                handleAccess(payload);

            } else if (topic.equals(Config.TOPIC_MOVE_REQUEST)) {
                handleMoveRequest(payload);

            } 
            else if (topic.startsWith(Config.TOPIC_SPOT.replace("#",""))) {
                handleSpotUpdate(topic, payload);
            }

        } catch (Exception e) {
            MqttLogger.error("ParkingAccess", "Exception processing message: " + e.getMessage());
            e.printStackTrace(); 
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Delivery complete ist meist uninteressant für Logs
    }

    // -----------------------------
    // HANDLER-BEREICH
    // -----------------------------

    private void handleRegistration(String json) throws Exception {
        RegistrationEvent reg = mapper.readValue(json, RegistrationEvent.class);
        UserRegistry.register(reg);
        
        MqttLogger.info("ParkingAccess", "Registered new plate: " + reg.getPlate(), null);
    }

    private void handleAccess(String json) throws Exception {
        LicensePlateEvent lp = mapper.readValue(json, LicensePlateEvent.class);

        UserProfile user = UserRegistry.findByPlate(lp.getPlate());

        // 1. Unbekanntes Kennzeichen
        if (user == null) {
            MqttLogger.warn("ParkingAccess", "Access denied: Unknown plate " + lp.getPlate(), null);
            publishAllocation(lp, user, "-2", "DENY");
            return;
        }

        // 2. Parkplatz finden (z. B. freie Spots aus State-Service)
        // Hinweis: Logging passiert bereits IN SpotAllocator, aber hier loggen wir den Erfolg am Gate
        SpotInfo spot = SpotAllocator.reserveSpotForUser(user);
        
        // SpotState sofort publishen (Reserviert), damit niemand anders ihn nimmt
        publishSpot(spot);
        
        String spotId = (spot != null) ? spot.getSpotId() : null;

        if (spotId == null) {
            MqttLogger.warn("ParkingAccess", "Access denied: No free spot available for " + lp.getPlate(), null);
            publishAllocation(lp, user, "-1", "DENY");
            return;
        }

        // 3. Schranke öffnen
        publishBarrier(lp, "OPEN");

        // 4. Display-/Parking-Node informieren
        publishAllocation(lp, user, spotId, "ALLOW");

        MqttLogger.info("ParkingAccess", "Allocation successful: " + lp.getPlate() + " -> " + spotId, spotId);
    }

    private void handleMoveRequest(String json) throws Exception {
        MoveRequestEvent moveReq = mapper.readValue(json, MoveRequestEvent.class);
        UserProfile user = UserRegistry.findByPlate(moveReq.getPlate());

        if (user == null) {
             MqttLogger.warn("ParkingAccess", "Move Request failed: Unknown plate " + moveReq.getPlate(), null);
             return;
        }

        // Finde alle Autos, die diesen User blockieren
        SpotInfo[] blockers = SpotAllocator.findBlockersforPlate(user.getPlate());
        
        if (blockers.length == 0) {
            MqttLogger.info("ParkingAccess", "Move Request: No blockers found for " + user.getPlate(), null);
        }

        for(SpotInfo spot : blockers){
            NotificationEvent notif = new NotificationEvent(spot.getUser(), "Move Request", "Benutzer moechte ausparken, bitte parken Sie ihr Fahrzeug um");
            String notifJson = mapper.writeValueAsString(notif);

            client.publish(
                    Config.TOPIC_NOTIFICATION,
                    new MqttMessage(notifJson.getBytes(StandardCharsets.UTF_8))
            );
            
            MqttLogger.info("ParkingAccess", "Sent Move-Notification to blocker on " + spot.getSpotId(), spot.getSpotId());
        }
    }

    private void handleSpotUpdate(String topic, String payload) throws Exception {
        SpotUpdateEvent spotUpd = SpotUpdateEvent.of(topic, payload);
        
        // Logging passiert innerhalb von handleSensorUpdate
        SpotInfo updatedSpot = SpotAllocator.handleSensorUpdate(spotUpd.getSpotId(), spotUpd.isOccupied());
        
        publishSpot(updatedSpot);
    }

    // -----------------------------
    // PUBLISHER-Methoden
    // -----------------------------

    private void publishBarrier(LicensePlateEvent lp, String action) throws Exception {
        BarrierCommand cmd = new BarrierCommand(
                lp.getGateId(),
                lp.getPlate(),
                action
        );
        String json = mapper.writeValueAsString(cmd);

        MqttMessage message = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
        message.setQos(2);

        client.publish(
                Config.TOPIC_BARRIER,
                message
        );
    }

    private void publishAllocation(LicensePlateEvent lp, UserProfile user, String spotId, String action) throws Exception {
        AllocationEvent alloc = new AllocationEvent(
                lp.getPlate(),
                lp.getGateId(),
                spotId,
                action
        );

        String json = mapper.writeValueAsString(alloc);
        MqttMessage message = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
        message.setQos(1);

        client.publish(
                Config.TOPIC_ALLOCATION,
                message
        );
    }

    private void publishSpot(SpotInfo spot) throws Exception {
        if(spot == null) return;
        SpotStateMessage msg = new SpotStateMessage(
                spot.getSpotId(),
                spot.getState().toString(),
                spot.getAssignedPlate(),
                spot.getArrivalTime(),
                spot.getEstimatedDepartureTime()
        );
        String json = mapper.writeValueAsString(msg);

        MqttMessage message = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
        message.setQos(1);

        client.publish(
                Config.TOPIC_SPOT_STATE + spot.getSpotId(),
                message
        );

        message = new MqttMessage(SpotAllocator.getFreeSpotCount().getBytes());

        client.publish(
                Config.TOPIC_SPOT_COUNT,
                message
        );
    }
}