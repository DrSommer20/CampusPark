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
        String brokerUrl = Config.MQTT_BROKER;
        String clientId  = Config.MQTT_CLIENT_ID;

        MqttClient client;

        try {
            client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
            
            client.setCallback(new ParkingAccessCallback(client)); 

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setUserName(Config.MQTT_USERNAME);
            options.setPassword(Config.MQTT_PASSWORD.toCharArray());

            System.out.println("Versuche Verbindung zum Broker herzustellen...");
            client.connect(options); 
            System.out.println("Verbunden.");

            client.subscribe(Config.TOPIC_REGISTRATION, 1);
            client.subscribe(Config.TOPIC_LICENSE_PLATE, 1);
            client.subscribe(Config.TOPIC_MOVE_REQUEST, 1);
            System.out.println("Erfolgreich Abonnements erstellt.");

            System.out.println("Service ist bereit und wartet auf Nachrichten...");
            while (client.isConnected()) { 
                 Thread.sleep(1000); // 1 Sekunde warten, um CPU-Auslastung zu reduzieren
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }

    }
}
