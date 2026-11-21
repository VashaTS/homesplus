package com.sethome;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SethomeConfig {
	public static final Logger LOGGER = LoggerFactory.getLogger("sethome");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** 0 means unlimited. */
	public static final int DEFAULT_MAX_HOMES = 0;
	public static final boolean DEFAULT_OPS_BYPASS_LIMIT = false;

	private static final SethomeConfig INSTANCE = new SethomeConfig();

	private int maxHomes = DEFAULT_MAX_HOMES;
	private boolean opsBypassLimit = DEFAULT_OPS_BYPASS_LIMIT;

	private SethomeConfig() {
	}

	public static SethomeConfig get() {
		return INSTANCE;
	}

	public static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("sethome.json");
	}

	public int maxHomes() {
		return this.maxHomes;
	}

	public boolean opsBypassLimit() {
		return this.opsBypassLimit;
	}

	public boolean isUnlimited() {
		return this.maxHomes <= 0;
	}

	/** In-memory override used by game tests; does not write the file. */
	public void setMaxHomes(int maxHomes) {
		this.maxHomes = maxHomes;
	}

	public void setOpsBypassLimit(boolean opsBypassLimit) {
		this.opsBypassLimit = opsBypassLimit;
	}

	public boolean canCreateNewHome(int currentCount, boolean isOp) {
		if (isOp && this.opsBypassLimit) {
			return true;
		}
		if (isUnlimited()) {
			return true;
		}
		return currentCount < this.maxHomes;
	}

	public void loadOrCreate() {
		Path path = configPath();
		try {
			if (Files.notExists(path)) {
				this.maxHomes = DEFAULT_MAX_HOMES;
				this.opsBypassLimit = DEFAULT_OPS_BYPASS_LIMIT;
				save();
				LOGGER.info("Created default sethome config at {}", path.toAbsolutePath());
				return;
			}
			loadFrom(path);
		} catch (Exception e) {
			LOGGER.error("Failed to load sethome config from {}, using defaults", path.toAbsolutePath(), e);
			this.maxHomes = DEFAULT_MAX_HOMES;
			this.opsBypassLimit = DEFAULT_OPS_BYPASS_LIMIT;
		}
	}

	public void loadFrom(Path path) throws IOException {
		String raw = Files.readString(path, StandardCharsets.UTF_8);
		JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
		this.maxHomes = json.has("maxHomes") ? json.get("maxHomes").getAsInt() : DEFAULT_MAX_HOMES;
		this.opsBypassLimit = json.has("opsBypassLimit") ? json.get("opsBypassLimit").getAsBoolean() : DEFAULT_OPS_BYPASS_LIMIT;
		LOGGER.info("Loaded sethome config (maxHomes={}, opsBypassLimit={})", this.maxHomes, this.opsBypassLimit);
	}

	public void save() throws IOException {
		Path path = configPath();
		Files.createDirectories(path.getParent());
		JsonObject json = new JsonObject();
		json.addProperty("maxHomes", this.maxHomes);
		json.addProperty("opsBypassLimit", this.opsBypassLimit);
		Files.writeString(path, GSON.toJson(json) + System.lineSeparator(), StandardCharsets.UTF_8);
	}
}
