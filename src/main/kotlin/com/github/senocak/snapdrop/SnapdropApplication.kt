package com.github.senocak.snapdrop

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@EnableWebSocket
@SpringBootApplication
class SnapdropApplication(
    private val snapdropWebSocketHandler: SnapdropWebSocketHandler
): WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry
            .addHandler(snapdropWebSocketHandler, "/server/webrtc")
            .setAllowedOrigins("*")
    }
}

fun main(args: Array<String>) {
    runApplication<SnapdropApplication>(*args)
}

fun <R : Any> R.logger(): Lazy<Logger> = lazy {
    LoggerFactory.getLogger((if (javaClass.kotlin.isCompanion) javaClass.enclosingClass else javaClass).name)
}