# OfflinePay (Android, Kotlin + Compose)

Bina internet UPI payment, *99# se. Sab kuch app ke andar, koi system popup nahi.

## Pehli baar kholne par
1. Swagat + bhasha  2. Login (Google ya Guest)  3. Permissions (step-by-step guide, sab dena zaroori)
4. Payment SIM chunna (dual SIM mein ek baar)  5. App Lock (optional)

## Features
- QR scan, UPI ID, mobile number se pay; balance check
- Payment ke dauran professional loading screen, safal hone par success screen + notification
- Har payment ka Payment ID + receipt (receiver ka naam jo bank ne dikhaya), history delete nahi hoti
- App Lock (PIN), screenshots blocked

## APK banana
GitHub par saari files upload karein -> Actions "Build APK" chalegi -> Code tab -> Releases se app-debug.apk download.

## Google login setup (zaroori, warna sirf Guest chalega)
1. console.cloud.google.com -> naya project -> APIs & Services -> OAuth consent screen
2. Credentials -> Create OAuth client ID: "Web application" aur "Android" (package com.offlinepay.app + SHA-1)
3. Web client ka Client ID GoogleAuth.kt mein WEB_CLIENT_ID mein paste karein.
Note: GitHub ki debug APK har build par naye key se sign hoti hai, isliye SHA-1 badalta rehta hai.
Google login stable chalane ke liye ek fixed keystore chahiye.

## Dhyan rakhein
- Asli phone par hi chalega. SIM mein signal + bank-registered number chahiye.
- Limit ~Rs 5,000, ~Rs 0.50 telecom charge.
- Receiver ka naam bank ke USSD jawab se nikala jata hai; kuch banks naam nahi bhejte, tab bank ka poora sandesh dikhta hai.
