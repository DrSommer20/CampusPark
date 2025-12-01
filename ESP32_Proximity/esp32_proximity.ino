#include <WiFi.h>
#include <PubSubClient.h>

// -------- WLAN-DATEN ANPASSEN --------
const char* ssid          = "";
const char* password      = "";

// -------- MQTT-DATEN ANPASSEN --------
const char* mqtt_server   = "";  
const uint16_t mqtt_port  = 1883;
const char* mqttUser      = "";
const char* mqttPassword  = ""; 

const char* mqttTopic     = "parking/raw/spot/A-01";

// -------- SENSOR --------
const int SENSOR_PIN = 27;     
const int ACTIVE_STATE = LOW;  

WiFiClient espClient;
PubSubClient client(espClient);

int lastSensorState = -1;

void setupWifi() {
  delay(10);
  WiFi.begin(ssid, password);

  while (WiFi.status() != WL_CONNECTED) {  // Wait for connection to be established
    delay(500);
  }
}

void reconnectMqtt() {
  while (!client.connected()) {    
    String clientId = "esp32-spot1-";
    clientId += String((uint32_t)ESP.getEfuseMac(), HEX);
    bool connected = false;
    connected = client.connect(clientId.c_str(), mqttUser, mqttPassword);

    if (connected) {
      Serial.println("verbunden!");
    } else {
      Serial.print("fehlgeschlagen, rc=");
      Serial.print(client.state());
      Serial.println(" -> in 5 Sekunden erneut versuchen");
      delay(5000);
    }
  }
}

void publishSensorState(bool occupied) {
  const char* payload = occupied ? "occupied" : "free";

  if (!client.publish(mqttTopic, payload, true)) { 
    Serial.println("MQTT publish fehlgeschlagen!");
  }
}

void setup() {
  Serial.begin(115200);
  delay(1000);

  pinMode(SENSOR_PIN, INPUT); 

  setupWifi();

  client.setServer(mqtt_server, mqtt_port);

  reconnectMqtt();

  int currentState = digitalRead(SENSOR_PIN);
  lastSensorState = currentState;
  bool occupied = (currentState == ACTIVE_STATE);
  publishSensorState(occupied);
}

void loop() {
  if (!client.connected()) {
    reconnectMqtt();
  }
  client.loop();

  // Sensor lesen
  int currentState = digitalRead(SENSOR_PIN);

  // Nur bei Änderung senden
  if (currentState != lastSensorState) {
    lastSensorState = currentState;

    bool occupied = (currentState == ACTIVE_STATE);

    publishSensorState(occupied);
  }

  delay(500); 
}
