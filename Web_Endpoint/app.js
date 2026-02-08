/**
 * SmartPark MQTT Dashboard Logic
 * Updated for Specific JSON API
 */

// --- CONFIGURATION ---
const MQTT_CONFIG = {
    host: '192.168.0.100', 
    // Topics
    topic_spot_state: 'parking/state/spot',  // + /#
    topic_summary: 'parking/state/summary',
    topic_move: 'parking/move/request',
    topic_barrier: 'parking/access/barrier',
    topic_registration: 'parking/registration/plate'
};

const availableSpot = 10;

// Global App State
let mqttClient = null;


// --- Initialization ---

function init() {
    connectMQTT();
}

function toggleRegModal() {
    document.getElementById('reg-modal').classList.toggle('hidden');
}

// --- Connection Logic ---

function connectMQTT() {
    updateConnectionUI('connecting');
    logEvent("System: Establishing secure link...");

    try {
        const port = 9001; 
        
        const clientId = "sp_web_" + Math.random().toString(16).substr(2, 6);
        
        mqttClient = new Paho.MQTT.Client(MQTT_CONFIG.host, port, clientId);
        mqttClient.onConnectionLost = onConnectionLost;
        mqttClient.onMessageArrived = onMessageArrived;

        mqttClient.connect({
            onSuccess: onConnectSuccess,
            onFailure: onConnectFailure,
            //reconnect: true
        });

    } catch (e) {
        console.error(e); // Added console log for debugging
        logEvent("Config Error: Invalid Host URL.");
        updateConnectionUI('disconnected');
    }
}

// --- MQTT Callbacks ---

function onConnectSuccess() {
    updateConnectionUI('connected');
    logEvent("Online. Subscribed to necessary topics.");
    
    // Subscribe to State topics 
    mqttClient.subscribe(MQTT_CONFIG.topic_spot_state + "/#");
    mqttClient.subscribe(MQTT_CONFIG.topic_summary);
}

function onConnectFailure(error) {
    logEvent("Connection Error: " + error.errorMessage);
    updateConnectionUI('disconnected');
}

function onConnectionLost(response) {
    if (response.errorCode !== 0) {
        logEvent("Link Failure: " + response.errorMessage);
        updateConnectionUI('disconnected');
    }
}

function onMessageArrived(msg) {
    const topic = msg.destinationName;
    const payloadStr = msg.payloadString;

    // 1. Handle Spot State (JSON)
    if (topic.startsWith(MQTT_CONFIG.topic_spot_state)) {
        try {
            const data = JSON.parse(payloadStr);
            // data format: { spotId:"L1-P2", state:"occupied", plate:"...", ... }
            updateSpotUI(data);
        } catch (e) {
            console.error("JSON Error in SpotState", e);
        }
    }
    // 2. Handle Summary (Int)
    else if (topic === MQTT_CONFIG.topic_summary) {
        document.getElementById('stat-summary').textContent = availableSpot - payloadStr;
        document.getElementById('stat-available').textContent = payloadStr;
    }
}

// --- UI Updates ---

/**
 * Aktualisiert die Parkplatz-Karte basierend auf MQTT-Daten.
 * @param {Object} data - Erwartet { spotId: "L1-P1", state: "free", plate: "..." }
 */
function updateSpotUI(data) {
    if (!data || !data.spotId) return;

    const card = document.getElementById(`spot-${data.spotId}`);
    if (!card) return;
    
    const icon = card.querySelector('.icon');
    const label = card.querySelector('.spot-label');
    const timeDisplay = card.querySelector('.departure-time'); // Neues Element greifen

    card.classList.remove('available', 'occupied', 'reserved');
    const state = data.state.toLowerCase();

    // Zeit-Formatierung vorbereiten
    let timeString = "";
    if (data.estimatedDepartureTime) {
        const date = new Date(data.estimatedDepartureTime);
        // Formatiert zu "HH:mm", z.B. "15:30"
        timeString = date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    }

    if (state === 'occupied') {
        card.classList.add('occupied');
        icon.textContent = 'block';
        label.textContent = 'Belegt';
        if (timeDisplay) timeDisplay.textContent = `bis ~${timeString}`; 
        card.setAttribute('data-tip', data.plate || 'Besetzt');
    } 
    else if (state === 'reserved') {
        card.classList.add('reserved');
        icon.textContent = 'bookmarks';
        label.textContent = 'Reserviert';
        if (timeDisplay) timeDisplay.textContent = ""; 
        card.setAttribute('data-tip', data.plate || 'Reserviert');
    } 
    else {
        card.classList.add('available');
        icon.textContent = 'directions_car';
        label.textContent = 'Frei';
        if (timeDisplay) timeDisplay.textContent = ""; 
        card.removeAttribute('data-tip');
    }
    
    refreshCount();
}
function refreshCount() {
    // Simple frontend count of "available" class
    const free = document.querySelectorAll('.spot-card.available').length;
    document.getElementById('stat-available').textContent = free;


}

function updateConnectionUI(status) {
    const container = document.getElementById('connection-status');
    const dot = container.querySelector('.status-dot');
    const text = container.querySelector('.status-text');

    if (!container || !dot || !text) return;

    container.className = "flex items-center gap-2 px-3 py-1.5 rounded-full text-[10px] font-black border transition-colors duration-300";
    
    dot.className = "w-2 h-2 rounded-full status-dot"; 

    if (status === 'connected') {
        container.classList.add('bg-green-50', 'text-green-700', 'border-green-200');
        dot.classList.add('bg-green-500', 'status-pulse');
        text.textContent = "Live";
    } else if (status === 'connecting') {
        container.classList.add('bg-amber-50', 'text-amber-700', 'border-amber-200');
        dot.classList.add('bg-amber-500', 'status-pulse');
        text.textContent = "Syncing";
    } else {
        container.classList.add('bg-slate-50', 'text-slate-400', 'border-slate-200');
        dot.classList.add('bg-slate-300');
        text.textContent = "Standby";
    }
}

// --- Actions (Publishing) ---

/**
 * Sends BarrierCommand
 * JSON: { gateId: "Einfahrt-Nord", plate: "...", action: "OPEN" }
 */
/**
 * Sendet den OPEN Befehl und setzt die Animation nach 10 Sek. zurück
 */
function publishGate(action) {
    if (!mqttClient || !mqttClient.isConnected()) {
        showToast("Error: Broker disconnected", "error");
        return;
    }
    if (action === "OPEN") {
        const command = {
            gateId: "Main-Gate", 
            action: action 
        };

        const message = new Paho.MQTT.Message(JSON.stringify(command));
        message.destinationName = MQTT_CONFIG.topic_barrier;
        mqttClient.send(message);
        
        const visual = document.getElementById('gate-visual');
        if (visual) {
            visual.classList.add('gate-open');
            setTimeout(() => {
                visual.classList.remove('gate-open');
                console.log("Animation: Gate closed visually");
            }, 10000); 
        }
    }
}

/**
 * Sends MoveRequest (Notify Blockers)
 * JSON: { plate: "...", requestedBy: "Webservice", timestamp: "..." }
 */
function sendMoveRequest() {
    const input = document.getElementById('move-plate');
    const plate = input.value.trim().toUpperCase();
    
    if (!plate) {
        showToast("Enter a blocker plate!", "warning");
        return;
    }

    if (!mqttClient || !mqttClient.isConnected()) {
        showToast("Offline: Message not sent.", "error");
        return;
    }

    const payload = {
        plate: plate,
        requestedBy: "WebDashboard",
        timestamp: new Date().toISOString().split('.')[0] // Remove millis to match example roughly
    };

    const message = new Paho.MQTT.Message(JSON.stringify(payload));
    message.destinationName = MQTT_CONFIG.topic_move;
    mqttClient.send(message);
    
    logEvent(`Move Request sent for ${plate}`);
    showToast(`Request sent: ${plate}`);
    input.value = '';
}

/**
 * Sends RegistrationEvent (Blind)
 * JSON: { plate: "...", role: "...", phoneNumber: "...", course: "...", timestamp: "..." }
 */
function submitRegistration() {
    const plate = document.getElementById('reg-plate').value.trim().toUpperCase();
    const role = document.getElementById('reg-role').value;
    const phone = document.getElementById('reg-phone').value.trim();
    const course = document.getElementById('reg-course').value.trim().toUpperCase();

    if (!plate || !phone) {
        showToast("Please fill in Plate and Phone", "warning");
        return;
    }

    if (!mqttClient || !mqttClient.isConnected()) {
        showToast("Offline: Cannot register.", "error");
        return;
    }

    const payload = {
        plate: plate,
        role: role,
        phoneNumber: phone,
        course: course,
        timestamp: new Date().toISOString().split('.')[0]
    };

    const message = new Paho.MQTT.Message(JSON.stringify(payload));
    message.destinationName = MQTT_CONFIG.topic_registration;
    mqttClient.send(message);

    logEvent(`Reg. Data sent for ${plate}`);
    showToast("Registration Data Sent (Blind)", "success");
    toggleRegModal();
}

// --- Utilities ---

function logEvent(msg) {
    const logContainer = document.getElementById('log-stream');
    const time = new Date().toLocaleTimeString([], {hour:'2-digit', minute:'2-digit', second:'2-digit'});
    
    const entry = document.createElement('div');
    entry.className = "p-2 bg-slate-50 border-l-2 border-indigo-200 rounded text-[10px] flex gap-2 animate-in slide-in-from-right duration-300 mb-1";
    entry.innerHTML = `<span class="opacity-30 font-mono">${time}</span> <span>${msg}</span>`;
    
    logContainer.prepend(entry);
    if (logContainer.children.length > 50) logContainer.lastChild.remove();
}

function showToast(message, type = "success") {
    const toast = document.getElementById('toast');
    const toastMsg = document.getElementById('toast-message');
    const toastBg = document.getElementById('toast-bg');
    
    toastMsg.textContent = message;
    
    toastBg.className = "p-1.5 rounded-full flex items-center justify-center transition-colors duration-300 ";
    if (type === "error") toastBg.classList.add('bg-red-500');
    else if (type === "warning") toastBg.classList.add('bg-amber-500');
    else toastBg.classList.add('bg-green-500');

    toast.classList.remove('hidden-toast');
    setTimeout(() => { toast.classList.add('hidden-toast'); }, 4000);
}

// Start
window.onload = init;