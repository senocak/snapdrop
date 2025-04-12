# Snapdrop

Snapdrop is a local file sharing solution that allows you to easily transfer files between devices on the same network. It's a web-based, peer-to-peer application that works without requiring any setup, installation, or account creation.

![Snapdrop Logo](src/main/resources/static/images/favicon-96x96.png)

## Features

- **Instant File Sharing**: Transfer files between devices on the same network
- **Text Messaging**: Send text messages between devices
- **No Setup Required**: Just open the web app on both devices
- **No Account Needed**: No sign-up, no login
- **Cross-Platform**: Works on any device with a modern web browser
- **Peer-to-Peer**: Files are transferred directly between devices (WebRTC)
- **Progressive Web App**: Can be installed on devices for offline access
- **Open Source**: Free to use and modify

## How It Works

Snapdrop uses WebSockets for discovery and WebRTC for peer-to-peer data transfer:

1. Devices on the same network connect to the Snapdrop server via WebSockets
2. The server groups devices by IP address, creating "rooms" for local networks
3. Devices in the same room can see each other and establish direct WebRTC connections
4. Files and messages are transferred directly between devices, not through the server

## Technologies Used

- **Backend**:
  - Kotlin
  - Spring Boot
  - WebSockets
  - Java 21

- **Frontend**:
  - HTML5
  - CSS3
  - JavaScript
  - WebRTC
  - Progressive Web App (PWA) features

- **Libraries**:
  - Yauaa (User Agent Analyzer)
  - Datafaker (for generating friendly device names)
  - Jackson (JSON processing)

## Getting Started

### Prerequisites

- Java 21 or higher
- Gradle

### Running the Application

1. Clone the repository:
   ```
   git clone https://github.com/yourusername/snapdrop.git
   cd snapdrop
   ```

2. Build and run the application:
   ```
   ./gradlew bootRun
   ```

3. Open your browser and navigate to:
   ```
   http://localhost:8085
   ```

4. To access from other devices on the same network, use your computer's local IP address:
   ```
   http://your-local-ip:8085
   ```

### Building for Production

To build a production-ready JAR file:

```
./gradlew build
```

The JAR file will be located in `build/libs/snapdrop-0.0.1.jar`.

## Usage

1. Open Snapdrop on two or more devices connected to the same network
2. Each device will appear as a node on the other devices' screens
3. To send a file:
   - Click/tap on the recipient's node
   - Select the file you want to send
   - The recipient will receive a notification to accept the file
4. To send a message:
   - Right-click (or long-press on mobile) on the recipient's node
   - Type your message and send
   - The recipient will receive the message in a popup

## Configuration

The application can be configured through `application.yml`:

```yaml
server:
  port: 8085  # Change this to use a different port
spring:
  websocket:
    max-text-message-size: 65536
    max-binary-message-size: 65536
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is open source and available under the [MIT License](LICENSE).

## Acknowledgments

- This project is inspired by the original [Snapdrop](https://snapdrop.net/) created by [RobinLinus](https://github.com/RobinLinus)
- Thanks to all contributors who have helped improve this project