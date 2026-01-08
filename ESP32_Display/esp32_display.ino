#include <WiFi.h>
#include <PubSubClient.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <Wire.h>

// OLED Configuration
#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
#define OLED_RESET    -1
#define SCREEN_ADDRESS 0x3C

// WLAN Credentials
const char* ssid          = "";
const char* password      = "";

// MQTT Configuration
const char* mqtt_server   = "192.168.4.1";
const uint16_t mqtt_port  = 1883;
const char* mqttUser      = "";
const char* mqttPassword  = "";
const char* topic_summary = "parking/state/summary";

// Objects
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);
WiFiClient espClient;
PubSubClient client(espClient);

unsigned long lastReconnectAttempt = 0;

// Helper to center text easily
void displayPrintCenter(const String &text, uint8_t textSize, int16_t y) {
  display.setTextSize(textSize);
  display.setTextColor(SSD1306_WHITE);

  int16_t x1, y1;
  uint16_t w, h;
  display.getTextBounds(text, 0, y, &x1, &y1, &w, &h);
  int16_t x = (SCREEN_WIDTH - w) / 2;

  display.setCursor(x, y);
  display.print(text);
}

// Main Display Update Logic
void updateDisplay(String value) {
  display.clearDisplay();

  // Header
  displayPrintCenter("CAMPUS PARK", 1, 4);

  // Divider Line
  display.drawFastHLine(0, 15, SCREEN_WIDTH, SSD1306_WHITE);

  // Large Number
  displayPrintCenter(value, 4, 24);

  // Footer (Using AE for stability)
  displayPrintCenter("FREIE PLAETZE", 1, 56);

  display.display();
}

void callback(char* topic, byte* payload, unsigned int length) {
  if (strcmp(topic, topic_summary) == 0) {
    // Convert raw byte payload to string
    char msg[length + 1];
    memcpy(msg, payload, length);
    msg[length] = '\0';

    updateDisplay(String(msg));
    Serial.print("Summary Updated: ");
    Serial.println(msg);
  }
}

void setupWifi() {
  delay(10);
  Serial.println("Connecting to WiFi...");
  WiFi.begin(ssid, password);

  display.clearDisplay();
  displayPrintCenter("WIFI CONNECT", 1, 28);
  display.display();

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  Serial.println("\nWiFi connected");
}

void reconnect() {
  // Create unique ID based on MAC
  uint64_t chipid = ESP.getEfuseMac();
  char clientId[30];
  sprintf(clientId, "esp32-summary-%04X%08X", (uint16_t)(chipid >> 32), (uint32_t)chipid);

  Serial.print("Attempting MQTT connection...");
  if (client.connect(clientId, mqttUser, mqttPassword)) {
    Serial.println("connected");
    client.subscribe(topic_summary);

    // Initial screen state after connection
    display.clearDisplay();
    displayPrintCenter("WARTE AUF DATEN", 1, 28);
    display.display();
  } else {
    Serial.print("failed, rc=");
    Serial.println(client.state());
  }
}

void setup() {
  Serial.begin(115200);

  // SSD1306_SWITCHCAPVCC = generate display voltage from 3.3V internally
  if(!display.begin(SSD1306_SWITCHCAPVCC, SCREEN_ADDRESS)) {
    Serial.println(F("SSD1306 allocation failed"));
    for(;;);
  }

  display.clearDisplay();
  display.display();

  setupWifi();
  client.setServer(mqtt_server, mqtt_port);
  client.setCallback(callback);
}

void loop() {
  if (!client.connected()) {
    unsigned long now = millis();
    if (now - lastReconnectAttempt > 5000) {
      lastReconnectAttempt = now;
      reconnect();
    }
  } else {
    client.loop();
  }
}
