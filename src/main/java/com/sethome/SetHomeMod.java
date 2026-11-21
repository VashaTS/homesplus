package com.sethome;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.sethome.HomeStorage.HomeData;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.Commands.CommandSelection;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.CachedUserNameToIdResolver;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserNameToIdResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SetHomeMod implements ModInitializer {
	private static final SuggestionProvider<CommandSourceStack> HOME_NAME_SUGGESTIONS = (ctx, builder) -> {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player != null) {
			return SharedSuggestionProvider.suggest(HomeStorage.get(ctx.getSource().getServer()).homeNames(player.getUUID()), builder);
		}
		return builder.buildFuture();
	};

	private static final SuggestionProvider<CommandSourceStack> PLAYER_NAME_SUGGESTIONS = (ctx, builder) ->
		SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), builder);

	@Override
	public void onInitialize() {
		SethomeConfig.get().loadOrCreate();
		ServerLifecycleEvents.SERVER_STARTING.register(server -> SethomeConfig.get().loadOrCreate());
		CommandRegistrationCallback.EVENT.register(this::registerCommands);
	}

	private void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, CommandSelection environment) {
		dispatcher.register(
			Commands.literal("sethome")
				.executes(ctx -> executeSetHome(ctx, HomeStorage.DEFAULT_HOME))
				.then(Commands.argument("name", StringArgumentType.word())
					.executes(ctx -> executeSetHome(ctx, StringArgumentType.getString(ctx, "name"))))
		);
		dispatcher.register(
			Commands.literal("home")
				.executes(ctx -> executeHome(ctx, HomeStorage.DEFAULT_HOME))
				.then(Commands.argument("name", StringArgumentType.word())
					.suggests(HOME_NAME_SUGGESTIONS)
					.executes(ctx -> executeHome(ctx, StringArgumentType.getString(ctx, "name"))))
		);
		dispatcher.register(
			Commands.literal("delhome")
				.executes(ctx -> executeDelHome(ctx, HomeStorage.DEFAULT_HOME))
				.then(Commands.argument("name", StringArgumentType.word())
					.suggests(HOME_NAME_SUGGESTIONS)
					.executes(ctx -> executeDelHome(ctx, StringArgumentType.getString(ctx, "name"))))
		);
		dispatcher.register(
			Commands.literal("listhomes")
				.executes(this::executeListOwnHomes)
				.then(Commands.argument("player", StringArgumentType.word())
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.suggests(PLAYER_NAME_SUGGESTIONS)
					.executes(this::executeListOtherHomes))
		);
		dispatcher.register(
			Commands.literal("homesconfig")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("reload").executes(this::executeReloadConfig))
		);
	}

	private int executeSetHome(CommandContext<CommandSourceStack> ctx, String rawName) {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("This command can only be used by a player."));
			return 0;
		}
		if (!HomeStorage.isValidName(rawName)) {
			source.sendFailure(Component.literal("§cInvalid home name. Use 1-32 letters, digits, underscores or hyphens."));
			return 0;
		}

		String name = HomeStorage.normalizeName(rawName);
		HomeStorage storage = HomeStorage.get(source.getServer());
		boolean overwriting = storage.hasHome(player.getUUID(), name);
		if (!overwriting) {
			boolean isOp = source.getServer().getPlayerList().isOp(player.nameAndId());
			if (!SethomeConfig.get().canCreateNewHome(storage.homeCount(player.getUUID()), isOp)) {
				int max = SethomeConfig.get().maxHomes();
				source.sendFailure(Component.literal(
					"§cYou have reached the maximum of §f" + max + "§c homes. Delete one with §f/delhome <name>§c or overwrite an existing name."
				));
				return 0;
			}
		}

		Vec3 pos = player.position();
		HomeData data = new HomeData(pos.x, pos.y, pos.z, player.getYRot(), player.getXRot(), player.level().dimension().identifier().toString());
		storage.setHome(player.getUUID(), name, data);
		String dim = friendlyDimName(data.dimension());
		if (HomeStorage.DEFAULT_HOME.equals(name)) {
			source.sendSuccess(
				() -> Component.literal(String.format("§aHome set at §f(%.1f, %.1f, %.1f)§a in §f%s§a.", pos.x, pos.y, pos.z, dim)),
				false
			);
		} else {
			source.sendSuccess(
				() -> Component.literal(String.format("§aHome §f%s§a set at §f(%.1f, %.1f, %.1f)§a in §f%s§a.", name, pos.x, pos.y, pos.z, dim)),
				false
			);
		}
		return 1;
	}

	private int executeHome(CommandContext<CommandSourceStack> ctx, String rawName) {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("This command can only be used by a player."));
			return 0;
		}
		if (!HomeStorage.isValidName(rawName)) {
			source.sendFailure(Component.literal("§cInvalid home name. Use 1-32 letters, digits, underscores or hyphens."));
			return 0;
		}

		String name = HomeStorage.normalizeName(rawName);
		MinecraftServer server = source.getServer();
		HomeData home = HomeStorage.get(server).getHome(player.getUUID(), name);
		if (home == null) {
			if (HomeStorage.DEFAULT_HOME.equals(name)) {
				source.sendFailure(Component.literal("§cYou haven't set a home yet! Use §f/sethome§c first."));
			} else {
				source.sendFailure(Component.literal("§cYou don't have a home named §f" + name + "§c."));
			}
			return 0;
		}

		ResourceKey<Level> targetDim = home.dimensionKey();
		ServerLevel targetWorld = server.getLevel(targetDim);
		if (targetWorld == null) {
			source.sendFailure(Component.literal("§cCould not find your home dimension (§f" + home.dimension() + "§c)."));
			return 0;
		}

		player.teleportTo(targetWorld, home.x(), home.y(), home.z(), Set.of(), home.yaw(), home.pitch(), true);
		String dim = friendlyDimName(home.dimension());
		if (HomeStorage.DEFAULT_HOME.equals(name)) {
			source.sendSuccess(
				() -> Component.literal(String.format("§aTeleported to your home at §f(%.1f, %.1f, %.1f)§a in §f%s§a.", home.x(), home.y(), home.z(), dim)),
				false
			);
		} else {
			source.sendSuccess(
				() -> Component.literal(String.format("§aTeleported to home §f%s§a at §f(%.1f, %.1f, %.1f)§a in §f%s§a.", name, home.x(), home.y(), home.z(), dim)),
				false
			);
		}
		return 1;
	}

	private int executeDelHome(CommandContext<CommandSourceStack> ctx, String rawName) {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("This command can only be used by a player."));
			return 0;
		}
		if (!HomeStorage.isValidName(rawName)) {
			source.sendFailure(Component.literal("§cInvalid home name. Use 1-32 letters, digits, underscores or hyphens."));
			return 0;
		}

		String name = HomeStorage.normalizeName(rawName);
		boolean deleted = HomeStorage.get(source.getServer()).deleteHome(player.getUUID(), name);
		if (!deleted) {
			if (HomeStorage.DEFAULT_HOME.equals(name)) {
				source.sendFailure(Component.literal("§cYou haven't set a home yet! Use §f/sethome§c first."));
			} else {
				source.sendFailure(Component.literal("§cYou don't have a home named §f" + name + "§c."));
			}
			return 0;
		}

		if (HomeStorage.DEFAULT_HOME.equals(name)) {
			source.sendSuccess(() -> Component.literal("§aDeleted your home."), false);
		} else {
			source.sendSuccess(() -> Component.literal("§aDeleted home §f" + name + "§a."), false);
		}
		return 1;
	}

	private int executeListOwnHomes(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("This command can only be used by a player. Ops can use §f/listhomes <player>§c."));
			return 0;
		}
		return sendHomeList(source, "Your homes", HomeStorage.get(source.getServer()).getHomes(player.getUUID()), true);
	}

	private int executeListOtherHomes(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		String query = StringArgumentType.getString(ctx, "player");
		NameAndId profile = resolvePlayer(source.getServer(), query);
		if (profile == null) {
			source.sendFailure(Component.literal("§cNo player found with the name §f" + query + "§c. They must have joined this server before."));
			return 0;
		}
		return sendHomeList(source, "Homes for " + profile.name(), HomeStorage.get(source.getServer()).getHomes(profile.id()), false);
	}

	private int executeReloadConfig(CommandContext<CommandSourceStack> ctx) {
		SethomeConfig.get().loadOrCreate();
		SethomeConfig config = SethomeConfig.get();
		ctx.getSource().sendSuccess(
			() -> Component.literal(String.format(
				"§aReloaded sethome config (§fmaxHomes=%d§a, §fopsBypassLimit=%s§a).",
				config.maxHomes(),
				config.opsBypassLimit()
			)),
			true
		);
		return 1;
	}

	private static NameAndId resolvePlayer(MinecraftServer server, String name) {
		ServerPlayer online = server.getPlayerList().getPlayerByName(name);
		if (online != null) {
			return online.nameAndId();
		}
		return findCachedPlayer(server.services().nameToIdCache(), name);
	}

	/**
	 * Looks up a player who has already joined (user cache only).
	 * Does not call Mojang or invent an offline-mode profile for unknown names.
	 */
	private static NameAndId findCachedPlayer(UserNameToIdResolver cache, String name) {
		if (cache instanceof CachedUserNameToIdResolver cached) {
			NameAndId local = getLocalCacheEntry(cached, name);
			if (local != null) {
				return local;
			}
			return null;
		}
		return cache.get(name).orElse(null);
	}

	private static NameAndId getLocalCacheEntry(CachedUserNameToIdResolver cache, String name) {
		try {
			Field field = CachedUserNameToIdResolver.class.getDeclaredField("profilesByName");
			field.setAccessible(true);
			@SuppressWarnings("unchecked")
			Map<String, ?> byName = (Map<String, ?>) field.get(cache);
			Object info = byName.get(name.toLowerCase(Locale.ROOT));
			if (info == null) {
				return null;
			}
			Method method = info.getClass().getDeclaredMethod("nameAndId");
			method.setAccessible(true);
			return (NameAndId) method.invoke(info);
		} catch (ReflectiveOperationException e) {
			SethomeConfig.LOGGER.warn("Could not read the local user cache; falling back to a full lookup", e);
			return cache.get(name).orElse(null);
		}
	}

	static int sendHomeList(CommandSourceStack source, String title, Map<String, HomeData> homes, boolean clickable) {
		if (homes.isEmpty()) {
			source.sendFailure(Component.literal("§cNo homes to list."));
			return 0;
		}
		source.sendSuccess(() -> formatHomeList(title, homes, clickable), false);
		return homes.size();
	}

	public static Component formatHomeList(String title, Map<String, HomeData> homes, boolean clickable) {
		MutableComponent result = Component.literal(title + " ").withStyle(ChatFormatting.GREEN)
			.append(Component.literal("(" + homes.size() + ")").withStyle(ChatFormatting.WHITE))
			.append(Component.literal(":").withStyle(ChatFormatting.GREEN));
		homes.forEach((name, home) -> result.append(Component.literal("\n")).append(formatHomeEntry(name, home, clickable)));
		return result;
	}

	public static MutableComponent formatHomeEntry(String name, HomeData home, boolean clickable) {
		long x = Math.round(home.x());
		long y = Math.round(home.y());
		long z = Math.round(home.z());
		MutableComponent line = Component.literal("• ").withStyle(ChatFormatting.GRAY)
			.append(Component.literal(name).withStyle(ChatFormatting.AQUA))
			.append(Component.literal(" — ").withStyle(ChatFormatting.GRAY))
			.append(Component.literal(friendlyDimName(home.dimension())).withStyle(ChatFormatting.WHITE))
			.append(Component.literal(" (" + x + ", " + y + ", " + z + ")").withStyle(ChatFormatting.GRAY));
		if (clickable) {
			line = line.withStyle(style -> style
				.withClickEvent(new ClickEvent.RunCommand("/home " + name))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to teleport"))));
		}
		return line;
	}

	static String friendlyDimName(String dimId) {
		return switch (dimId) {
			case "minecraft:overworld" -> "Overworld";
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "The End";
			default -> dimId;
		};
	}
}
