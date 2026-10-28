// Import the functions you need from the SDKs you need
import { initializeApp } from "firebase/app";
import { getAnalytics } from "firebase/analytics";
// TODO: Add SDKs for Firebase products that you want to use
// https://firebase.google.com/docs/web/setup#available-libraries

// Your web app's Firebase configuration
// For Firebase JS SDK v7.20.0 and later, measurementId is optional
const firebaseConfig = {
  apiKey: "<FIREBASE_WEB_API_KEY>", // lấy từ Firebase Console; không commit khóa thật vào repo
  authDomain: "cnpm-f1a39.firebaseapp.com",
  projectId: "cnpm-f1a39",
  storageBucket: "cnpm-f1a39.firebasestorage.app",
  messagingSenderId: "536335604006",
  appId: "1:536335604006:web:909fe5337f60cd0e7709e7",
  measurementId: "G-VFTRKLRGCL"
};

// Initialize Firebase
const app = initializeApp(firebaseConfig);
const analytics = getAnalytics(app);
