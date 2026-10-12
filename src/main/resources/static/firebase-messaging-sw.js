importScripts('https://www.gstatic.com/firebasejs/10.9.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.9.0/firebase-messaging-compat.js');

const firebaseConfig = {
  apiKey: "AIzaSyCWDZpx9-VW5ODIlLfVMcvozkwvKOVoTVg",
  authDomain: "dmeo-ae149.firebaseapp.com",
  projectId: "dmeo-ae149",
  storageBucket: "dmeo-ae149.firebasestorage.app",
  messagingSenderId: "46799592398",
  appId: "1:46799592398:web:b5560a829772c21094a99f",
  measurementId: "G-6E2N8M2RE6"
};

firebase.initializeApp(firebaseConfig);
const messaging = firebase.messaging();

messaging.onBackgroundMessage((payload) => {
  const title = (payload.notification && payload.notification.title) || "ALOUTE";
  const options = {
    body: (payload.notification && payload.notification.body) || "Bạn có thông báo mới",
    icon: "/images/default-avatar.png",
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
