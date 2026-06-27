package net.ccbluex.liquidbounce.features.module.modules.`fun`

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import kotlin.io.encoding.Base64

object ModuleNewBlockProtocol : ClientModule("NewBlockProtocol", ModuleCategories.FUN) {

    val client = HttpClient(Java) {
        install(WebSockets) {
            maxFrameSize = Long.MAX_VALUE
        }
    }

    var ws: DefaultClientWebSocketSession? = null

    override fun onEnabled() {
        Thread {
            runBlocking {
                ws = client.webSocketSession(host = "127.0.0.1", port = 8080, path = "/ws")
                println("[WS] 连接成功")

                launch {
                    runCatching {
                        for (frame in ws!!.incoming) {
                            val message = (frame as? Frame.Text)?.readText()?.split("|") ?: continue

                            println("[WS] $message")

                            when (message[0]) {
                                "info" -> mc.connection?.send(ServerboundCustomPayloadPacket(InfoPayload(message[1])))
                                "public_key" -> mc.connection?.send(ServerboundCustomPayloadPacket(PublicKeyPayload(message[1])))
                                "heartbeat" -> mc.connection?.send(ServerboundCustomPayloadPacket(HeartBeatPayload(message[1])))
                                "screenshot" -> message[1].split("::::").forEach { raw ->
                                    mc.connection?.send(ServerboundCustomPayloadPacket(ImageChunkPayload(raw)))
                                }
                            }
                        }
                    }
                        .onFailure { e -> e.printStackTrace() }

                    println("[WS] 断开连接")
                }
            }
        }.start()
    }

    override fun onDisabled() {
        runCatching {
            runBlocking {
                ws?.close()
            }
        }
        ws = null
    }

    init {
        PayloadTypeRegistry.clientboundPlay().register(RequestPayload.ID, RequestPayload.CODEC)

        ClientPlayNetworking.registerGlobalReceiver(RequestPayload.ID) { payload, _ ->
            runBlocking {
                ws?.send(Frame.Text(buildString {
                    append("public_key")
                }))

                ws?.send(Frame.Text(buildString {
                    append("info")
                    append("|")
                    append(payload.raw)
                    append("|")
                    append(player.profile.name().get())
                    append("|")
                    append(player.x.toString())
                    append("|")
                    append(player.y.toString())
                    append("|")
                    append(player.z.toString())
                }))
            }
        }

        PayloadTypeRegistry.clientboundPlay().register(RulesPayload.ID, RulesPayload.CODEC)

        ClientPlayNetworking.registerGlobalReceiver(RulesPayload.ID) { payload, _ ->
            runBlocking {
                ws?.send(Frame.Text(buildString {
                    append("rules")
                    append("|")
                    append(payload.raw)
                }))
            }
        }

        PayloadTypeRegistry.serverboundPlay().register(InfoPayload.ID, InfoPayload.CODEC)
        PayloadTypeRegistry.serverboundPlay().register(PublicKeyPayload.ID, PublicKeyPayload.CODEC)

        PayloadTypeRegistry.clientboundPlay().register(HeartBeatPayload.ID, HeartBeatPayload.CODEC)
        PayloadTypeRegistry.serverboundPlay().register(HeartBeatPayload.ID, HeartBeatPayload.CODEC)

        ClientPlayNetworking.registerGlobalReceiver(HeartBeatPayload.ID) { payload, context ->
            val player = context.client().player ?: return@registerGlobalReceiver
            runBlocking {
                ws?.send(Frame.Text(buildString {
                    append("heartbeat")
                    append("|")
                    append(payload.raw)
                    append("|")
                    append(player.x.toString())
                    append("|")
                    append(player.y.toString())
                    append("|")
                    append(player.z.toString())
                }))
            }
        }

        PayloadTypeRegistry.clientboundPlay().register(ScreenshotPayload.ID, ScreenshotPayload.CODEC)
        ClientPlayNetworking.registerGlobalReceiver(ScreenshotPayload.ID) { payload, _ ->
            runBlocking {
                ws?.send(Frame.Text(buildString {
                    append("screenshot")
                    append("|")
                    append(payload.raw)
                }))
            }
        }
        PayloadTypeRegistry.serverboundPlay().register(ImageChunkPayload.ID, ImageChunkPayload.CODEC)
    }


    @JvmRecord
    data class RequestPayload(val raw: String) : CustomPacketPayload {
        override fun type() = ID
        companion object {
            val ID = CustomPacketPayload.Type<RequestPayload>(Identifier.fromNamespaceAndPath("newblock", "request"))

            val CODEC = object: StreamCodec<FriendlyByteBuf, RequestPayload> {
                override fun encode(buf: FriendlyByteBuf, value: RequestPayload) {
                    buf.writeBytes(Base64.decode(value.raw))
                }

                override fun decode(buf: FriendlyByteBuf) = ByteArray(buf.readableBytes())
                    .apply { buf.readBytes(this) }
                    .let { RequestPayload(Base64.encode(it)) }
            }
        }
    }

    @JvmRecord
    data class PublicKeyPayload(val raw: String) : CustomPacketPayload {
        override fun type() = ID
        companion object {
            val ID = CustomPacketPayload.Type<PublicKeyPayload>(Identifier.fromNamespaceAndPath("newblock", "request"))

            val CODEC = object: StreamCodec<FriendlyByteBuf, PublicKeyPayload> {
                override fun encode(buf: FriendlyByteBuf, value: PublicKeyPayload) {
                    buf.writeBytes(Base64.decode(value.raw))
                }

                override fun decode(buf: FriendlyByteBuf) = ByteArray(buf.readableBytes())
                    .apply { buf.readBytes(this) }
                    .let { PublicKeyPayload(Base64.encode(it)) }
            }
        }
    }

    @JvmRecord
    data class InfoPayload(val raw: String) : CustomPacketPayload {
        override fun type() = ID
        companion object {
            val ID = CustomPacketPayload.Type<InfoPayload>(Identifier.fromNamespaceAndPath("newblock", "info"))

            val CODEC = object: StreamCodec<FriendlyByteBuf, InfoPayload> {
                    override fun encode(buf: FriendlyByteBuf, value: InfoPayload) {
                        buf.writeBytes(Base64.decode(value.raw))
                    }

                    override fun decode(buf: FriendlyByteBuf) = ByteArray(buf.readableBytes())
                        .apply { buf.readBytes(this) }
                        .let { InfoPayload(Base64.encode(it)) }
                }
        }
    }

    @JvmRecord
    data class RulesPayload(val raw: String) : CustomPacketPayload {
        override fun type() = ID
        companion object {
            val ID = CustomPacketPayload.Type<RulesPayload>(Identifier.fromNamespaceAndPath("newblock", "rules"))

            val CODEC = object: StreamCodec<FriendlyByteBuf, RulesPayload> {
                override fun encode(buf: FriendlyByteBuf, value: RulesPayload) {
                    buf.writeBytes(Base64.decode(value.raw))
                }

                override fun decode(buf: FriendlyByteBuf) = ByteArray(buf.readableBytes())
                    .apply { buf.readBytes(this) }
                    .let { RulesPayload(Base64.encode(it)) }
            }
        }
    }

    @JvmRecord
    data class HeartBeatPayload(val raw: String) : CustomPacketPayload {
        override fun type() = ID
        companion object {
            val ID = CustomPacketPayload.Type<HeartBeatPayload>(Identifier.fromNamespaceAndPath("newblock", "heartbeat"))

            val CODEC = object: StreamCodec<FriendlyByteBuf, HeartBeatPayload> {
                override fun encode(buf: FriendlyByteBuf, value: HeartBeatPayload) {
                    buf.writeBytes(Base64.decode(value.raw))
                }

                override fun decode(buf: FriendlyByteBuf) = ByteArray(buf.readableBytes())
                    .apply { buf.readBytes(this) }
                    .let { HeartBeatPayload(Base64.encode(it)) }
            }
        }
    }


    @JvmRecord
    data class ScreenshotPayload(val raw: String) : CustomPacketPayload {
        override fun type() = ID
        companion object {
            val ID = CustomPacketPayload.Type<ScreenshotPayload>(Identifier.fromNamespaceAndPath("newblock", "screenshot"))

            val CODEC = object: StreamCodec<FriendlyByteBuf, ScreenshotPayload> {
                override fun encode(buf: FriendlyByteBuf, value: ScreenshotPayload) {
                    buf.writeBytes(Base64.decode(value.raw))
                }

                override fun decode(buf: FriendlyByteBuf) = ByteArray(buf.readableBytes())
                    .apply { buf.readBytes(this) }
                    .let { ScreenshotPayload(Base64.encode(it)) }
            }
        }
    }

    @JvmRecord
    data class ImageChunkPayload(val raw: String) : CustomPacketPayload {
        override fun type() = ID
        companion object {
            val ID = CustomPacketPayload.Type<ImageChunkPayload>(Identifier.fromNamespaceAndPath("newblock", "imagechunk"))

            val CODEC = object: StreamCodec<FriendlyByteBuf, ImageChunkPayload> {
                override fun encode(buf: FriendlyByteBuf, value: ImageChunkPayload) {
                    buf.writeBytes(Base64.decode(value.raw))
                }

                override fun decode(buf: FriendlyByteBuf) = ByteArray(buf.readableBytes())
                    .apply { buf.readBytes(this) }
                    .let { ImageChunkPayload(Base64.encode(it)) }
            }
        }
    }

}
