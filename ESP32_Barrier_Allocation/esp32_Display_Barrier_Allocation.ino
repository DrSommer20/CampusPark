#include <WiFi.h>
#include <PubSubClient.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <Wire.h>
#include <ESP32Servo.h>
#include <ArduinoJson.h>

// Servo Config
Servo barrierServo;
int servoPin = 13;
const uint16_t open_angle = 180;
const uint16_t close_angle = 75;

// OLED Config
#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
#define OLED_RESET    -1
#define SCREEN_ADDRESS 0x3C

// WIFI-DATEN
const char* ssid          = "";
const char* password      = "";

// MQTT-DATEN
const char* mqtt_server   = "192.168.4.1";
const uint16_t mqtt_port  = 1883;
const char* mqttUser      = "";
const char* mqttPassword  = "";
const char* topic_display = "parking/access/allocation";
const char* topic_barrier = "parking/access/barrier";

//Creation of Objects for WIFI, OLED and MQTT
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);
WiFiClient espClient;
PubSubClient client(espClient);

// Timout after Display or Open Command
unsigned long lastReconnectAttempt = 0;
unsigned long actionTimer = 0;
bool isActive = false;
const unsigned long timeout = 10000;

// Helper function to center text on OLED
void displayPrintCenter(const String &text, uint8_t textSize, int16_t y) {
  display.setTextSize(textSize);
  display.setTextColor(SSD1306_WHITE); // OLED is monochrome

  int16_t x1, y1;
  uint16_t w, h;
  display.getTextBounds(text, 0, y, &x1, &y1, &w, &h);
  int16_t x = (SCREEN_WIDTH - w) / 2;

  display.setCursor(x, y);
  display.print(text);
  display.display();
}

//Helper funktion to Display Message when no MQTT-Message is displayed
void showWelcomeScreen() {
  closeGate();
  display.clearDisplay();
  displayPrintCenter("Willkommen!", 1, 15);
  displayPrintCenter("CampusPark", 2, 35);
  display.display();
  isActive = false;
}

//Funktion to move the Servo to open the Gate
void openGate(){
  barrierServo.write(open_angle);
}

//Funktion to move the Servo to close the Gate
void closeGate(){
  barrierServo.write(close_angle);
}

//Function to define the Process, when a MQTT Message os recieved
void callback(char* topic, byte* payload, unsigned int length) {
  StaticJsonDocument<256> doc;
  DeserializationError error = deserializeJson(doc, payload, length);
  if (error) return;

  // Start the timer and set state to active
  actionTimer = millis();
  isActive = true;

  if (strcmp(topic, topic_display) == 0) {
    const char* spotId = doc["spotId"];
    display.clearDisplay();
    displayPrintCenter("Ihr Parkplatz:", 1, 10);
    if (spotId) {
      displayPrintCenter(String(spotId), 2, 35);
    }
  }
  else if (strcmp(topic, topic_barrier) == 0) {
    const char* action = doc["action"];
    if (action != NULL) {
      if (strcmp(action, "OPEN") == 0) {
        openGate();
      }
    }
  }
}

void setupWifi() {
  WiFi.begin(ssid, password);
  display.clearDisplay();
  displayPrintCenter("WiFi Connect", 1, 20);

  uint8_t dots = 0;
  while (WiFi.status() != WL_CONNECTED) {
    delay(400);
    dots = (dots + 1) % 4;
    String line = "Verbinde";
    for (uint8_t i = 0; i < dots; i++) line += ".";
    display.clearDisplay();
    displayPrintCenter(line, 1, 35);
  }

  display.clearDisplay();
  displayPrintCenter("WiFi OK", 1, 25);
}

void reconnect() {
  while (!client.connected()) {
    display.clearDisplay();
    displayPrintCenter("MQTT Connect", 1, 25);

    String clientId = "esp32-display-";
    clientId += String((uint32_t)ESP.getEfuseMac(), HEX);

    bool ok = (strlen(mqttUser) > 0)
    ? client.connect(clientId.c_str(), mqttUser, mqttPassword)
    : client.connect(clientId.c_str());

    if (ok) {
      client.subscribe(topic_display);
      client.subscribe(topic_barrier);
      display.clearDisplay();
      displayPrintCenter("MQTT OK", 1, 25);
      delay(500);
    } else {
      displayPrintCenter("MQTT FAIL", 1, 45);
      delay(3000);
    }
  }
}

void setup() {
  Serial.begin(115200);

  // Initialize OLED
  if(!display.begin(SSD1306_SWITCHCAPVCC, SCREEN_ADDRESS)) {
    Serial.println(F("SSD1306 allocation failed"));
    for(;;); // Don't proceed, loop forever
  }

  display.clearDisplay();
  display.display();

  displayPrintCenter("Parking System", 1, 5);

  barrierServo.attach(servoPin);

  delay(1000);

  openGate();
  delay(500);
  closeGate();

  // WiFi & MQTT
  setupWifi();
  client.setServer(mqtt_server, mqtt_port);
  client.setCallback(callback);

  reconnect();
  showWelcomeScreen();
}

void loop() {
  // Reconnection logic
  if (!client.connected()) {
    unsigned long now = millis();
    if (now - lastReconnectAttempt > 5000) {
      lastReconnectAttempt = now;
      reconnect();
    }
  } else {
    client.loop();
  }

  // --- TIMEOUT & PROGRESS BAR LOGIC ---
  if (isActive) {
    unsigned long elapsed = millis() - actionTimer;

    if (elapsed >= timeout) {
      showWelcomeScreen();
    } else {
      // Calculate progress bar width (128 pixels total)
      // We map the remaining time (timeout - elapsed) to the screen width
      int barWidth = map(timeout - elapsed, 0, timeout, 0, SCREEN_WIDTH);

      // Draw the bar at the bottom (Y = 60, Height = 4)
      display.fillRect(0, 60, SCREEN_WIDTH, 4, SSD1306_BLACK); // Clear old bar area
      display.fillRect(0, 60, barWidth, 4, SSD1306_WHITE);     // Draw new progress
      display.display();
    }
  }

  delay(10);
}
