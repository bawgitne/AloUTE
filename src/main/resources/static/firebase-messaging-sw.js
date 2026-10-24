importScripts('https://www.gstatic.com/firebasejs/10.9.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.9.0/firebase-messaging-compat.js');

const firebaseConfig = {
  apiKey: "AIzaSyCoE1xYkQbD6s4gAW4QnncvAOg5IPm2xOY",
  authDomain: "cnpm-f1a39.firebaseapp.com",
  projectId: "cnpm-f1a39",
  storageBucket: "cnpm-f1a39.firebasestorage.app",
  messagingSenderId: "536335604006",
  appId: "1:536335604006:web:909fe5337f60cd0e7709e7",
  measurementId: "G-VFTRKLRGCL"
};

firebase.initializeApp(firebaseConfig);
const messaging = firebase.messaging();

messaging.onBackgroundMessage((payload) => {
  const title = (payload.notification && payload.notification.title) || "ALOUTE";
  const options = {
    body: (payload.notification && payload.notification.body) || "Bạn có thông báo mới",
    icon: "/images/logo.png",
    data: {
      url: (payload.data && payload.data.click_url) || "/feed"
    }
  };
  self.registration.showNotification(title, options);
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const urlToOpen = (event.notification.data && event.notification.data.url) || "/feed";
  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windowClients) => {
      for (let client of windowClients) {
        if (client.url.includes(urlToOpen) && 'focus' in client) {
          return client.focus();
        }
      }
      if (clients.openWindow) {
        return clients.openWindow(urlToOpen);
      }
    })
  );
});
