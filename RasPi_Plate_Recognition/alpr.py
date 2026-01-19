import cv2
import subprocess
import os
import time
import json
import paho.mqtt.client as mqtt
from paho.mqtt.client import CallbackAPIVersion
from threading import Thread

# --- CONFIG FROM ENV ---
MQTT_BROKER = "localhost"
MQTT_TOPIC  = "parking/access/licensePlate"
MQTT_USER   = ""
MQTT_PASS   = ""
MOTION_THRESHOLD = 500
TEMP_FILE = "/dev/shm/plate_capture.jpg"

# --- MQTT SETUP ---
client = mqtt.Client(CallbackAPIVersion.VERSION2,
                     client_id="alpr-client")
client.username_pw_set(MQTT_USER, MQTT_PASS)

def on_connect(client, userdata, flags, reason_code, properties):
  print(f"Connected: reason_code={reason_code}, properties={properties}")

client.on_connect = on_connect
client.connect(MQTT_BROKER, 1883, 60)
client.loop_start()

latest_result = {"plate": "Waiting...", "timestamp": "-", "status": "Idle"}

def lpr_worker():
    global latest_result
    avg = None
    print("🚀 LPR Worker Started...")
    while True:
        # Capture with flip settings
        subprocess.run([
            "rpicam-still", "-t", "1", "-o", TEMP_FILE,  
            "--width", "640", "--height", "480", "--immediate", "--nopreview", 
            "--vflip", "--hflip"
        ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

        if os.path.exists(TEMP_FILE):
            frame = cv2.imread(TEMP_FILE)
            if frame is None: continue

            gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
            gray = cv2.GaussianBlur(gray, (21, 21), 0)

            if avg is None:
                avg = gray.copy().astype("float")
                continue

            cv2.accumulateWeighted(gray, avg, 0.5)
            frameDelta = cv2.absdiff(gray, cv2.convertScaleAbs(avg))
            thresh = cv2.threshold(frameDelta, 25, 255, cv2.THRESH_BINARY)[1]
            movement = cv2.countNonZero(thresh)

            if movement > MOTION_THRESHOLD:
                result = subprocess.run(
                    ["alpr", "-c", "eu", "-j", TEMP_FILE],
                    capture_output=True, text=True
                )
                try:
                    data = json.loads(result.stdout)
                    if data['results']:
                        best = data['results'][0]
                        plate = best['plate']
                        conf = best['confidence']
                        
                        if conf > 75:
                            timestamp = time.strftime("%H:%M:%S")
                            client.publish(MQTT_TOPIC, plate)
                            print(f"Published: {plate} ({conf}%)")
                            time.sleep(5) 
                except:
                    pass
        time.sleep(0.1)

if __name__ == "__main__":
    lpr_worker()
