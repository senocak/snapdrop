package com.github.senocak.snapdrop

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpHeaders
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.net.InetSocketAddress
import java.net.URI

// Test-specific subclass that exposes protected methods
class TestableSnapdropWebSocketHandler(objectMapper: ObjectMapper) : SnapdropWebSocketHandler(objectMapper) {
    public override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        super.handleTextMessage(session, message)
    }
}

@ExtendWith(MockitoExtension::class)
class SnapdropWebSocketHandlerTest {

    @Mock
    private lateinit var objectMapper: ObjectMapper

    @Mock
    private lateinit var session: WebSocketSession

    private lateinit var handler: TestableSnapdropWebSocketHandler

    // Initialize handler manually since we're not using @InjectMocks with our custom subclass
    @BeforeEach
    fun setup() {
        handler = TestableSnapdropWebSocketHandler(objectMapper)
    }

    @Test
    fun `test connection lifecycle`() {
        // Setup session mock
        val headers = HttpHeaders()
        headers.add("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
        headers.add("Cookie", "peerid=test-peer-id")

        `when`(session.id).thenReturn("test-session-id")
        `when`(session.handshakeHeaders).thenReturn(headers)
        `when`(session.remoteAddress).thenReturn(InetSocketAddress("127.0.0.1", 8080))
        `when`(session.uri).thenReturn(URI("/server/webrtc"))
        `when`(session.isOpen).thenReturn(true)

        // Test connection established
        handler.afterConnectionEstablished(session)

        // Test message handling
        val pingMessage = TextMessage("""{"type":"pong"}""")
        `when`(objectMapper.readValue(pingMessage.payload, Map::class.java)).thenReturn(mapOf("type" to "pong"))
        handler.handleTextMessage(session, pingMessage)

        // Test connection closed
        handler.afterConnectionClosed(session, CloseStatus.NORMAL)
    }

    @Test
    fun `test signal message handling`() {
        // Setup session mock
        val headers = HttpHeaders()
        headers.add("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
        headers.add("Cookie", "peerid=test-peer-id")

        `when`(session.id).thenReturn("test-session-id")
        `when`(session.handshakeHeaders).thenReturn(headers)
        `when`(session.remoteAddress).thenReturn(InetSocketAddress("127.0.0.1", 8080))
        `when`(session.uri).thenReturn(URI("/server/webrtc"))
        `when`(session.isOpen).thenReturn(true)

        // Establish connection
        handler.afterConnectionEstablished(session)

        // Test signal message
        val signalMessage = TextMessage("""{"type":"signal","to":"other-peer-id","sdp":"test-sdp"}""")
        `when`(objectMapper.readValue(signalMessage.payload, Map::class.java))
            .thenReturn(mapOf("type" to "signal", "to" to "other-peer-id", "sdp" to "test-sdp"))

        handler.handleTextMessage(session, signalMessage)
    }
}
