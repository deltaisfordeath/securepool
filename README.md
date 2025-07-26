# Securepool
#### Georgia Southwestern State University  
#### CSCI 6130 - Mobile Security  
#### Summer 2025  
  
Purpose: To design an insecure Android application, identify and model threats, and then secure the app by applying techniques learned from CSCI 6130 - Mobile Security.  
  
## Prerequisites  
- Android Studio  
- MySQL  
- NodeJS  
- OpenSSL  

## Backend Server  
- From the `backend` directory, run `npm install`  
- Run `cp .env.example .env` to copy the template .env file.
- Populate the `.env` file with your secret keys and connection properties (can be left as-is for dev environment)  
- Run `npm run start` to start the server, or `npm run dev` for hot reloading of server.js changes.

## Android Application
- Copy `securepool_cert.pem` from `backend\dev_cert` to your emulated Android device in Android Studio by dragging it to the virtual device's screen.  
- Install the certificate on the Android device `Settings > Security > Encryption & credentials > Install a certificate > CA Certificate` and locate `securepool_cert.pem`  
