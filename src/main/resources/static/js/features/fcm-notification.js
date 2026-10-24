import { initializeApp } from "https://www.gstatic.com/firebasejs/10.9.0/firebase-app.js";
import { getMessaging, getToken, onMessage } from "https://www.gstatic.com/firebasejs/10.9.0/firebase-messaging.js";

const firebaseConfig = {
  apiKey: "AIzaSyCoE1xYkQbD6s4gAW4QnncvAOg5IPm2xOY",
  authDomain: "cnpm-f1a39.firebaseapp.com",
  projectId: "cnpm-f1a39",
  storageBucket: "cnpm-f1a39.firebasestorage.app",
  messagingSenderId: "536335604006",
  appId: "1:536335604006:web:909fe5337f60cd0e7709e7",
  measurementId: "G-VFTRKLRGCL"
};

const VAPID_KEY = "BCpqck5lGPEQC4k2LC0OV6dTJIcVNVGcgXs7EywUriQ9_36dztVvxnJA9EJ6l7eG6HVcD9QfC0dNOiGyIik4PTo";

let app;
let messaging;

try {
  app = initializeApp(firebaseConfig);
  messaging = getMessaging(app);
} catch (e) {
  console.warn("Firebase app initialization skipped:", e);
}

export async function initFcmNotification() {
  if (!('Notification' in window) || !('serviceWorker' in navigator) || !messaging) {
    console.log("Trình duyệt không hỗ trợ Web Push Notification hoặc chưa nạp FCM SDK.");
    return;
  }

  try {
    const permission = await Notification.requestPermission();
    if (permission !== 'granted') {
      console.log("Người dùng từ chối cấp quyền thông báo.");
      return;
    }

    const registration = await navigator.serviceWorker.register('/firebase-messaging-sw.js');
    const token = await getToken(messaging, {
      vapidKey: VAPID_KEY,
      serviceWorkerRegistration: registration
    });

    if (token) {
      console.log("FCM Device Token:", token);
      await fetch('/api/notifications/fcm-token', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ token: token })
      });
    }
  } catch (err) {
    console.warn("Lỗi khi kích hoạt FCM Web Push:", err);
  }
}

if (messaging) {
  onMessage(messaging, (payload) => {
    console.log("Nhận thông báo trong foreground tab:", payload);
    if (Notification.permission === 'granted') {
      new Notification((payload.notification && payload.notification.title) || "ALOUTE", {
        body: (payload.notification && payload.notification.body) || "Bạn có thông báo mới",
        icon: "/images/logo.png"
      });
    }
  });
}
