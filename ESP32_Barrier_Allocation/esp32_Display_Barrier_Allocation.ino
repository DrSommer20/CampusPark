#include <WiFi.h>
#include <PubSubClient.h>
#include <Adafruit_GFX.h>
#include <Adafruit_ST7735.h>
#include <SPI.h>
#include <ArduinoJson.h>
#include <ESP32Servo.h>

// Pins
#define TFT_CS   2
#define TFT_DC   5
#define TFT_RST  4
#define TFT_MOSI 25
#define TFT_SCLK 18

// WLAN & MQTT
const char* ssid          = "";
const char* password      = "";
const char* mqtt_server   = "";
const uint16_t mqtt_port  = 1883;
const char* mqttUser      = "";
const char* mqttPassword  = "";

const char* topic_barrier     = "parking/access/barrier";
const char* topic_allocation  = "parking/access/allocation";

//Servo
Servo servo;
unsigned long barrierOpenMillis = 0; 
bool barrierIsModelOpen = false;  
const long barrierInterval = 10000;  
int angleClosed = 0;
int angleOpen = 100;


Adafruit_ST7735 tft = Adafruit_ST7735(TFT_CS, TFT_DC, TFT_MOSI, TFT_SCLK, TFT_RST);
WiFiClient espClient;
PubSubClient client(espClient);

void tftPrintCenter(const String &text, uint8_t textSize, uint16_t color, int16_t y) {
  tft.setTextSize(textSize);
  tft.setTextColor(color, ST77XX_BLACK);
  int16_t x1, y1;
  uint16_t w, h;
  tft.getTextBounds(text, 0, y, &x1, &y1, &w, &h);
  int16_t x = (tft.width() - w) / 2;
  tft.fillRect(0, y, tft.width(), h, ST77XX_BLACK);
  tft.setCursor(x, y);
  tft.print(text);
}

void callback(char* topic, byte* payload, unsigned int length) {
  JsonDocument doc;
  DeserializationError error = deserializeJson(doc, payload, length);
  
  if (error) return;

  tft.fillScreen(ST77XX_BLACK);
  
  const char* action = doc["action"] | ""; 

  if (strcmp(topic, topic_allocation) == 0) {
    const char* plate = doc["plate"] | "??";
    if (String(action) == "ALLOW") {
      const char* spotId = doc["spotId"] | "??";
      
      tftPrintCenter("Kennzeichen", 1, ST77XX_WHITE, 10);
      tftPrintCenter(plate, 2, ST77XX_GREEN, 30); 
      tftPrintCenter("Parkplatz:", 1, ST77XX_WHITE, 60);
      tftPrintCenter(spotId, 2, ST77XX_GREEN, 80);
    } else{
      tftPrintCenter("Kennzeichen", 1, ST77XX_WHITE, 10);
      tftPrintCenter(plate, 2, ST77XX_RED, 30); 
      tftPrintCenter("Zufahrt", 2, ST77XX_RED, 60);
      tftPrintCenter("verweigert", 2, ST77XX_RED, 60);
    }
  } 
  else if (strcmp(topic, topic_barrier) == 0) {
    if (String(action) == "OPEN") {
      servo.write(angleOpen);
      barrierOpenMillis = millis();    // Aktuelle Zeit speichern
      barrierIsModelOpen = true;
    }
  }
}


void setupWifi() {
  WiFi.begin(ssid, password);

  tftPrintCenter("Verbinde WLAN", 1, ST77XX_WHITE, 20);

  uint8_t dots = 0;
  while (WiFi.status() != WL_CONNECTED) {
    delay(400);
    dots = (dots + 1) % 4;
    String line = "Verbinde";
    for (uint8_t i = 0; i < dots; i++) line += ".";
    tftPrintCenter(line, 1, ST77XX_CYAN, 35);
  }

  tftPrintCenter("WLAN OK", 1, ST77XX_GREEN, 20);
}

void reconnect() {
  while (!client.connected()) {
    tftPrintCenter("MQTT Connect", 1, ST77XX_WHITE, 70);

    String clientId = "esp32-display-";
    clientId += String((uint32_t)ESP.getEfuseMac(), HEX);

    bool ok;
    if (strlen(mqttUser) > 0) {
      ok = client.connect(clientId.c_str(), mqttUser, mqttPassword);
    } else {
      ok = client.connect(clientId.c_str());
    }

    if (ok) {
      client.subscribe(topic_barrier);
      client.subscribe(topic_allocation);
      tftPrintCenter("MQTT OK", 1, ST77XX_GREEN, 85);
    } else {
      tftPrintCenter("MQTT FAIL", 1, ST77XX_RED, 85);
      delay(3000);
    }
  }
}


void setup() {
  delay(500);

  // TFT
  tft.initR(INITR_BLACKTAB);
  tft.setRotation(1);
  tft.fillScreen(ST77XX_BLACK);

  tftPrintCenter("Parking Display", 1, ST77XX_WHITE, 5);

  //Servo
  servo.attach(14);
  servo.write(angleClosed);

  // WLAN
  setupWifi();

  // MQTT
  client.setServer(mqtt_server, mqtt_port);
  client.setCallback(callback);

  reconnect();

  tftPrintCenter("Warte auf", 1, ST77XX_WHITE, 35);
  tftPrintCenter("MQTT-Daten", 1, ST77XX_WHITE, 50);
}

void loop() {
  if (!client.connected()) {
    reconnect();
  }
  client.loop();

  if (barrierIsModelOpen && (millis() - barrierOpenMillis >= barrierInterval)) {
    servo.write(angleClosed);
    barrierIsModelOpen = false;
  }
}