package de.campuspark;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;

import de.campuspark.logic.SpotAllocator;
import de.campuspark.service.ParkingAccessCallback;
import de.campuspark.util.Config;

import java.nio.charset.StandardCharsets;

public class ParkingAccessService
{
    public static void main( String[] args )
    {
        String brokerUrl = Config.MQTT_BROKER;
        String clientId  = Config.MQTT_CLIENT_ID;

        // Jackson Mapper für die State-Messages vorbereiten
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule()); // Für Zeitstempel
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        try {
            MqttClient client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
            
            // ------------------------------------------------------------
            // 1. STATE-LISTENER VERDRAHTEN
            // Bevor wir verbinden, sagen wir dem Manager, was er bei Änderungen tun soll.
            // ------------------------------------------------------------
            SpotAllocator.setOnStateChange(spotStateMsg -> {
                try {
                    // Topic bauen: "parking/state/spot/A-01"
                    String topic = Config.TOPIC_SPOT_STATE + spotStateMsg.spotId;
                    
                    // JSON erzeugen
                    String jsonPayload = mapper.writeValueAsString(spotStateMsg);
                    
                    // Nachricht erstellen (Retained = true ist hier wichtig!)
                    MqttMessage msg = new MqttMessage(jsonPayload.getBytes(StandardCharsets.UTF_8));
                    msg.setRetained(true);
                    
                    // Senden (nur wenn Client verbunden ist)
                    if(client.isConnected()) {
                        client.publish(topic, msg);
                        System.out.println("[STATE OUT] " + topic + " -> " + spotStateMsg.state);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            // ------------------------------------------------------------
            // 2. MQTT SETUP
            // ------------------------------------------------------------
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setUserName(Config.MQTT_USERNAME);
            options.setPassword(Config.MQTT_PASSWORD.toCharArray());

            // Callback setzen
            client.setCallback(new ParkingAccessCallback(client));

            client.connect(options);
            System.out.println("Connected to Broker!");

            // ------------------------------------------------------------
            // 3. SUBSCRIPTIONS
            // ------------------------------------------------------------
            client.subscribe(Config.TOPIC_REGISTRATION);
            client.subscribe(Config.TOPIC_LICENSE_PLATE);
            client.subscribe(Config.TOPIC_MOVE_REQUEST);
            client.subscribe(Config.TOPIC_SPOT); 

        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}