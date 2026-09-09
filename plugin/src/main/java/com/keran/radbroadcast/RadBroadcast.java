package com.keran.radbroadcast;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * RadBroadcast - 服务端广播音频插件（Paper 1.20.1）
 * 通过 Fabric 插件消息通道 "radb:play" 通知客户端播放音频直链。
 */
public class RadBroadcast extends JavaPlugin {
	public static final String CHANNEL = "radb:play";

	private static RadBroadcast instance;

	@Override
	public void onEnable() {
		instance = this;
		Bukkit.getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
		RadCommand cmd = new RadCommand(this);
		getCommand("rad").setExecutor(cmd);
		getCommand("rad").setTabCompleter(cmd);
		getLogger().info("RadBroadcast 已启用，通道: " + CHANNEL);
	}

	@Override
	public void onDisable() {
		Bukkit.getMessenger().unregisterOutgoingPluginChannel(this, CHANNEL);
	}

	/** 通知单个客户端立即播放音频 */
	public void playToPlayer(Player player, String url) {
		if (player == null || !player.isOnline()) return;
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(bos);
			byte[] bytes = url.getBytes(StandardCharsets.UTF_8);
			dos.writeInt(bytes.length);
			dos.write(bytes);
			dos.flush();
			player.sendPluginMessage(this, CHANNEL, bos.toByteArray());
			getLogger().info("→ 播放指令已发送 " + player.getName() + " : " + url);
		} catch (Exception e) {
			getLogger().warning("发送播放指令失败: " + e.getMessage());
		}
	}

	/** 世界名解析：直接按 Bukkit 世界名，找不到时尝试 Multiverse-Core 别名（软依赖，反射避免硬依赖） */
	public World resolveWorld(String name) {
		World w = Bukkit.getWorld(name);
		if (w != null) return w;
		for (World world : Bukkit.getWorlds()) {
			if (world.getName().equalsIgnoreCase(name)) return world;
		}
		// Multiverse-Core 别名解析（未安装则忽略）
		try {
			Object mv = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
			if (mv != null) {
				Object mgr = mv.getClass().getMethod("getMVWorldManager").invoke(mv);
				Object mvw = mgr.getClass().getMethod("getMVWorld", String.class).invoke(mgr, name);
				if (mvw != null) {
					String real = (String) mvw.getClass().getMethod("getName").invoke(mvw);
					World w2 = Bukkit.getWorld(real);
					if (w2 != null) return w2;
				}
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	public static RadBroadcast get() {
		return instance;
	}
}
