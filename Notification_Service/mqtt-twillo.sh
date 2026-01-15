#!/bin/bash

# MQTT Settings
MQTT_HOST="localhost"
MQTT_TOPIC="parking/notification/"

# Twilio Settings
TWILIO_SID=""
TWILIO_TOKEN=""
MSG_SERVICE_SID=""
TO_NUMBER="+491624237658"

mosquitto_sub -h "$MQTT_HOST" -t "$MQTT_TOPIC" | while read -r payload
do
    echo "---------------------------------"
    echo "Raw JSON: $payload"
    
    target_phone=$(echo "$payload" | jq -r '.phoneNumber')
    msg_content=$(echo "$payload" | jq -r '.message')
    plate=$(echo "$payload" | jq -r '.plate')
    type=$(echo "$payload" | jq -r '.messageType')

    if [[ "$target_phone" == "null" || -z "$target_phone" ]]; then
        echo "Skipping: No phone number found in JSON."
        continue
    fi

    full_sms_body="[$type] $plate: $msg_content"

    echo "Sending to: $target_phone"
    echo "Message: $full_sms_body"

    curl -s -o /dev/null \
      "https://api.twilio.com/2010-04-01/Accounts/$TWILIO_SID/Messages.json" \
      -X POST \
      --data-urlencode "To=$target_phone" \
      --data-urlencode "MessagingServiceSid=$MSG_SERVICE_SID" \
      --data-urlencode "Body=$full_sms_body" \
      -u "$TWILIO_SID:$TWILIO_TOKEN"

    echo "Request sent"
done
