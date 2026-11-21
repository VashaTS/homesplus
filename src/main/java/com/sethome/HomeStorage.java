package com.sethome;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class HomeStorage extends SavedData {
	public static final String DEFAULT_HOME = "home";
	public static final int MAX_NAME_LENGTH = 32;
	public static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");

	/** New format: UUID -> (name -> home). Also accepts the old single-home value and migrates it to {@link #DEFAULT_HOME}. */
	public static final Codec<Map<String, HomeData>> PLAYER_HOMES_CODEC = Codec.either(
		Codec.unboundedMap(Codec.STRING, HomeData.CODEC),
		HomeData.CODEC
	).xmap(HomeStorage::fromEither, map -> Either.left(map));

	private static final Codec<Map<UUID, Map<String, HomeData>>> HOMES_CODEC =
		Codec.unboundedMap(UUIDUtil.STRING_CODEC, PLAYER_HOMES_CODEC);

	public static final SavedDataType<HomeStorage> TYPE = new SavedDataType<>(
		Identifier.fromNamespaceAndPath("sethome", "homes"),
		HomeStorage::new,
		HOMES_CODEC.xmap(HomeStorage::new, storage -> storage.homes),
		null
	);

	private final Map<UUID, Map<String, HomeData>> homes;

	private HomeStorage() {
		this.homes = new HashMap<>();
	}

	private HomeStorage(Map<UUID, Map<String, HomeData>> homes) {
		this.homes = new HashMap<>();
		homes.forEach((id, named) -> this.homes.put(id, normalizeMap(named)));
	}

	public static String normalizeName(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	public static boolean isValidName(String name) {
		return name != null && NAME_PATTERN.matcher(name).matches();
	}

	private static Map<String, HomeData> fromEither(Either<Map<String, HomeData>, HomeData> either) {
		return either.map(HomeStorage::normalizeMap, data -> {
			Map<String, HomeData> map = new LinkedHashMap<>();
			map.put(DEFAULT_HOME, data);
			return map;
		});
	}

	private static Map<String, HomeData> normalizeMap(Map<String, HomeData> named) {
		Map<String, HomeData> copy = new LinkedHashMap<>();
		named.forEach((name, data) -> copy.put(normalizeName(name), data));
		return copy;
	}

	public void setHome(UUID player, HomeData data) {
		setHome(player, DEFAULT_HOME, data);
	}

	public void setHome(UUID player, String name, HomeData data) {
		this.homes.computeIfAbsent(player, id -> new LinkedHashMap<>()).put(normalizeName(name), data);
		this.setDirty();
	}

	public HomeData getHome(UUID player) {
		return getHome(player, DEFAULT_HOME);
	}

	public HomeData getHome(UUID player, String name) {
		Map<String, HomeData> named = this.homes.get(player);
		return named == null ? null : named.get(normalizeName(name));
	}

	public boolean deleteHome(UUID player, String name) {
		Map<String, HomeData> named = this.homes.get(player);
		if (named == null || named.remove(normalizeName(name)) == null) {
			return false;
		}
		if (named.isEmpty()) {
			this.homes.remove(player);
		}
		this.setDirty();
		return true;
	}

	public int homeCount(UUID player) {
		Map<String, HomeData> named = this.homes.get(player);
		return named == null ? 0 : named.size();
	}

	public boolean hasHome(UUID player, String name) {
		return getHome(player, name) != null;
	}

	/** Default home first, then the rest in alphabetical order. */
	public Map<String, HomeData> getHomes(UUID player) {
		Map<String, HomeData> named = this.homes.get(player);
		if (named == null || named.isEmpty()) {
			return Map.of();
		}
		Map<String, HomeData> ordered = new LinkedHashMap<>();
		if (named.containsKey(DEFAULT_HOME)) {
			ordered.put(DEFAULT_HOME, named.get(DEFAULT_HOME));
		}
		List<String> rest = new ArrayList<>();
		for (String name : named.keySet()) {
			if (!DEFAULT_HOME.equals(name)) {
				rest.add(name);
			}
		}
		Collections.sort(rest);
		for (String name : rest) {
			ordered.put(name, named.get(name));
		}
		return Collections.unmodifiableMap(ordered);
	}

	public List<String> homeNames(UUID player) {
		return new ArrayList<>(getHomes(player).keySet());
	}

	public static HomeStorage get(MinecraftServer server) {
		ServerLevel overworld = server.getLevel(Level.OVERWORLD);
		return overworld.getDataStorage().computeIfAbsent(TYPE);
	}

	public record HomeData(double x, double y, double z, float yaw, float pitch, String dimension) {
		public static final Codec<HomeData> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
				Codec.DOUBLE.fieldOf("x").forGetter(HomeData::x),
				Codec.DOUBLE.fieldOf("y").forGetter(HomeData::y),
				Codec.DOUBLE.fieldOf("z").forGetter(HomeData::z),
				Codec.FLOAT.fieldOf("yaw").forGetter(HomeData::yaw),
				Codec.FLOAT.fieldOf("pitch").forGetter(HomeData::pitch),
				Codec.STRING.fieldOf("dimension").forGetter(HomeData::dimension)
			).apply(instance, HomeData::new)
		);

		public ResourceKey<Level> dimensionKey() {
			return ResourceKey.create(Registries.DIMENSION, Identifier.parse(this.dimension));
		}
	}
}
