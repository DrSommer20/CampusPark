#include <WiFi.h>
#include <PubSubClient.h>

// -------- CONFIGURATION CONSTANTS --------

// WLAN-DATEN
const char* ssid          = "";
const char* password      = "";

// MQTT-DATEN
const char* mqtt_server   = "";
const uint16_t mqtt_port  = 1887;
const char* mqttUser      = "";
const char* mqttPassword  = "";

// SENSOR CONFIGURATION
const float DISTANCE_THRESHOLD_CM = 20.0;

// TIME CONSTANTS
const unsigned long HOLD_TIME_MS      = 20000; 
const unsigned long HEARTBEAT_INTERVAL_MS = 60000;

// SENSOR 1
const int TRIG_PIN_1 = 14;
const int ECHO_PIN_1 = 27;
const char* MQTT_TOPIC_1 = "parking/raw/spot/A-01";
bool lastState_1 = false;                 
bool currentState_1 = false;          
unsigned long holdTimer_1 = 0;          
unsigned long heartbeatTimer_1 = 0;   

// SENSOR 2
const int TRIG_PIN_2 = 33;
const int ECHO_PIN_2 = 25;
const char* MQTT_TOPIC_2 = "parking/raw/spot/A-02";
bool lastState_2 = false;
bool currentState_2 = false;
unsigned long holdTimer_2 = 0;
unsigned long heartbeatTimer_2 = 0;

WiFiClient espClient;
PubSubClient client(espClient);


void setupWifi() {
  delay(10);
  Serial.print("Connecting to WiFi...");
  WiFi.begin(ssid, password);

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nWiFi connected.");
  Serial.print("IP Address: ");
  Serial.println(WiFi.localIP());
}

void reconnectMqtt() {
  while (!client.connected()) {
    Serial.print("Attempting MQTT connection...");
    String clientId = "esp32-spot-";
    clientId += String((uint32_t)ESP.getEfuseMac(), HEX);

    // Attempt to connect
    if (client.connect(clientId.c_str(), mqttUser, mqttPassword)) {
      Serial.println("verbunden!");
    } else {
      Serial.print("fehlgeschlagen, rc=");
      Serial.print(client.state());
      Serial.println(". in 5 Sekunden erneut versuchen");
      delay(5000);
    }
  }
}

void publishSensorState(bool occupied, const char* topic) {
  const char* payload = occupied ? "occupied" : "free";

  // ONLY FOR DEBUG / DEVELOPMENT: Retain = true: The broker keeps the last message and sends it to new subscribers.
  if (client.publish(topic, payload, true)) {
    Serial.print("MQTT published to ");
    Serial.print(topic);
    Serial.print(": ");
    Serial.println(payload);
  } else {
    Serial.println("MQTT publish fehlgeschlagen!");
  }
}

/**
 * @brief Reads the ultrasonic sensor, determines occupancy, and publishes the state if it has changed.
 *
 * @param trigPin The ESP32 pin connected to the TRIG pin of the sensor.
 * @param echoPin The ESP32 pin connected to the ECHO pin of the sensor.
 * @param lastState Reference to the variable storing the previous occupancy state.
 * @param topic The MQTT topic to publish to.
 */
void updateSensorState(int trigPin, int echoPin, const char* topic, 
                       bool& lastState, bool& currentState, unsigned long& holdTimer, unsigned long& heartbeatTimer) {
  
  //MESSUNG
  digitalWrite(trigPin, LOW);
  delayMicroseconds(2);
  digitalWrite(trigPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(trigPin, LOW);

  float duration = pulseIn(echoPin, HIGH);
  float distance = (duration * 0.0343) / 2;

  Serial.print("Sensor "); Serial.print(topic); Serial.print(" Distance: "); Serial.print(distance); Serial.println(" cm");

  bool sensorReading = (distance < DISTANCE_THRESHOLD_CM);
  unsigned long currentTime = millis();

  //Pulish-Delay => Publish only if Sensor changes for a fixed period of time
  if (sensorReading != currentState) {
    currentState = sensorReading;
    // Start Timer
    holdTimer = currentTime;
    Serial.print("NEU! "); Serial.print(topic); Serial.print(" -> "); Serial.println(currentState ? "occupied" : "free");

  } else if (currentState != lastState) {
    // State differs from the last pulished State
    if (currentTime - holdTimer >= HOLD_TIME_MS) {
      publishSensorState(currentState, topic);
      lastState = currentState; 
      heartbeatTimer = currentTime; 
    }
  }

  //HEARTBEAT => Sends State of Sensor after a fixed Time Period
  if (currentTime - heartbeatTimer >= HEARTBEAT_INTERVAL_MS) {
    publishSensorState(lastState, topic);
    heartbeatTimer = currentTime;
  }
}


void setup() {
  Serial.begin(9600);
  delay(1000);

  pinMode(TRIG_PIN_1, OUTPUT);
  pinMode(ECHO_PIN_1, INPUT);
  pinMode(TRIG_PIN_2, OUTPUT);
  pinMode(ECHO_PIN_2, INPUT);

  setupWifi();
  client.setServer(mqtt_server, mqtt_port);
  reconnectMqtt();
}

void loop() {
  if (!client.connected()) {
    reconnectMqtt();
  }
  client.loop();

  updateSensorState(TRIG_PIN_1, ECHO_PIN_1, MQTT_TOPIC_1, 
                    lastState_1, currentState_1, holdTimer_1, heartbeatTimer_1);

  updateSensorState(TRIG_PIN_2, ECHO_PIN_2, MQTT_TOPIC_2, 
                    lastState_2, currentState_2, holdTimer_2, heartbeatTimer_2);

  delay(100);
}