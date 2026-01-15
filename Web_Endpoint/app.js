/**
 * SmartPark MQTT Dashboard Logic
 * Updated for Specific JSON API
 */

// --- CONFIGURATION ---
const MQTT_CONFIG = {
    host: 'wss://localhost:1883', 
    // Topics
    topic_spot_state: 'parking/state/spot',  // + /#
    topic_summary: 'parking/state/summary',
    topic_move: 'parking/move/request',
    topic_barrier: 'parking/access/barrier',
    topic_registration: 'parking/registration/plate'
};

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
        const url = new URL(MQTT_CONFIG.host);
        const clientId = "sp_web_" + Math.random().toString(16).substr(2, 6);
        
        mqttClient = new Paho.MQTT.Client(url.hostname, Number(url.port), url.pathname, clientId);
        
        mqttClient.onConnectionLost = onConnectionLost;
        mqttClient.onMessageArrived = onMessageArrived;

        mqttClient.connect({
            onSuccess: onConnectSuccess,
            onFailure: onConnectFailure,
            useSSL: url.protocol === 'wss:',
            reconnect: true
        });
    } catch (e) {
        logEvent("Config Error: Invalid Host URL.");
        updateConnectionUI('disconnected');
    }
}

// --- MQTT Callbacks ---

function onConnectSuccess() {
    updateConnectionUI('connected');
    logEvent("Online. Subscribed to necessary topics.");
    
    // Subscribe ONLY to State topics (to see what is happening)
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
            // data format: { spotId:"A-01", state:"occupied", plate:"...", ... }
            updateSpotUI(data);
        } catch (e) {
            console.error("JSON Error in SpotState", e);
        }
    }
    // 2. Handle Summary (Int)
    else if (topic === MQTT_CONFIG.topic_summary) {
        // payload is just an Integer, e.g. "25"
        document.getElementById('stat-summary').textContent = payloadStr;
    }
}

// --- UI Updates ---

function updateSpotUI(data) {
    // Need to find the correct numeric ID from "A-01", "B-09" etc.
    // Logic: Extract the number from the string.
    const idMatch = data.spotId.match(/\d+/); 
    if (!idMatch) return;
    
    const numericId = parseInt(idMatch[0]); // "01" -> 1
    const card = document.getElementById(`spot-${numericId}`);
    
    if (!card) return;
    
    const icon = card.querySelector('.icon');
    const label = card.querySelector('.spot-label');
    const displayId = card.querySelector('.spot-id');

    // Update displayed ID just in case
    displayId.textContent = data.spotId;

    // Remove all states
    card.classList.remove('available', 'occupied', 'reserved');

    if (data.state === 'occupied') {
        card.classList.add('occupied');
        icon.textContent = 'block';
        label.textContent = 'Taken';
        // Optional: Tooltip with Plate?
        card.setAttribute('data-tip', data.plate || 'Unknown');
    } else if (data.state === 'reserved') {
        card.classList.add('reserved');
        icon.textContent = 'bookmarks';
        label.textContent = 'Reserved';
        card.setAttribute('data-tip', data.plate || 'Reserved');
    } else {
        card.classList.add('available');
        icon.textContent = 'directions_car';
        label.textContent = 'Free';
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

    container.className = "flex items-center gap-2 px-3 py-1.5 rounded-full text-[10px] font-black border transition-colors duration-300";
    dot.className = "w-2 h-2 rounded-full";

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
function publishGate(action) {
    if (!mqttClient || !mqttClient.isConnected()) {
        showToast("Error: Broker disconnected", "error");
        return;
    }

    const myPlate = document.getElementById('gate-plate').value.trim() || "UNKNOWN";

    const command = {
        gateId: "Main-Gate", // Hardcoded or dynamic
        plate: myPlate,
        action: action // "OPEN" or "CLOSE"
    };

    const message = new Paho.MQTT.Message(JSON.stringify(command));
    message.destinationName = MQTT_CONFIG.topic_barrier;
    mqttClient.send(message);
    
    logEvent(`Sent BarrierCommand: ${action} for ${myPlate}`);
    
    // Optimistic UI update for visualization
    const visual = document.getElementById('gate-visual');
    const stat = document.getElementById('stat-gate');
    if(action === "OPEN") {
        visual.classList.add('gate-open');
        stat.textContent = "OPEN";
    } else {
        visual.classList.remove('gate-open');
        stat.textContent = "CLOSED";
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