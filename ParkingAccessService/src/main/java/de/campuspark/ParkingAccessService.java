package de.campuspark;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import de.campuspark.service.ParkingAccessCallback;
import de.campuspark.util.Config;

public class ParkingAccessService
{
    public static void main(String[] args) throws InterruptedException
    {
        // Broker-URL und Client-ID aus Config laden
        String brokerUrl = Config.MQTT_BROKER;
        String clientId  = Config.MQTT_CLIENT_ID;

        try {
            // MQTT Client erstellen
            MqttClient client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
        
            // Verbindungsoptionen konfigurieren
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true); // Automatische Wiederverbindung aktivieren
            options.setCleanSession(true); // Saubere Session starten
            options.setMaxInflight(100); // Maximale Anzahl gleichzeitiger Nachrichten
            options.setConnectionTimeout(30); // Timeout auf 30 Sekunden setzen
            options.setKeepAliveInterval(60); // Keep-Alive alle 60 Sekunden

            // Callback für eingehende Nachrichten setzen
            client.setCallback(new ParkingAccessCallback(client));

            // Mit Broker verbinden
            client.connect(options);
            System.out.println("Connected to Broker!");

            // Alle relevanten Topics abonnieren
            client.subscribe(Config.TOPIC_REGISTRATION);
            client.subscribe(Config.TOPIC_LICENSE_PLATE);
            client.subscribe(Config.TOPIC_MOVE_REQUEST);
            client.subscribe(Config.TOPIC_SPOT); 

        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
