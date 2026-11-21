package com.sethome.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Records {@link ServerPlayer#sendSystemMessage} output for gametests (via mixin). */
public final class ChatCapture {
	private static final Map<UUID, List<String>> MESSAGES = new ConcurrentHashMap<>();

	private ChatCapture() {
	}

	public static void start(ServerPlayer player) {
		MESSAGES.put(player.getUUID(), new CopyOnWriteArrayList<>());
	}

	public static void record(ServerPlayer player, Component message) {
		List<String> list = MESSAGES.get(player.getUUID());
		if (list != null) {
			list.add(message.getString());
		}
	}

	public static List<String> snapshot(ServerPlayer player) {
		List<String> list = MESSAGES.get(player.getUUID());
		return list == null ? List.of() : List.copyOf(list);
	}

	public static void clear(ServerPlayer player) {
		List<String> list = MESSAGES.get(player.getUUID());
		if (list != null) {
			list.clear();
		}
	}

	public static void stop(ServerPlayer player) {
		MESSAGES.remove(player.getUUID());
	}

	public static boolean anyMatch(ServerPlayer player, String needle) {
		return snapshot(player).stream().anyMatch(s -> s.contains(needle));
	}

	public static String dump(ServerPlayer player) {
		return new ArrayList<>(snapshot(player)).toString();
	}
}
