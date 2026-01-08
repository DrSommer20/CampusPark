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
        System.err.println("[ERROR] MQTT connection lost: " + cause.getLocalizedMessage());
        cause.printStackTrace();
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {


        try {
        
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        // Debug Log etwas gekürzt, damit bei vielen Sensoren die Konsole nicht explodiert
        //if(!topic.startsWith(Config.TOPIC_SPOT.replace("#", ""))) {
            System.out.println("[MQTT] Received on " + topic + ": " + payload);
        //}

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
        e.printStackTrace(); // This will show you the real killer
    }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    // -----------------------------
    // HANDLER-BEREICH
    // -----------------------------

    private void handleRegistration(String json) throws Exception {
        RegistrationEvent reg = mapper.readValue(json, RegistrationEvent.class);
        UserRegistry.register(reg);
        System.out.println("[ACCESS] Registered new plate: " + reg.getPlate());
    }

    private void handleAccess(String json) throws Exception {
        LicensePlateEvent lp = mapper.readValue(json, LicensePlateEvent.class);

        UserProfile user = UserRegistry.findByPlate(lp.getPlate());

        // 1. Unbekanntes Kennzeichen
        if (user == null) {
            System.out.println("[ACCESS] Unknown plate: " + lp.getPlate());
            publishAllocation(lp, user, "-2", "DENY");
            return;
        }

        // 2. Parkplatz finden (z. B. freie Spots aus State-Service)
        SpotInfo spot = SpotAllocator.reserveSpotForUser(user);
        publishSpot(spot);
        String spotId = spot.getSpotId();

        if (spotId == null) {
            System.out.println("[ACCESS] No free spot available for " + lp.getPlate());
            publishAllocation(lp, user, "-1", "DENY");
            return;
        }

        // 3. Schranke öffnen
        publishBarrier(lp, "OPEN");

        // 4. Display-/Parking-Node informieren
        publishAllocation(lp, user, spotId, "ALLOW");

        System.out.println("[ACCESS] Allocation OK: " + lp.getPlate() + " -> " + spotId);
    }

    private void handleMoveRequest(String json) throws Exception {
        MoveRequestEvent moveReq = mapper.readValue(json, MoveRequestEvent.class);
        UserProfile user = UserRegistry.findByPlate(moveReq.getPlate());

        if (user == null) {
             System.out.println("[MOVE] Request for unknown plate: " + moveReq.getPlate());
             return;
        }

        NotificationEvent notif = NotificationEvent.fromMoveRequest(moveReq, user);
        String notifJson = mapper.writeValueAsString(notif);

        client.publish(
                Config.TOPIC_NOTIFICATION,
                new MqttMessage(notifJson.getBytes(StandardCharsets.UTF_8))
        );
    }

    private void handleSpotUpdate(String topic, String payload) throws Exception {
        SpotUpdateEvent spotUpd = SpotUpdateEvent.of(topic, payload);
        publishSpot(SpotAllocator.handleSensorUpdate(spotUpd.getSpotId(), spotUpd.isOccupied()));
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