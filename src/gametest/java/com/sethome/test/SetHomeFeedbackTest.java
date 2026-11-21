package com.sethome.test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sethome.HomeStorage;
import com.sethome.HomeStorage.HomeData;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Non-op players must always see command confirmations and {@code /listhomes},
 * including in the End and Nether, and when {@code sendCommandFeedback} is false.
 */
public class SetHomeFeedbackTest {
	@GameTest(maxTicks = 120)
	public void nonOpListhomesInEndWithFeedbackDisabled(GameTestHelper helper) {
		ServerPlayer player = nonOpPlayer(helper);
		MinecraftServer server = helper.getLevel().getServer();
		disableCommandFeedback(helper);

		ServerLevel end = server.getLevel(Level.END);
		helper.assertTrue(end != null, "the End should be loaded");
		Vec3 pos = new Vec3(8.5, 64.0, 12.5);
		player.teleportTo(end, pos.x, pos.y, pos.z, Set.of(), 45.0F, 5.0F, true);
		helper.assertTrue(player.level().dimension().equals(Level.END), "player should be in the End, was " + player.level().dimension());

		ChatCapture.start(player);
		helper.assertTrue(runAs(player, "sethome ender") == 1, "sethome ender should succeed for a non-op");
		HomeData saved = HomeStorage.get(server).getHome(player.getUUID(), "ender");
		helper.assertTrue(saved != null && saved.dimension().equals("minecraft:the_end"),
			"home should be stored under 'ender' in the End, was " + (saved == null ? "null" : saved.dimension()));
		helper.assertTrue(ChatCapture.anyMatch(player, "ender") && ChatCapture.anyMatch(player, "The End"),
			"sethome should confirm in chat, got " + ChatCapture.dump(player));

		ChatCapture.clear(player);
		helper.assertTrue(runAs(player, "listhomes") == 1, "listhomes should list the End home");
		helper.assertTrue(ChatCapture.anyMatch(player, "Your homes") && ChatCapture.anyMatch(player, "ender")
				&& ChatCapture.anyMatch(player, "The End"),
			"listhomes should print the list in chat for a non-op when sendCommandFeedback is false, got "
				+ ChatCapture.dump(player));
		ChatCapture.stop(player);
		helper.succeed();
	}

	@GameTest(maxTicks = 120)
	public void nonOpListhomesInNetherWithFeedbackDisabled(GameTestHelper helper) {
		ServerPlayer player = nonOpPlayer(helper);
		MinecraftServer server = helper.getLevel().getServer();
		disableCommandFeedback(helper);

		ServerLevel nether = server.getLevel(Level.NETHER);
		helper.assertTrue(nether != null, "the Nether should be loaded");
		Vec3 pos = new Vec3(20.5, 80.0, 4.5);
		player.teleportTo(nether, pos.x, pos.y, pos.z, Set.of(), 0.0F, 0.0F, true);
		helper.assertTrue(player.level().dimension().equals(Level.NETHER), "player should be in the Nether, was " + player.level().dimension());

		ChatCapture.start(player);
		helper.assertTrue(runAs(player, "sethome nether_hub") == 1, "sethome nether_hub should succeed");
		helper.assertTrue(ChatCapture.anyMatch(player, "nether_hub") && ChatCapture.anyMatch(player, "Nether"),
			"sethome should confirm in chat, got " + ChatCapture.dump(player));

		ChatCapture.clear(player);
		helper.assertTrue(runAs(player, "listhomes") == 1, "listhomes should list the Nether home");
		helper.assertTrue(ChatCapture.anyMatch(player, "Your homes") && ChatCapture.anyMatch(player, "nether_hub")
				&& ChatCapture.anyMatch(player, "Nether"),
			"listhomes should print the Nether home in chat, got " + ChatCapture.dump(player));
		ChatCapture.stop(player);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void nonOpHomeAndDelhomeFeedbackWithGameruleOff(GameTestHelper helper) {
		ServerPlayer player = nonOpPlayer(helper);
		disableCommandFeedback(helper);
		ChatCapture.start(player);

		helper.assertTrue(runAs(player, "sethome") == 1, "sethome should succeed");
		helper.assertTrue(ChatCapture.anyMatch(player, "Home set"), "sethome confirmation missing: " + ChatCapture.dump(player));

		ChatCapture.clear(player);
		helper.assertTrue(runAs(player, "home") == 1, "home should succeed");
		helper.assertTrue(ChatCapture.anyMatch(player, "Teleported"), "home confirmation missing: " + ChatCapture.dump(player));

		ChatCapture.clear(player);
		helper.assertTrue(runAs(player, "delhome") == 1, "delhome should succeed");
		helper.assertTrue(ChatCapture.anyMatch(player, "Deleted"), "delhome confirmation missing: " + ChatCapture.dump(player));

		ChatCapture.stop(player);
		helper.succeed();
	}

	@GameTest
	public void nonOpCannotListOtherPlayers(GameTestHelper helper) {
		ServerPlayer player = nonOpPlayer(helper);
		ChatCapture.start(player);
		int result = runAsAllowingParseFailure(player, "listhomes SomeoneElse");
		helper.assertTrue(result == 0, "non-op /listhomes <player> should be rejected, got " + result);
		helper.assertTrue(!ChatCapture.anyMatch(player, "Homes for"),
			"non-op should not receive another player's list, got " + ChatCapture.dump(player));
		ChatCapture.stop(player);
		helper.succeed();
	}

	private static ServerPlayer nonOpPlayer(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		MinecraftServer server = helper.getLevel().getServer();
		if (server.getPlayerList().isOp(player.nameAndId())) {
			server.getPlayerList().deop(player.nameAndId());
		}
		helper.assertTrue(!server.getPlayerList().isOp(player.nameAndId()), "test player should not be an op");
		helper.assertTrue(!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER),
			"test player should be permission level 0, got " + player.permissions());
		if (player.permissions() instanceof LevelBasedPermissionSet levelSet) {
			helper.assertTrue(levelSet.level() == PermissionLevel.ALL, "expected ALL (0), got " + levelSet.level());
		}
		return player;
	}

	private static void disableCommandFeedback(GameTestHelper helper) {
		helper.getLevel().getGameRules().set(GameRules.SEND_COMMAND_FEEDBACK, false, helper.getLevel().getServer());
		helper.assertTrue(!helper.getLevel().getGameRules().get(GameRules.SEND_COMMAND_FEEDBACK),
			"sendCommandFeedback should be false");
	}

	private static int runAs(ServerPlayer player, String command) {
		try {
			return player.level().getServer().getCommands().getDispatcher()
				.execute(command, player.createCommandSourceStack());
		} catch (CommandSyntaxException e) {
			throw new RuntimeException("Failed to run /" + command + ": " + e.getMessage(), e);
		}
	}

	private static int runAsAllowingParseFailure(ServerPlayer player, String command) {
		try {
			return player.level().getServer().getCommands().getDispatcher()
				.execute(command, player.createCommandSourceStack());
		} catch (CommandSyntaxException e) {
			return 0;
		}
	}
}
