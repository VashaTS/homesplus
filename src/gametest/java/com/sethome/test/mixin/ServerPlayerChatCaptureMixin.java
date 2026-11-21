package com.sethome.test.mixin;

import com.sethome.test.ChatCapture;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class ServerPlayerChatCaptureMixin {
	@Inject(method = "sendSystemMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("HEAD"))
	private void sethome$captureChat(Component message, boolean overlay, CallbackInfo ci) {
		if (!overlay) {
			ChatCapture.record((ServerPlayer) (Object) this, message);
		}
	}
}
