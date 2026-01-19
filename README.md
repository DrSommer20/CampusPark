# CampusPark – MQTT-Based Parking Management for DHBW Campus

[🇩🇪 Deutsche Version](#deutsche-version) | [🇬🇧 English Version](#english-version)

---

## English Version

### Overview

CampusPark is a distributed, event-driven IoT system for automatic detection and analysis of parking lot occupancy on the DHBW campus. The system features a fully decoupled architecture using an MQTT broker for event-driven communication between independent publishers, subscribers, and edge devices.

### Key Features

- **Fully distributed and scalable architecture** – No central application server required
- **Event-driven design based on MQTT topics** – Complete decoupling of system components
- **Real-time monitoring** – Live parking lot status using proximity sensors
- **License Plate Recognition (ALPR)** – Automated detection handled by Raspberry Pi 3B+
- **Automated notifications** – Twilio integration for parking violations and action alerts
- **Live parking analytics and forecasts** – Time-series analysis using InfluxDB
- **Modern web-based dashboard** – Real-time visualization with TailwindCSS and JavaScript

### Hardware Setup

The system consists of:
- **1 Raspberry Pi 3B+** – Handles license plate recognition and MQTT-related tasks
- **10 proximity sensors** – Connected to 6 ESP32 devices for detecting parking spot occupancy
- **1 ESP32 device** – Manages barrier allocation and parking spot assignment
- **1 ESP32 device** – Displays available parking spaces

### System Architecture

#### **MQTT Broker at the Core**
All components communicate exclusively through the MQTT broker, ensuring complete decoupling and pure event-driven integration.

#### **Publishers**
- **ESP32 Proximity Sensors** – Detect parking spot occupancy
- **Raspberry Pi 3B+** – License plate recognition (ALPR)
- **Parking Access Service** – Aggregates occupancy data and generates status events
- **Violation Service** – Validates parking rules and detects violations
- **Web Endpoint** – Handles user registration and notifications

#### **Subscribers / Services**
- **Parking Access Service** – Processes sensor data and manages parking allocation
- **Violation Service** – Monitors rule compliance
- **Web Endpoint** – User interface and notifications
- **Notification Service** – Sends SMS alerts via Twilio
- **Analytics Service** – InfluxDB for time-series data and forecasting
- **Web Dashboard** – Real-time visualization of parking status
- **ESP32 Display** – Shows available parking spaces

### Components

The system consists of the following components:

- [**ESP32_Barrier_Allocation**](https://github.com/DrSommer20/CampusPark/tree/main/ESP32_Barrier_Allocation) – Manages barrier control and parking spot assignment
- [**ESP32_Display**](https://github.com/DrSommer20/CampusPark/tree/main/ESP32_Display) – Displays available parking spaces
- [**ESP32_Proximity**](https://github.com/DrSommer20/CampusPark/tree/main/ESP32_Proximity) – Proximity sensors for detecting parking spot occupancy
- [**Notification_Service**](https://github.com/DrSommer20/CampusPark/tree/main/Notification_Service) – Twilio-based notification system for SMS alerts
- [**ParkingAccessService**](https://github.com/DrSommer20/CampusPark/tree/main/ParkingAccessService) – Core service for parking management and state aggregation
- [**RasPi_Plate_Recognition**](https://github.com/DrSommer20/CampusPark/tree/main/RasPi_Plate_Recognition) – License plate recognition using OpenCV/easyOCR
- [**Violation_Service**](https://github.com/DrSommer20/CampusPark/tree/main/Violation_Service) – Monitors and reports parking violations
- [**Web_Endpoint**](https://github.com/DrSommer20/CampusPark/tree/main/Web_Endpoint) – Web dashboard with real-time visualization (TailwindCSS + JavaScript)

### MQTT Topics (Overview)

| Topic                             | Publisher                  | Subscriber                      | Description                          |
|-----------------------------------|----------------------------|---------------------------------|--------------------------------------|
| `parking/raw/spot/<id>`           | ESP32 Proximity            | Parking Access Service          | Raw sensor data from proximity sensor |
| `parking/access/licensePlate`     | Raspberry Pi 3B+           | Parking Access, Violation       | ALPR license plate events            |
| `parking/access/privileged`       | Parking Access Service     | Violation Service               | Privileged access events             |
| `parking/access/allocation`       | Parking Access Service     | ESP32 Barrier Allocation        | Assigned parking spot information    |
| `parking/access/barrier`          | Parking Access Service     | ESP32 Barrier Allocation        | Barrier open/close commands          |
| `parking/state/spot/<id>`         | Parking Access Service     | Dashboard, Violation, Analytics | Aggregated occupancy data per spot   |
| `parking/state/summary`           | Parking Access Service     | Dashboard, Display, Analytics   | Total available/occupied spots       |
| `parking/notification/`           | Violation, Parking Access  | Notification Service            | Notification triggers                |
| `parking/registration/plate`      | Web Endpoint               | Parking Access Service          | New license plate registration       |
| `parking/registration/smsPlate`   | Web Endpoint               | Notification Service            | License plate + phone number combo   |

### Technologies Used

- **ESP32 microcontrollers** – IoT edge devices with proximity sensors
- **Raspberry Pi 3B+** – ALPR via OpenCV/easyOCR
- **MQTT Broker (Mosquitto)** – Event-driven message bus
- **Python / Java Microservices** – Backend services
- **InfluxDB** – Time-series database for analytics and forecasting
- **TailwindCSS + JavaScript** – Modern web dashboard with real-time updates
- **Twilio** – SMS notification service

---

## Deutsche Version

### Übersicht

CampusPark ist ein verteiltes, eventbasiertes IoT-System zur automatischen Erfassung und Analyse von Parkplatzbelegung auf dem DHBW-Campus. Das System verfügt über eine vollständig entkoppelte Architektur mit einem MQTT-Broker für die ereignisgesteuerte Kommunikation zwischen unabhängigen Publishern, Subscribern und Edge-Geräten.

### Hauptfunktionen

- **Vollständig verteilte und skalierbare Architektur** – Kein zentraler Anwendungsserver erforderlich
- **Ereignisgesteuertes Design basierend auf MQTT-Topics** – Vollständige Entkopplung der Systemkomponenten
- **Echtzeit-Überwachung** – Live-Parkplatzstatus mit Näherungssensoren
- **Kennzeichenerkennung (ALPR)** – Automatisierte Erkennung durch Raspberry Pi 3B+
- **Automatisierte Benachrichtigungen** – Twilio-Integration für Parkverstöße und Aktionswarnungen
- **Live-Parkplatzanalyse und -prognosen** – Zeitreihenanalyse mit InfluxDB
- **Modernes webbasiertes Dashboard** – Echtzeitvisualisierung mit TailwindCSS und JavaScript

### Hardware-Aufbau

Das System besteht aus:
- **1 Raspberry Pi 3B+** – Verarbeitet Kennzeichenerkennung und MQTT-bezogene Aufgaben
- **10 Näherungssensoren** – Verbunden mit 6 ESP32-Geräten zur Erkennung der Parkplatzbelegung
- **1 ESP32-Gerät** – Verwaltet Schrankenzuteilung und Parkplatzzuweisung
- **1 ESP32-Gerät** – Zeigt verfügbare Parkplätze an

### Systemarchitektur

#### **MQTT-Broker im Zentrum**
Alle Komponenten kommunizieren ausschließlich über den MQTT-Broker und gewährleisten damit vollständige Entkopplung und reine ereignisgesteuerte Integration.

#### **Publisher**
- **ESP32-Näherungssensoren** – Erkennen Parkplatzbelegung
- **Raspberry Pi 3B+** – Kennzeichenerkennung (ALPR)
- **Parking Access Service** – Aggregiert Belegungsdaten und generiert Statusereignisse
- **Violation Service** – Validiert Parkregeln und erkennt Verstöße
- **Web Endpoint** – Verarbeitet Benutzerregistrierung und Benachrichtigungen

#### **Subscriber / Services**
- **Parking Access Service** – Verarbeitet Sensordaten und verwaltet Parkplatzzuweisungen
- **Violation Service** – Überwacht Regelkonformität
- **Web Endpoint** – Benutzeroberfläche und Benachrichtigungen
- **Notification Service** – Sendet SMS-Benachrichtigungen über Twilio
- **Analytics Service** – InfluxDB für Zeitreihendaten und Prognosen
- **Web Dashboard** – Echtzeitvisualisierung des Parkplatzstatus
- **ESP32 Display** – Zeigt verfügbare Parkplätze an

### Komponenten

Das System besteht aus folgenden Komponenten:

- [**ESP32_Barrier_Allocation**](https://github.com/DrSommer20/CampusPark/tree/main/ESP32_Barrier_Allocation) – Verwaltet Schrankensteuerung und Parkplatzzuweisung
- [**ESP32_Display**](https://github.com/DrSommer20/CampusPark/tree/main/ESP32_Display) – Zeigt verfügbare Parkplätze an
- [**ESP32_Proximity**](https://github.com/DrSommer20/CampusPark/tree/main/ESP32_Proximity) – Näherungssensoren zur Erkennung der Parkplatzbelegung
- [**Notification_Service**](https://github.com/DrSommer20/CampusPark/tree/main/Notification_Service) – Twilio-basiertes Benachrichtigungssystem für SMS-Warnungen
- [**ParkingAccessService**](https://github.com/DrSommer20/CampusPark/tree/main/ParkingAccessService) – Kerndienst für Parkplatzverwaltung und Statusaggregation
- [**RasPi_Plate_Recognition**](https://github.com/DrSommer20/CampusPark/tree/main/RasPi_Plate_Recognition) – Kennzeichenerkennung mit OpenCV/easyOCR
- [**Violation_Service**](https://github.com/DrSommer20/CampusPark/tree/main/Violation_Service) – Überwacht und meldet Parkverstöße
- [**Web_Endpoint**](https://github.com/DrSommer20/CampusPark/tree/main/Web_Endpoint) – Web-Dashboard mit Echtzeitvisualisierung (TailwindCSS + JavaScript)

### MQTT-Topics (Übersicht)

| Topic                             | Publisher                  | Subscriber                      | Beschreibung                              |
|-----------------------------------|----------------------------|---------------------------------|-------------------------------------------|
| `parking/raw/spot/<id>`           | ESP32 Proximity            | Parking Access Service          | Rohdaten vom Näherungssensor              |
| `parking/access/licensePlate`     | Raspberry Pi 3B+           | Parking Access, Violation       | ALPR-Kennzeichenereignisse                |
| `parking/access/privileged`       | Parking Access Service     | Violation Service               | Privilegierte Zugriffsereignisse          |
| `parking/access/allocation`       | Parking Access Service     | ESP32 Barrier Allocation        | Zugewiesene Parkplatzinformationen        |
| `parking/access/barrier`          | Parking Access Service     | ESP32 Barrier Allocation        | Schranken öffnen/schließen-Befehle        |
| `parking/state/spot/<id>`         | Parking Access Service     | Dashboard, Violation, Analytics | Aggregierte Belegungsdaten pro Platz      |
| `parking/state/summary`           | Parking Access Service     | Dashboard, Display, Analytics   | Gesamt verfügbare/belegte Plätze          |
| `parking/notification/`           | Violation, Parking Access  | Notification Service            | Benachrichtigungsauslöser                 |
| `parking/registration/plate`      | Web Endpoint               | Parking Access Service          | Neue Kennzeichenregistrierung             |
| `parking/registration/smsPlate`   | Web Endpoint               | Notification Service            | Kennzeichen + Telefonnummer-Kombination   |

### Verwendete Technologien

- **ESP32-Mikrocontroller** – IoT-Edge-Geräte mit Näherungssensoren
- **Raspberry Pi 3B+** – ALPR via OpenCV/easyOCR
- **MQTT Broker (Mosquitto)** – Ereignisgesteuerter Message-Bus
- **Python / Java Microservices** – Backend-Dienste
- **InfluxDB** – Zeitreihendatenbank für Analysen und Prognosen
- **TailwindCSS + JavaScript** – Modernes Web-Dashboard mit Echtzeit-Updates
- **Twilio** – SMS-Benachrichtigungsdienst

