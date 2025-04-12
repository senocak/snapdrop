package com.github.senocak.snapdrop

import com.fasterxml.jackson.databind.ObjectMapper
import net.datafaker.Faker
import nl.basjes.parse.useragent.UserAgent
import nl.basjes.parse.useragent.UserAgentAnalyzer
import org.slf4j.Logger
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.Random
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.collections.get

@Component
class SnapdropWebSocketHandler(
    private val objectMapper: ObjectMapper
): TextWebSocketHandler() {
    private val logger: Logger by logger()
    private val rooms: ConcurrentMap<String, MutableMap<String, Peer>> = ConcurrentHashMap()
    private val sessions: ConcurrentMap<String, WebSocketSession> = ConcurrentHashMap()
    private val keepAliveExecutor: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
    private val userAgentAnalyzer: UserAgentAnalyzer = UserAgentAnalyzer.newBuilder().build()
    private val faker = Faker()

    init {
        // Schedule keep-alive pings
        keepAliveExecutor.scheduleAtFixedRate(this::sendKeepAlive, 30, 30, TimeUnit.SECONDS)
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val peer: Peer = createPeer(session = session)
        sessions[session.id] = session
        joinRoom(peer = peer)
        sendDisplayName(peer = peer)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val peer: Peer? = findPeerBySessionId(session.id)
        if (peer != null)
            leaveRoom(peer = peer)
        sessions.remove(key = session.id)
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val peer: Peer = findPeerBySessionId(session.id) ?: return
        try {
            val messageMap = objectMapper.readValue(message.payload, Map::class.java)
            val type: String? = messageMap["type"] as String?
            when (type) {
                "disconnect" -> leaveRoom(peer)
                "pong" -> peer.lastBeat = System.currentTimeMillis()
                "signal" -> {
                    val to: String? = messageMap["to"] as String?
                    if (to != null && rooms[peer.ip]?.containsKey(to) == true) {
                        val recipient: Peer? = rooms[peer.ip]?.get(to)
                        val newMessage: MutableMap<String, Any?> = mutableMapOf()
                        messageMap.forEach { (key, value) ->
                            if (key != "to") {
                                newMessage[key.toString()] = value
                            }
                        }
                        newMessage["sender"] = peer.id
                        sendMessage(peer = recipient, message = newMessage)
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error handling message", e)
        }
    }

    private fun createPeer(session: WebSocketSession): Peer {
        val peerId: String = getPeerId(session = session)
        val rtcSupported: Boolean = session.uri?.path?.contains(other = "webrtc") ?: false
        val userAgent: String = session.handshakeHeaders.getFirst("user-agent") ?: ""

        val parsedUserAgent: UserAgent.ImmutableUserAgent = userAgentAnalyzer.parse(userAgent)
        val osName: String = parsedUserAgent.getValue("OperatingSystemName") ?: ""
        val deviceModel: String = parsedUserAgent.getValue("DeviceModel") ?: ""
        val browserName: String = parsedUserAgent.getValue("AgentName") ?: ""
        val deviceType: String = parsedUserAgent.getValue("DeviceClass") ?: ""

        val deviceName: String = buildDeviceName(osName = osName, deviceModel = deviceModel, browserName = browserName)
        val displayName: String = generateDisplayName(seed = peerId)

        return Peer(
            id = peerId,
            ip = getIpAddress(session = session),
            sessionId = session.id,
            rtcSupported = rtcSupported,
            name = PeerName(
                model = deviceModel,
                os = osName,
                browser = browserName,
                type = deviceType,
                deviceName = deviceName,
                displayName = displayName
            ),
            lastBeat = System.currentTimeMillis()
        )
    }

    private fun getIpAddress(session: WebSocketSession): String {
        var ip: String = session.remoteAddress?.address?.hostAddress ?: "127.0.0.1"
        // Check for X-Forwarded-For header
        val forwardedFor: String? = session.handshakeHeaders.getFirst("X-Forwarded-For")
        if (!forwardedFor.isNullOrBlank())
            ip = forwardedFor.split(",")[0].trim()
        // Normalize localhost addresses
        if (ip == "::1" || ip == "::ffff:127.0.0.1")
            ip = "127.0.0.1"
        return ip
    }

    private fun getPeerId(session: WebSocketSession): String {
        // Try to get peerId from cookies
        val cookies: List<String>? = session.handshakeHeaders.get("Cookie")
        if (cookies != null) {
            for (cookieHeader: String in cookies) {
                if (cookieHeader.contains(other = "peerid=")) {
                    val peeridCookie: String? = cookieHeader.split(";")
                        .find { it.trim().startsWith(prefix = "peerid=") }
                    if (peeridCookie != null)
                        return peeridCookie.trim().substring(startIndex = "peerid=".length)
                }
            }
        }

        // Generate a new UUID if no cookie found
        return UUID.randomUUID().toString()
    }

    private fun buildDeviceName(osName: String, deviceModel: String, browserName: String): String {
        val deviceNameBuilder = StringBuilder()
        when {
            osName.isNotBlank() -> deviceNameBuilder.append(osName.replace(oldValue = "Mac OS", newValue = "Mac")).append(" ")
        }
        when {
            deviceModel.isNotBlank() -> deviceNameBuilder.append(deviceModel)
            browserName.isNotBlank() -> deviceNameBuilder.append(browserName)
        }
        return deviceNameBuilder.toString().ifBlank { "Unknown Device" }
    }

    private fun generateDisplayName(seed: String): String {
        val random = Random(seed.hashCode().toLong())
        val adjective: String = faker.color().name().capitalize()
        val animal: String = faker.animal().name().capitalize()
        return "$adjective $animal ${random.nextInt(100)}"
    }

    private fun joinRoom(peer: Peer) {
        // Create room if it doesn't exist
        if (!rooms.containsKey(key = peer.ip)) {
            rooms[peer.ip] = ConcurrentHashMap()
        }

        // Notify all other peers in the room
        rooms[peer.ip]?.forEach { (_, otherPeer: Peer) ->
            sendMessage(peer = otherPeer, message = mapOf(
                "type" to "peer-joined",
                "peer" to peer.getInfo()
            ))
        }

        // Notify the new peer about existing peers
        val otherPeers: List<Map<String, Any>> = rooms[peer.ip]?.values?.map { it.getInfo() } ?: emptyList()
        sendMessage(peer = peer, message = mapOf(
            "type" to "peers",
            "peers" to otherPeers
        ))

        // Add peer to room
        rooms[peer.ip]?.put(key = peer.id, value = peer)
    }

    private fun leaveRoom(peer: Peer) {
        if (rooms[peer.ip]?.containsKey(key = peer.id) != true) return

        // Remove peer from room
        rooms[peer.ip]?.remove(key = peer.id)

        // Close session
        val session: WebSocketSession? = sessions[peer.sessionId]
        session?.close()

        // If room is empty, remove it
        if (rooms[peer.ip]?.isEmpty() == true) {
            rooms.remove(key = peer.ip)
        } else {
            // Notify other peers
            rooms[peer.ip]?.forEach { (_, otherPeer: Peer) ->
                sendMessage(peer = otherPeer, message = mapOf(
                    "type" to "peer-left",
                    "peerId" to peer.id
                ))
            }
        }
    }

    private fun sendDisplayName(peer: Peer) {
        sendMessage(peer = peer, message = mapOf(
            "type" to "display-name",
            "message" to mapOf(
                "displayName" to peer.name.displayName,
                "deviceName" to peer.name.deviceName
            )
        ))
    }

    private fun sendMessage(peer: Peer?, message: Map<String, Any?>) {
        if (peer == null) return
        val session: WebSocketSession = sessions[peer.sessionId] ?: return
        if (!session.isOpen) return

        try {
            val jsonMessage: String = objectMapper.writeValueAsString(message)
            session.sendMessage(TextMessage(jsonMessage))
        } catch (e: Exception) {
            logger.error("Error sending message", e)
        }
    }

    private fun findPeerBySessionId(sessionId: String): Peer? {
        for (room: MutableMap<String, Peer> in rooms.values) {
            for (peer: Peer in room.values) {
                if (peer.sessionId == sessionId) {
                    return peer
                }
            }
        }
        return null
    }

    private fun sendKeepAlive() {
        val now: Long = System.currentTimeMillis()
        val timeout = 60_000L // 60 seconds

        for (room: MutableMap<String, Peer> in rooms.values) {
            for (peer: Peer in room.values.toList()) { // Create a copy to avoid concurrent modification
                if (now - peer.lastBeat > 2 * timeout) {
                    // Peer hasn't responded for too long, remove it
                    leaveRoom(peer = peer)
                } else {
                    // Send ping
                    sendMessage(peer = peer, message = mapOf("type" to "ping"))
                }
            }
        }
    }

    data class Peer(
        val id: String,
        val ip: String,
        val sessionId: String,
        val rtcSupported: Boolean,
        val name: PeerName,
        var lastBeat: Long
    ) {
        fun getInfo(): Map<String, Any> =
            mapOf(
                "id" to id,
                "name" to name,
                "rtcSupported" to rtcSupported
            )
    }

    data class PeerName(
        val model: String,
        val os: String,
        val browser: String,
        val type: String,
        val deviceName: String,
        val displayName: String
    )
}