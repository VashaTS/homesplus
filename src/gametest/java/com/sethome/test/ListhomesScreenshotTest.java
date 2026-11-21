package com.sethome.test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.LinkedHashMap;
import com.sethome.SetHomeMod;
import com.sethome.HomeStorage;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

/**
 * Singleplayer client screenshot of {@code /listhomes} for the Modrinth gallery.
 * Run with: xvfb-run -a ./gradlew runClientGameTest
 */
public class ListhomesScreenshotTest implements FabricClientGameTest {
	private static final Path OUT = Path.of(
		System.getProperty("sethome.galleryShot", "/workspace/sethome-26.2-out/publish")
	);
	private static final List<String> SHOWN = new CopyOnWriteArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> SHOWN.add(message.getString()));
		try (TestSingleplayerContext world = context.worldBuilder()
				.adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
				.create()) {
			TestServerContext server = world.getServer();
			context.runOnClient(mc -> {
				mc.getWindow().setWindowed(1280, 720);
				mc.options.guiScale().set(3);
				int scale = mc.getWindow().calculateScale(3, mc.isEnforceUnicode());
				mc.getWindow().setGuiScale(scale);
				mc.options.chatOpacity().set(1.0);
				mc.options.chatWidth().set(1.0);
				mc.options.chatHeightFocused().set(1.0);
			});
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("gamerule doMobSpawning false");
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("gamerule doWeatherCycle false");
			server.runCommand("gamerule send_command_feedback false");
			server.runCommand("gamerule announce_advancements false");
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("forceload add -16 -16 16 16");
			// A small overworld meadow so the background is a real in-game view, not a loading screen.
			server.runCommand("fill -16 80 -16 16 80 16 minecraft:grass_block");
			server.runCommand("fill -16 81 -16 16 96 16 minecraft:air");
			server.runCommand("fill -8 80 -12 8 80 -4 minecraft:grass_block");
			placeTree(server, -6, 81, 5);
			placeTree(server, 7, 81, 3);
			placeTree(server, 4, 81, -8);
			server.runCommand("setblock 2 81 -3 minecraft:campfire[lit=true]");
			server.runCommand("setblock -2 81 -2 minecraft:oak_log");
			server.runCommand("setblock -2 82 -2 minecraft:oak_log");
			server.runCommand("setblock -3 81 -2 minecraft:crafting_table");
			server.runCommand("fill 6 81 -6 8 81 -4 minecraft:short_grass");
			server.runCommand("fill -5 81 -6 -3 81 -5 minecraft:poppy");
			server.runCommand("gamemode creative @a");

			// default home
			server.runCommand("tp @p 0 81 0 200 8");
			context.waitTicks(20);
			runAsPlayer(server, "sethome");

			// base, a little further in the meadow
			server.runCommand("tp @p -3 81 -2 160 10");
			context.waitTicks(5);
			runAsPlayer(server, "sethome base");

			// mine, in a short stone shaft under the meadow
			server.runCommand("fill 10 70 8 14 74 12 minecraft:stone");
			server.runCommand("fill 11 71 9 13 73 11 minecraft:air");
			server.runCommand("setblock 11 71 9 minecraft:torch");
			server.runCommand("tp @p 12 71 10 90 5");
			context.waitTicks(5);
			runAsPlayer(server, "sethome mine");

			// nether_hub on a small platform in the Nether
			server.runCommand("execute in minecraft:the_nether run forceload add 0 0");
			context.waitTicks(20);
			server.runCommand("execute in minecraft:the_nether run fill -2 80 -2 2 80 2 minecraft:polished_blackstone_bricks");
			server.runCommand("execute in minecraft:the_nether run tp @p 0 81 0 0 0");
			context.waitTicks(40);
			runAsPlayer(server, "sethome nether_hub");

			// Back to the meadow for the shot, looking across the trees and campfire
			server.runCommand("execute in minecraft:overworld run tp @p 0.5 81.0 0.5 200 8");
			context.waitTicks(40);

			context.runOnClient(mc -> {
				mc.gui.toastManager().clear();
				mc.gui.hud.getChat().clearMessages(false);
			});
			SHOWN.clear();
			context.waitTicks(5);
			runAsPlayer(server, "listhomes");
			context.waitTicks(20);
			System.out.println("[sethome-test] chat shown after command=" + SHOWN);
			// The integrated client-gametest connection does not deliver sendSuccess
			// packets to chat, so add the same formatted list the command produces.
			if (SHOWN.stream().noneMatch(s -> s.contains("Your homes"))) {
				var homes = server.computeOnServer(s -> {
					var player = s.getPlayerList().getPlayers().getFirst();
					return new LinkedHashMap<>(HomeStorage.get(s).getHomes(player.getUUID()));
				});
				context.runOnClient(mc -> mc.gui.hud.getChat()
					.addServerSystemMessage(SetHomeMod.formatHomeList("Your homes", homes, true)));
				context.waitTicks(5);
			}
			System.out.println("[sethome-test] chat shown after ensure=" + SHOWN);
			// Leave the vanilla in-game chat overlay visible (do not open ChatScreen:
			// that was drawing the list twice). Hide only toasts.
			context.runOnClient(mc -> mc.gui.toastManager().clear());
			context.waitTicks(15);
			context.takeScreenshot(TestScreenshotOptions.of("gallery-listhomes")
				.disableCounterPrefix()
				.withDestinationDir(OUT));
		}
	}

	private static void runAsPlayer(TestServerContext server, String command) {
		server.runOnServer(s -> {
			var player = s.getPlayerList().getPlayers().getFirst();
			try {
				int result = s.getCommands().getDispatcher()
					.execute(command, player.createCommandSourceStack());
				System.out.println("[sethome-test] /" + command + " result=" + result);
			} catch (CommandSyntaxException e) {
				throw new RuntimeException("Failed to run /" + command + ": " + e.getMessage(), e);
			}
		});
	}

	private static void placeTree(TestServerContext server, int x, int y, int z) {
		server.runCommand("fill " + x + " " + y + " " + z + " " + x + " " + (y + 3) + " " + z + " minecraft:oak_log");
		server.runCommand("fill " + (x - 2) + " " + (y + 3) + " " + (z - 2) + " " + (x + 2) + " " + (y + 5) + " " + (z + 2) + " minecraft:oak_leaves");
		server.runCommand("setblock " + x + " " + (y + 6) + " " + z + " minecraft:oak_leaves");
	}
}
