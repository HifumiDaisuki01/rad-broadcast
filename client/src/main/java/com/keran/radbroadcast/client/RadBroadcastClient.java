package com.keran.radbroadcast.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;

/**
 * 广播客户端入口：注册插件消息通道 radb:play，收到服务端指令后立即播放音频直链。
 */
public class RadBroadcastClient implements ClientModInitializer {
	public static final ResourceLocation PLAY_CHANNEL = new ResourceLocation("radb", "play");

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(PLAY_CHANNEL, (client, handler, buf, responseSender) -> {
			String url;
			try {
				int len = buf.readInt();
				if (len < 0 || len > 65535) return;
				byte[] bytes = new byte[len];
				buf.readBytes(bytes);
				url = new String(bytes, StandardCharsets.UTF_8);
			} catch (Exception e) {
				return;
			}
			final String target = url;
			client.execute(() -> RadioPlayer.INSTANCE.play(target));
		});
	}
}
