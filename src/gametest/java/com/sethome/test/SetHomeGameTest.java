package com.sethome.test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.JsonOps;
import com.sethome.HomeStorage;
import com.sethome.HomeStorage.HomeData;
import com.sethome.SethomeConfig;
import com.sethome.SetHomeMod;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.minecraft.world.phys.Vec3;

public class SetHomeGameTest {
	private static final double POS_EPSILON = 0.01;

	@GameTest(maxTicks = 80)
	public void sethomeAndHomeTeleport(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		MinecraftServer server = helper.getLevel().getServer();
		Vec3 homePos = helper.absoluteVec(new Vec3(2.5, 2.0, 3.5));
		player.snapTo(homePos.x, homePos.y, homePos.z, 45.0F, 10.0F);

		int setResult = runAs(player, "sethome");
		helper.assertTrue(setResult == 1, "sethome should succeed, got " + setResult);

		HomeData saved = HomeStorage.get(server).getHome(player.getUUID());
		helper.assertTrue(saved != null, "home should be stored after /sethome");
		helper.assertTrue(near(saved.x(), homePos.x) && near(saved.y(), homePos.y) && near(saved.z(), homePos.z),
			"stored home coords were " + saved.x() + "," + saved.y() + "," + saved.z());
		helper.assertTrue(saved.dimension().equals(player.level().dimension().identifier().toString()),
			"stored dimension was " + saved.dimension());

		player.snapTo(homePos.x + 20, homePos.y + 5, homePos.z + 20, 0.0F, 0.0F);
		int homeResult = runAs(player, "home");
		helper.assertTrue(homeResult == 1, "home should succeed, got " + homeResult);
		helper.assertTrue(near(player.getX(), homePos.x) && near(player.getY(), homePos.y) && near(player.getZ(), homePos.z),
			"player should be teleported back, was " + player.position());
		helper.assertTrue(Math.abs(player.getYRot() - 45.0F) < 0.1F, "yaw should be restored, was " + player.getYRot());
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void homePersistsAcrossReload(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		MinecraftServer server = helper.getLevel().getServer();
		UUID id = player.getUUID();
		Vec3 homePos = helper.absoluteVec(new Vec3(8.25, 4.0, 9.75));
		player.snapTo(homePos.x, homePos.y, homePos.z, 90.0F, -5.0F);

		helper.assertTrue(runAs(player, "sethome") == 1, "sethome should succeed");
		helper.assertTrue(runAs(player, "sethome mine") == 1, "named sethome should succeed");
		ServerLevel overworld = server.getLevel(Level.OVERWORLD);
		SavedDataStorage dataStorage = overworld.getDataStorage();
		dataStorage.saveAndJoin();

		Path dataFile = Identifier.fromNamespaceAndPath("sethome", "homes")
			.withSuffix(".dat")
			.resolveAgainst(dataFolder(dataStorage));
		helper.assertTrue(Files.exists(dataFile), "saved data file should exist at " + dataFile);

		try {
			CompoundTag tag = NbtIo.readCompressed(dataFile, NbtAccounter.unlimitedHeap());
			helper.assertTrue(tag.contains("data"), "homes.dat should contain a data tag");
		} catch (Exception e) {
			helper.fail("failed to read homes.dat: " + e);
			return;
		}

		clearSavedDataCache(dataStorage);
		HomeStorage reloaded = HomeStorage.get(server);
		HomeData home = reloaded.getHome(id);
		helper.assertTrue(home != null, "default home should still exist after evicting the in-memory cache");
		helper.assertTrue(near(home.x(), homePos.x) && near(home.y(), homePos.y) && near(home.z(), homePos.z),
			"reloaded coords were " + home.x() + "," + home.y() + "," + home.z());
		helper.assertTrue(reloaded.getHome(id, "mine") != null, "named home should persist across reload");
		helper.succeed();
	}

	@GameTest(maxTicks = 120)
	public void homeTeleportsAcrossDimensions(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel nether = server.getLevel(Level.NETHER);
		helper.assertTrue(nether != null, "nether should be loaded");

		Vec3 netherHome = new Vec3(12.5, 80.0, 18.5);
		player.teleportTo(nether, netherHome.x, netherHome.y, netherHome.z, java.util.Set.of(), 0.0F, 0.0F, true);
		helper.assertTrue(player.level().dimension().equals(Level.NETHER), "player should be in the nether after teleport, was " + player.level().dimension());

		helper.assertTrue(runAs(player, "sethome") == 1, "sethome in the nether should succeed");
		HomeData saved = HomeStorage.get(server).getHome(player.getUUID());
		helper.assertTrue(saved != null && saved.dimension().equals("minecraft:the_nether"), "stored dimension was " + (saved == null ? "null" : saved.dimension()));

		ServerLevel overworld = server.getLevel(Level.OVERWORLD);
		player.teleportTo(overworld, 0.5, 80.0, 0.5, java.util.Set.of(), 0.0F, 0.0F, true);
		helper.assertTrue(player.level().dimension().equals(Level.OVERWORLD), "player should be back in the overworld");

		helper.assertTrue(runAs(player, "home") == 1, "home should teleport back to the nether");
		helper.assertTrue(player.level().dimension().equals(Level.NETHER), "player should be in the nether after /home, was " + player.level().dimension());
		helper.assertTrue(near(player.getX(), netherHome.x) && near(player.getY(), netherHome.y) && near(player.getZ(), netherHome.z),
			"nether home position was " + player.position());
		helper.succeed();
	}

	@GameTest
	public void homeWithoutSethomeFails(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		int result = runAs(player, "home");
		helper.assertTrue(result == 0, "home without a saved location should fail, got " + result);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void namedHomesAreSeparateFromDefault(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		MinecraftServer server = helper.getLevel().getServer();
		HomeStorage storage = HomeStorage.get(server);

		Vec3 defaultPos = helper.absoluteVec(new Vec3(3.0, 5.0, 7.0));
		player.snapTo(defaultPos.x, defaultPos.y, defaultPos.z, 0.0F, 0.0F);
		helper.assertTrue(runAs(player, "sethome") == 1, "default sethome should succeed");

		Vec3 minePos = helper.absoluteVec(new Vec3(13.0, 6.0, 17.0));
		player.snapTo(minePos.x, minePos.y, minePos.z, 30.0F, 5.0F);
		helper.assertTrue(runAs(player, "sethome mine") == 1, "named sethome should succeed");
		helper.assertTrue(runAs(player, "sethome Mine") == 1, "same name in different case should overwrite, not create a second home");
		helper.assertTrue(storage.homeCount(player.getUUID()) == 2, "should have exactly 2 homes, got " + storage.homeCount(player.getUUID()));

		player.snapTo(0, 10, 0, 0, 0);
		helper.assertTrue(runAs(player, "home mine") == 1, "home mine should succeed");
		helper.assertTrue(near(player.getX(), minePos.x) && near(player.getZ(), minePos.z), "named home pos was " + player.position());

		helper.assertTrue(runAs(player, "home") == 1, "default home should still work");
		helper.assertTrue(near(player.getX(), defaultPos.x) && near(player.getZ(), defaultPos.z), "default home pos was " + player.position());
		helper.succeed();
	}

	@GameTest
	public void invalidHomeNameRejected(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(runAsAllowingParseFailure(player, "sethome bad name") == 0, "spaces should be rejected");
		helper.assertTrue(runAsAllowingParseFailure(player, "sethome bad!name") == 0, "disallowed symbols should fail to parse");
		helper.assertTrue(runAs(player, "sethome bad.name") == 0, "dots should be rejected by name validation");
		helper.assertTrue(runAs(player, "sethome " + "a".repeat(33)) == 0, "names over 32 characters should be rejected");
		helper.assertTrue(HomeStorage.get(helper.getLevel().getServer()).homeCount(player.getUUID()) == 0, "no homes should have been stored");
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void delhomeRemovesDefaultAndNamed(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		HomeStorage storage = HomeStorage.get(helper.getLevel().getServer());
		player.snapTo(5, 10, 5, 0, 0);
		helper.assertTrue(runAs(player, "sethome") == 1, "sethome");
		helper.assertTrue(runAs(player, "sethome shop") == 1, "sethome shop");
		helper.assertTrue(runAs(player, "delhome") == 1, "delhome should delete the default");
		helper.assertTrue(storage.getHome(player.getUUID()) == null, "default home should be gone");
		helper.assertTrue(storage.getHome(player.getUUID(), "shop") != null, "named home should still exist");
		helper.assertTrue(runAs(player, "delhome shop") == 1, "delhome shop");
		helper.assertTrue(storage.homeCount(player.getUUID()) == 0, "all homes should be gone");
		helper.assertTrue(runAs(player, "delhome shop") == 0, "deleting a missing home should fail");
		helper.succeed();
	}

	@GameTest
	public void maxHomesLimitAndOverwrite(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		SethomeConfig config = SethomeConfig.get();
		int previous = config.maxHomes();
		boolean previousBypass = config.opsBypassLimit();
		config.setMaxHomes(1);
		config.setOpsBypassLimit(false);
		try {
			player.snapTo(1, 10, 1, 0, 0);
			helper.assertTrue(runAs(player, "sethome") == 1, "first home should be allowed");
			helper.assertTrue(runAs(player, "sethome extra") == 0, "a new home beyond the limit should fail");
			helper.assertTrue(HomeStorage.get(helper.getLevel().getServer()).homeCount(player.getUUID()) == 1, "still only the default home");
			player.snapTo(9, 12, 9, 0, 0);
			helper.assertTrue(runAs(player, "sethome") == 1, "overwriting the existing default home should still be allowed");
			HomeData updated = HomeStorage.get(helper.getLevel().getServer()).getHome(player.getUUID());
			helper.assertTrue(updated != null && near(updated.x(), 9.0), "overwritten coords were " + (updated == null ? "null" : updated.x()));
		} finally {
			config.setMaxHomes(previous);
			config.setOpsBypassLimit(previousBypass);
		}
		helper.succeed();
	}

	@GameTest
	public void unlimitedWhenMaxHomesIsZero(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		SethomeConfig config = SethomeConfig.get();
		int previous = config.maxHomes();
		config.setMaxHomes(0);
		try {
			helper.assertTrue(config.isUnlimited(), "0 should mean unlimited");
			for (int i = 0; i < 6; i++) {
				helper.assertTrue(runAs(player, "sethome h" + i) == 1, "unlimited should allow home h" + i);
			}
			helper.assertTrue(HomeStorage.get(helper.getLevel().getServer()).homeCount(player.getUUID()) == 6, "all 6 named homes should exist");
		} finally {
			config.setMaxHomes(previous);
		}
		helper.succeed();
	}

	@GameTest
	public void listhomesShowsNameWorldAndCoords(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.snapTo(10.4, 65.2, -20.6, 0, 0);
		helper.assertTrue(runAs(player, "sethome") == 1, "sethome");
		player.snapTo(100.2, 70.8, 50.4, 0, 0);
		helper.assertTrue(runAs(player, "sethome shop") == 1, "sethome shop");

		Map<String, HomeData> homes = HomeStorage.get(helper.getLevel().getServer()).getHomes(player.getUUID());
		Component list = SetHomeMod.formatHomeList("Your homes", homes, true);
		String text = list.getString();
		helper.assertTrue(text.contains("Your homes") && text.contains("(2)"), "header was: " + text);
		helper.assertTrue(text.contains("home") && text.contains("shop"), "names missing in: " + text);
		helper.assertTrue(text.contains("Overworld"), "dimension missing in: " + text);
		helper.assertTrue(text.contains("(10, 65, -21)") || text.contains("(10, 65, -20)"), "rounded default coords missing in: " + text);
		helper.assertTrue(text.contains("(100, 71, 50)") || text.contains("(100, 70, 50)"), "rounded shop coords missing in: " + text);

		ClickEvent click = findClickEvent(list);
		helper.assertTrue(click instanceof ClickEvent.RunCommand, "entry should be clickable, got " + click);
		String command = ((ClickEvent.RunCommand) click).command();
		helper.assertTrue(command.equals("/home home") || command.equals("/home shop"), "click command was " + command);
		helper.assertTrue(findHoverEvent(list) instanceof HoverEvent.ShowText, "entry should have a hover hint");

		helper.assertTrue(runAs(player, "listhomes") == 2, "listhomes should return the number of homes");
		helper.succeed();
	}

	@GameTest
	public void listhomesOtherPlayerIncludesOffline(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		UUID offlineId = UUID.fromString("12345678-1234-1234-1234-123456789abc");
		HomeData data = new HomeData(1.0, 2.0, 3.0, 0.0F, 0.0F, "minecraft:the_end");
		HomeStorage.get(server).setHome(offlineId, "endbase", data);
		server.services().nameToIdCache().add(new NameAndId(offlineId, "OfflinePal"));

		int result = run(server, "listhomes OfflinePal");
		helper.assertTrue(result == 1, "console/op listhomes for an offline player should succeed, got " + result);
		helper.assertTrue(HomeStorage.get(server).getHome(offlineId, "endbase") != null, "offline player's home should still be stored");

		Component list = SetHomeMod.formatHomeList("Homes for OfflinePal", HomeStorage.get(server).getHomes(offlineId), false);
		String text = list.getString();
		helper.assertTrue(text.contains("OfflinePal") && text.contains("endbase") && text.contains("The End") && text.contains("(1, 2, 3)"),
			"offline list was: " + text);
		helper.assertTrue(findClickEvent(list) == null, "other-player lists should not be clickable");
		helper.succeed();
	}

	@GameTest
	public void listhomesUnknownPlayerFails(GameTestHelper helper) {
		int result = run(helper.getLevel().getServer(), "listhomes NobodyHere");
		helper.assertTrue(result == 0, "unknown players should not be listed, got " + result);
		helper.succeed();
	}

	@GameTest
	public void migratesLegacySingleHomeFormat(GameTestHelper helper) {
		HomeData legacy = new HomeData(42.5, 64.0, -8.25, 90.0F, 15.0F, "minecraft:overworld");
		var encoded = HomeData.CODEC.encodeStart(NbtOps.INSTANCE, legacy).result().orElse(null);
		helper.assertTrue(encoded != null, "legacy home should encode");
		Map<String, HomeData> migrated = HomeStorage.PLAYER_HOMES_CODEC.parse(NbtOps.INSTANCE, encoded)
			.result()
			.orElse(null);
		helper.assertTrue(migrated != null, "legacy value should parse as player homes");
		helper.assertTrue(migrated.size() == 1 && migrated.containsKey(HomeStorage.DEFAULT_HOME), "migrated keys were " + migrated.keySet());
		HomeData home = migrated.get(HomeStorage.DEFAULT_HOME);
		helper.assertTrue(near(home.x(), 42.5) && near(home.z(), -8.25) && home.yaw() == 90.0F, "migrated data was " + home);

		// New format still round-trips as a name map, not as a single home.
		var namedEncoded = HomeStorage.PLAYER_HOMES_CODEC.encodeStart(JsonOps.INSTANCE, migrated).result().orElse(null);
		helper.assertTrue(namedEncoded != null && namedEncoded.toString().contains("home"), "new format encode was " + namedEncoded);
		helper.succeed();
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

	private static int run(MinecraftServer server, String command) {
		try {
			return server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack());
		} catch (CommandSyntaxException e) {
			throw new RuntimeException("Failed to run /" + command + ": " + e.getMessage(), e);
		}
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < POS_EPSILON;
	}

	private static ClickEvent findClickEvent(Component component) {
		if (component.getStyle().getClickEvent() != null) {
			return component.getStyle().getClickEvent();
		}
		for (Component sibling : component.getSiblings()) {
			ClickEvent found = findClickEvent(sibling);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	private static HoverEvent findHoverEvent(Component component) {
		if (component.getStyle().getHoverEvent() != null) {
			return component.getStyle().getHoverEvent();
		}
		for (Component sibling : component.getSiblings()) {
			HoverEvent found = findHoverEvent(sibling);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	private static Path dataFolder(SavedDataStorage storage) {
		try {
			Field field = SavedDataStorage.class.getDeclaredField("dataFolder");
			field.setAccessible(true);
			return (Path) field.get(storage);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException("Could not read SavedDataStorage data folder", e);
		}
	}

	@SuppressWarnings("unchecked")
	private static void clearSavedDataCache(SavedDataStorage storage) {
		try {
			Field cache = SavedDataStorage.class.getDeclaredField("cache");
			cache.setAccessible(true);
			((Map<?, ?>) cache.get(storage)).clear();
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException("Could not clear SavedDataStorage cache for reload test", e);
		}
	}
}
