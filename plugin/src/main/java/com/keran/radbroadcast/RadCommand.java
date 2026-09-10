package com.keran.radbroadcast;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 命令解析：
 *   /rad play <x> <y> <z> <world> <range> <音频直链>
 *   /rad play <玩家(名/uuid/%占位符%/@a/@p/@r)> <音频直链>
 *   /rad play all <音频直链>
 */
public class RadCommand implements CommandExecutor, TabCompleter {
	private final RadBroadcast plugin;

	public RadCommand(RadBroadcast plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
							 @NotNull String label, @NotNull String[] args) {
		// 顶层子命令：wgplay（WorldGuard 区域广播）与 play 同级，必须在 play 守卫之前分流
		// 注意：Bukkit 按空格切分参数，链接不能含空格；含空格会被切碎拼成错误 URL，
		// 这里检测到多余参数时直接提示，避免静默拼错后播放诡异的 404。
		if (args.length >= 1 && args[0].equalsIgnoreCase("wgplay")) {
			if (args.length < 3) {
				sender.sendMessage("§c用法: /rad wgplay <WorldGuard区域名> <音频直链>");
				return true;
			}
			if (args.length > 3) {
				sender.sendMessage("§c音频直链中疑似含空格（Minecraft 命令无法保留空格）。");
				sender.sendMessage("§7请先做 URL 编码（空格写成 %20），或改用不含空格的直链。");
				return true;
			}
			try {
				return playWorldGuardRegion(sender, args[1], args[2]);
			} catch (Exception e) {
				sender.sendMessage("§c执行出错: " + e.getMessage());
				return true;
			}
		}
		if (args.length < 1 || !args[0].equalsIgnoreCase("play")) {
			sender.sendMessage("§c用法: §f/rad play <x> <y> <z> <世界> <范围> <音频直链>");
			sender.sendMessage("§f/rad play <玩家|@a|@p|@r> <音频直链>   §f/rad play all <音频直链>");
			sender.sendMessage("§f/rad wgplay <WorldGuard区域名> <音频直链>");
			return true;
		}
		String[] rest = new String[args.length - 1];
		System.arraycopy(args, 1, rest, 0, rest.length);
		if (rest.length < 2) {
			sender.sendMessage("§c参数不足。");
			return true;
		}
		try {
			// ---- 区域播放：前 3 个参数为数字 => /rad play x y z world range url
			if (isDouble(rest[0]) && isDouble(rest[1]) && isDouble(rest[2])) {
				if (rest.length < 6) {
					sender.sendMessage("§c区域播放需要: x y z 世界 范围 音频直链");
					return true;
				}
				if (rest.length > 6) {
					sender.sendMessage("§c音频直链中疑似含空格（Minecraft 命令无法保留空格）。");
					sender.sendMessage("§7请先做 URL 编码（空格写成 %20），或改用不含空格的直链。");
					return true;
				}
				double x = parseDouble(rest[0]);
				double y = parseDouble(rest[1]);
				double z = parseDouble(rest[2]);
				String worldName = rest[3];
				double range = parseDouble(rest[4]);
				String url = join(rest, 5);
				World world = plugin.resolveWorld(worldName);
				if (world == null) {
					sender.sendMessage("§c找不到世界: " + worldName);
					return true;
				}
				return playRegion(sender, new Location(world, x, y, z), range, url);
			}
			// ---- all：全体玩家
			if (rest[0].equalsIgnoreCase("all")) {
				if (rest.length > 2) {
					sender.sendMessage("§c音频直链中疑似含空格（Minecraft 命令无法保留空格）。");
					sender.sendMessage("§7请先做 URL 编码（空格写成 %20），或改用不含空格的直链。");
					return true;
				}
				String url = join(rest, 1);
				return playPlayers(sender, new ArrayList<>(Bukkit.getOnlinePlayers()), url);
			}
			// ---- 指定玩家
			String target = rest[0];
			if (rest.length > 2) {
				sender.sendMessage("§c音频直链中疑似含空格（Minecraft 命令无法保留空格）。");
				sender.sendMessage("§7请先做 URL 编码（空格写成 %20），或改用不含空格的直链。");
				return true;
			}
			String url = join(rest, 1);
			return playTarget(sender, target, url);
		} catch (Exception e) {
			sender.sendMessage("§c执行出错: " + e.getMessage());
			return true;
		}
	}

	/** WorldGuard 区域播放：检查每名玩家所在世界中的同名区域（软依赖） */
	private boolean playWorldGuardRegion(CommandSender sender, String regionName, String url) {
		if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) {
			sender.sendMessage("§c未安装 WorldGuard，无法使用区域播放");
			return true;
		}
		List<Player> targets = new ArrayList<>();
		String id = regionName.toLowerCase(Locale.ROOT);
		try {
			for (Player p : Bukkit.getOnlinePlayers()) {
				RegionManager rm = WorldGuard.getInstance().getPlatform().getRegionContainer()
						.get(BukkitAdapter.adapt(p.getWorld()));
				if (rm == null) continue;
				ProtectedRegion region = rm.getRegion(id);
				if (region == null) continue;
				Location l = p.getLocation();
				if (region.contains(BlockVector3.at(l.getBlockX(), l.getBlockY(), l.getBlockZ()))) {
					targets.add(p);
				}
			}
		} catch (Throwable t) {
			sender.sendMessage("§cWorldGuard 区域查询失败: " + t.getMessage());
			return true;
		}
		if (targets.isEmpty()) {
			sender.sendMessage("§7没有玩家位于区域 §f" + regionName + " §7内（或该区域不存在）");
			return true;
		}
		return playPlayers(sender, targets, url);
	}

	private boolean playRegion(CommandSender sender, Location center, double range, String url) {
		List<Player> targets = new ArrayList<>();
		World w = center.getWorld();
		double r2 = range * range;
		for (Player p : Bukkit.getOnlinePlayers()) {
			if (!p.getWorld().equals(w)) continue;
			Location l = p.getLocation();
			double dx = l.getX() - center.getX();
			double dy = l.getY() - center.getY();
			double dz = l.getZ() - center.getZ();
			if (dx * dx + dy * dy + dz * dz <= r2) targets.add(p);
		}
		return playPlayers(sender, targets, url);
	}

	private boolean playTarget(CommandSender sender, String target, String url) throws Exception {
		// PAPI 占位符展开（sender 为玩家时有上下文；无 PAPI 则原样返回）
		String resolved = PlaceholderApiBridge.setPlaceholders(sender, target);
		if (!resolved.equals(target) && isLookLikePlayerName(resolved) && Bukkit.getPlayerExact(resolved) != null) {
			return playPlayers(sender, List.of(Bukkit.getPlayerExact(resolved)), url);
		}
		switch (target.toLowerCase(Locale.ROOT)) {
			case "@a": {
				return playPlayers(sender, new ArrayList<>(Bukkit.getOnlinePlayers()), url);
			}
			case "@p": {
				if (!(sender instanceof Player p)) {
					sender.sendMessage("§c@p 需要由玩家执行（控制台请用 all 或指定玩家）");
					return true;
				}
				return playPlayers(sender, List.of(p), url);
			}
			case "@r": {
				List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
				if (online.isEmpty()) {
					sender.sendMessage("§c没有在线玩家");
					return true;
				}
				return playPlayers(sender, List.of(online.get((int) (Math.random() * online.size()))), url);
			}
			default: {
				Player player = resolvePlayer(resolved);
				if (player == null) {
					sender.sendMessage("§c找不到玩家: " + target);
					return true;
				}
				return playPlayers(sender, List.of(player), url);
			}
		}
	}

	private boolean playPlayers(CommandSender sender, List<Player> targets, String url) {
		if (targets.isEmpty()) {
			sender.sendMessage("§7范围内没有玩家（或目标玩家不在线）");
			return true;
		}
		int sent = 0;
		for (Player p : targets) {
			plugin.playToPlayer(p, url);
			sent++;
		}
		sender.sendMessage("§a已向 " + sent + " 名玩家发送播放指令");
		return true;
	}

	/** 玩家解析：精确名(忽略大小写) → 离线存储名 → uuid */
	private Player resolvePlayer(String s) {
		Player p = Bukkit.getPlayerExact(s);
		if (p != null) return p;
		for (Player online : Bukkit.getOnlinePlayers()) {
			if (online.getName().equalsIgnoreCase(s)) return online;
		}
		try {
			UUID uuid = UUID.fromString(s);
			Player pu = Bukkit.getPlayer(uuid);
			if (pu != null) return pu;
		} catch (Exception ignored) {
		}
		return null;
	}

	private static boolean isLookLikePlayerName(String s) {
		return !s.startsWith("%") && !s.contains(" ") && !s.contains("&");
	}

	private static boolean isDouble(String s) {
		try {
			Double.parseDouble(s.trim());
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private static double parseDouble(String s) {
		return Double.parseDouble(s.trim());
	}

	private static String join(String[] args, int from) {
		StringBuilder sb = new StringBuilder();
		for (int i = from; i < args.length; i++) {
			if (i > from) sb.append(' ');
			sb.append(args[i]);
		}
		return sb.toString();
	}

	@Override
	public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
									   @NotNull String alias, @NotNull String[] args) {
		List<String> list = new ArrayList<>();
		if (args.length == 2 && args[0].equalsIgnoreCase("play")) {
			list.add("all");
			list.add("@a");
			list.add("@p");
			list.add("@r");
			for (Player p : Bukkit.getOnlinePlayers()) list.add(p.getName());
		}
		if (args.length == 1) {
			list.add("play");
			list.add("wgplay");
		}
		return list;
	}
}
