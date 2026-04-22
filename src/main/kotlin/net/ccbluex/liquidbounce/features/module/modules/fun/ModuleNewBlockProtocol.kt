package net.ccbluex.liquidbounce.features.module.modules.`fun`

import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.runBlocking
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PlayerMoveEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.`fun`.ModuleNewBlockProtocol.rcon
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import nl.vv32.rcon.Rcon
import java.nio.charset.StandardCharsets
import kotlin.io.encoding.Base64
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds


object ModuleNewBlockProtocol : ClientModule("NewBlockProtocol", ModuleCategories.FUN) {

    lateinit var server: EmbeddedServer<*, *>
    var client: WebSocketSession? = null

    override val tag: String?
        get() = if (client != null) "已连接" else "未连接"

    override fun onEnabled() {
        server = embeddedServer(factory = CIO, port = 8001) {
            install(WebSockets) {
                pingPeriod = 15.seconds
                timeout = 30.seconds
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }
            routing {
                webSocket("/ws") {
                    client = this

                    runCatching {
                        incoming.consumeEach { frame ->
                            when (frame) {
                                is Frame.Text -> {
                                    frame.readText()
                                        .split("|")
                                        .takeIf { it.size == 2 }
                                        ?.also {
                                            println("[发送] ${it[0]}")
                                            when (it[0]) {
                                                "newblock:info" -> player.connection.send(ServerboundCustomPayloadPacket(NewBlockInfo(it[1])))
                                                "newblock:pubkey" -> player.connection.send(ServerboundCustomPayloadPacket(NewBlockPublicKey(it[1])))
                                                "newblock:heartbeat" -> player.connection.send(ServerboundCustomPayloadPacket(NewBlockHeartbeat(it[1])))
                                                else -> println("丢弃 ${it[0]}")
                                            }
                                        }
                                }
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }.start(wait = false)
        rcon = Rcon.open("localhost", 25575)
        rcon.authenticate("LLLLLL")
    }

    lateinit var rcon: Rcon
    var sync = 0

    override fun onDisabled() {
        server.stop()
        client = null

        rcon.close()
    }

    init {
        PayloadTypeRegistry.playC2S().register(NewBlockInfo.TYPE, NewBlockInfo.STREAM_CODEC)
        PayloadTypeRegistry.playC2S().register(NewBlockHeartbeat.TYPE, NewBlockHeartbeat.STREAM_CODEC)
        PayloadTypeRegistry.playC2S().register(NewBlockPublicKey.TYPE, NewBlockPublicKey.STREAM_CODEC)
        handler<WorldChangeEvent> {
            runBlocking {
                client?.send("minecraft:register|dGFiOmJyaWRnZS02AHNyOm1lc3NhZ2VjaGFubmVsAGZsb29kZ2F0ZTpwYWNrZXQAZmxvb2RnYXRlOmZvcm0AZmxvb2RnYXRlOnRyYW5zZmVyAHBhdDpjaGFubmVsAGZsb29kZ2F0ZTpza2lu")
                client?.send("minecraft:register|bmV3YmxvY2s6aW1hZ2VjaHVuawB0cmNoYXQ6c2VydmVyAGZsb29kZ2F0ZTpwYWNrZXQAYnVuZ2VlY29yZDptYWluAGZsb29kZ2F0ZTp0cmFuc2ZlcgBuZXdibG9jazpoZWFydGJlYXQAZmxvb2RnYXRlOmZvcm0AbmV3YmxvY2s6aW5mbwB0YWI6YnJpZGdlLTYAZmxvb2RnYXRlOnNraW4A")
            }
        }
        handler<MovementInputEvent> {
            runCatching {
                rcon.sendCommand("tp ${player.name} ${player.x.roundToInt()} ${player.y.roundToInt()} ${player.z.roundToInt()}")
            }
                .onFailure { println("同步坐标失败 > tp ${player.name} ${player.x.roundToInt()} ${player.y.roundToInt()} ${player.z.roundToInt()}") }
        }
    }

    val brands = listOf("fabric:recipe_sync", "newblock:uid", "fabric:attachment_sync_v1", "newblock:rules", "newblock:screenshot", "newblock:chatmsg", "newblock:heartbeat", "newblock:request", "newblock:webview", "appleskin:saturation", "shulkerboxtooltip:ec_update", "appleskin:exhaustion", "fabric-menu-api-v1:open_screen", "shulkerboxtooltip:s2c_handshake", "appleskin:natural_regeneration")

    fun onReceiveCustomPayload(id: Identifier, buf: FriendlyByteBuf) {
        runCatching {
            val channel = id.toString()
            val data = ByteArray(buf.readableBytes()).apply { buf.readBytes(this)}

            runBlocking { client?.send("$channel|${java.util.Base64.getEncoder().encodeToString(data)}") ?: error("未连接") }

            println("[收到] $channel")
        }
            .onFailure {
                println("上报失败 ${it.message}")
            }
    }

    fun writeRegister(buf: FriendlyByteBuf) {
        var first = true
        brands.forEach { brand ->
            if (first) {
                first = false
            } else {
                buf.writeByte(0)
            }
            buf.writeBytes(brand.toByteArray(StandardCharsets.US_ASCII))
        }


        println("替换modlist成功")
    }

    @JvmRecord
    data class NewBlockInfo(val wrapped: String) : CustomPacketPayload {
        private constructor(buffer: FriendlyByteBuf) : this(buffer.readUtf())

        private fun write(buffer: FriendlyByteBuf) {
            buffer.writeBytes(Base64.decode(wrapped))
        }

        override fun type() = TYPE

        companion object {
            val STREAM_CODEC = CustomPacketPayload.codec(
                { obj, buffer: FriendlyByteBuf -> obj.write(buffer) },
                { buffer -> NewBlockInfo(buffer) }
            )
            val TYPE = CustomPacketPayload.Type<NewBlockInfo>(Identifier.fromNamespaceAndPath("newblock", "info"))
        }
    }

    @JvmRecord
    data class NewBlockHeartbeat(val wrapped: String) : CustomPacketPayload {
        private constructor(buffer: FriendlyByteBuf) : this(buffer.readUtf())

        private fun write(buffer: FriendlyByteBuf) {
            buffer.writeBytes(Base64.decode(wrapped))
        }

        override fun type() = TYPE

        companion object {
            val STREAM_CODEC = CustomPacketPayload.codec(
                { obj, buffer: FriendlyByteBuf -> obj.write(buffer) },
                { buffer -> NewBlockHeartbeat(buffer) }
            )
            val TYPE = CustomPacketPayload.Type<NewBlockHeartbeat>(Identifier.fromNamespaceAndPath("newblock", "heartbeat"))
        }
    }

    @JvmRecord
    data class NewBlockPublicKey(val wrapped: String) : CustomPacketPayload {
        private constructor(buffer: FriendlyByteBuf) : this(buffer.readUtf())

        private fun write(buffer: FriendlyByteBuf) {
            buffer.writeBytes(Base64.decode(wrapped))
        }

        override fun type() = TYPE

        companion object {
            val STREAM_CODEC = CustomPacketPayload.codec(
                { obj, buffer: FriendlyByteBuf -> obj.write(buffer) },
                { buffer -> NewBlockPublicKey(buffer) }
            )
            val TYPE = CustomPacketPayload.Type<NewBlockPublicKey>(Identifier.fromNamespaceAndPath("newblock", "pubkey"))
        }
    }


}
