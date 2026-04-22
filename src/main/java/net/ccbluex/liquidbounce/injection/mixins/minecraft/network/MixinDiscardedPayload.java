/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */

package net.ccbluex.liquidbounce.injection.mixins.minecraft.network;

import net.ccbluex.liquidbounce.features.module.modules.fun.ModuleNewBlockProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DiscardedPayload.class)
public class MixinDiscardedPayload {

    @Inject(method = "codec", at = @At("HEAD"), cancellable = true)
    private static void codec(Identifier id, int maxSize, CallbackInfoReturnable<StreamCodec<FriendlyByteBuf, DiscardedPayload>> cir) {
        cir.setReturnValue(
            CustomPacketPayload.codec((discardedPayload, friendlyByteBuf) -> {
            }, (friendlyByteBuf) -> {
                if (ModuleNewBlockProtocol.INSTANCE.getEnabled()) {
                    ModuleNewBlockProtocol.INSTANCE.onReceiveCustomPayload(id, friendlyByteBuf);
                }

                int j = friendlyByteBuf.readableBytes();
                if (j >= 0 && j <= maxSize) {
                    friendlyByteBuf.skipBytes(j);
                    return new DiscardedPayload(id);
                } else {
                    throw new IllegalArgumentException("Payload may not be larger than " + maxSize + " bytes");
                }
            })
        );
        cir.cancel();
    }

}
