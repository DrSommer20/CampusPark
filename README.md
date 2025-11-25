# 🚗 CampusPark – MQTT-basierte Parkplatzorganisation für den DHBW-Campus

CampusPark ist ein verteiltes, eventbasiertes IoT-System zur automatischen Erfassung und Analyse von Parkplatzbelegung auf dem DHBW-Campus.  
Im Mittelpunkt steht eine **Message-Oriented Architecture (MOA)** mit **MQTT-Broker**, vielen unabhängigen **Publishern/Subscriber-Services** sowie Edge-Devices (ESP32 + Metallsensoren & Raspberry Pi Zero).

Das System demonstriert:
- Entkopplung durch Events
- IoT-Sensorik
- Event-Streaming & Event-Processing
- Microservices
- Analyse von Zeitreihen
- Automatisierte Benachrichtigungen (SMS)
- Lightweight Webvisualisierung

---

## 🎯 Ziele des Projekts
- Echtzeit-Erfassung der Parkplatzbelegung
- Automatisierte Regelprüfung (z. B. reserved spots)
- Kennzeichenerkennung an der Einfahrt
- Benachrichtigungen bei Verstößen (SMS)
- Live-Anzeige freier Plätze (Web + Arduino Display)
- Zeitreihenanalyse & Prognosen (Analytics)
- Vollständig verteilte Architektur ohne zentralen Applikationsserver

---

## 🧩 Systemarchitektur (Übersicht)

### **MQTT im Zentrum**
Alle Komponenten kommunizieren ausschließlich über den MQTT-Broker.  
Keine direkte Kopplung – reine Event-getriebene Integration.

### **Publisher**
- **ESP32 Nodes** (Metallsensoren pro Parkplatz)  
- **Raspberry Pi Zero** (Kennzeichenerkennung)

### **Subscriber / Services**
- **Parking-State-Service** (Aggregiert Belegung, erzeugt Status-Events)  
- **Violation-Service** (Prüft Regeln & Verstöße)  
- **Notification-/SMS-Service**  
- **Logging+Analytics-Service (InfluxDB)**  
- **Web-Dashboard** (Live-Visualisierung)  
- **Arduino Display Node** (Anzeige freier Plätze)

---

## 📡 Topics (Auszug)

| Topic                             | Publisher             | Subscriber                  | Beschreibung |
|----------------------------------|------------------------|------------------------------|--------------|
| `parking/raw/spot/<id>`          | ESP32 Node            | Parking-State-Service        | Rohdaten vom Metallsensor |
| `parking/access/licensePlate`    | Raspberry Pi Zero      | Parking-State, Violation     | ALPR-Kennzeichenereignisse |
| `parking/state/spot/<id>`        | Parking-State-Service | Dashboard, Violation, Logging| Aggregierte Belegungsdaten |
| `parking/state/summary`          | Parking-State-Service | Dashboard, Display, Logging  | Freie/belegte Plätze gesamt |
| `parking/violation/<id>`         | Violation-Service     | Notification, Logging        | Parkverstöße |
| `parking/analytics/*`            | Analytics-Service     | Dashboard                    | Prognosen & Statistiken |

---

## 🛠 Verwendete Technologien

- **ESP32 + Metallsensoren** (induktiv)
- **Raspberry Pi Zero** (ALPR via OpenCV/easyOCR)
- **MQTT Broker (Mosquitto)**
- **Python / Node.js Microservices**
- **InfluxDB für Zeitreihen**
- **Web-Dashboard (HTML/JS + WebSockets)**
- **Arduino Display Node (MAX7219 / LCD)**

