# Test

This repository contains a proof-of-concept Android app and relay server for a test internet-sharing flow.

Important: Android does not allow a normal app to create a full network route for internet sharing without system privileges, VPN, or root. This project is a test/demo relay app that demonstrates the client/server connection pattern for a shared-internet prototype.

## Architecture

- Android app (source/client role)
- Node.js relay server
- WebSocket-based communication between devices

## Roles

- Source device: has internet and acts as a relay participant
- Client device: asks for internet through the relay server
- Server: coordinates connection and message routing

## Quick start

### Server

```bash
cd server
npm install
npm start
```

Server runs on port 3000.

### Android app

Open the `android` folder in Android Studio and run the app.

Use the server URL as:

- emulator: `http://10.0.2.2:3000`
- real device: the laptop/PC IP on the same LAN, for example `http://192.168.0.12:3000`

## Notes

This is a demo prototype for learning and testing. For a production app, you would need a real tunneling / VPN / routing mechanism and server-side infrastructure.
