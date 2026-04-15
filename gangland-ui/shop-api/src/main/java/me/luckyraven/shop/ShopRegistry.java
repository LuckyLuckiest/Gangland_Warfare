package me.luckyraven.shop;

import lombok.CustomLog;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.persistence.FolderLoader;
import me.luckyraven.shop.io.ShopYamlReader;
import me.luckyraven.shop.io.ShopYamlWriter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

/**
 * Scans {@code plugins/<plugin>/shop/} for per-shop YAML files, parses each into a {@link ShopDefinition}, and exposes
 * CRUD operations for admin commands. Lives in shop-api rather than a feature module so any shop integration (trader
 * NPC, vending kiosk, future black-market terminal) can share the same on-disk layout.
 */
@CustomLog
public class ShopRegistry extends FolderLoader {

	private final JavaPlugin     plugin;
	private final FileManager    fileManager;
	private final ShopYamlReader reader;
	private final ShopYamlWriter writer;

	private final Map<String, ShopDefinition> shopsByKey        = new LinkedHashMap<>();
	private final Map<String, FileHandler>    fileHandlersByKey = new HashMap<>();

	public ShopRegistry(JavaPlugin plugin, FileManager fileManager, ShopYamlReader reader, ShopYamlWriter writer) {
		super(plugin, "shop", fileManager);
		this.plugin      = plugin;
		this.fileManager = fileManager;
		this.reader      = reader;
		this.writer      = writer;
	}

	@Override
	public void initialize() {
		shopsByKey.clear();
		fileHandlersByKey.clear();
		this.load(true, this::acceptFile, fileManager);
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		if (firstLoad) return;
		initialize();
	}

	@Nullable
	public ShopDefinition get(String key) {
		if (key == null) return null;
		return shopsByKey.get(key);
	}

	public boolean exists(String key) {
		return key != null && shopsByKey.containsKey(key);
	}

	public Set<String> keys() {
		return Collections.unmodifiableSet(shopsByKey.keySet());
	}

	public ShopDefinition createEmpty(String key) throws IOException {
		if (shopsByKey.containsKey(key)) {
			throw new IOException("Shop '" + key + "' already exists");
		}

		FileHandler handler = new FileHandler(plugin, key, "shop", "yml");
		handler.create(false);

		ShopDefinition empty = ShopDefinition.empty(key, defaultTitleFor(key), 54);
		writer.write(empty, handler);

		shopsByKey.put(key, empty);
		fileHandlersByKey.put(key, handler);

		return empty;
	}

	public void save(ShopDefinition definition) {
		FileHandler handler = fileHandlersByKey.get(definition.getKey());
		if (handler == null) {
			log.warn("Cannot save shop '{}' — no file handler found", definition.getKey());
			return;
		}

		writer.write(definition, handler);
		shopsByKey.put(definition.getKey(), definition);
	}

	public boolean delete(String key) throws IOException {
		FileHandler handler = fileHandlersByKey.remove(key);
		shopsByKey.remove(key);
		if (handler != null) {
			handler.delete();
			return true;
		}
		return false;
	}

	private String defaultTitleFor(String key) {
		if (key == null || key.isEmpty()) return "&6Shop";
		char first = Character.toUpperCase(key.charAt(0));
		return "&6" + first + key.substring(1);
	}

	private void acceptFile(FileHandler handler) {
		String key = handler.getName();

		try {
			ShopDefinition def = reader.parse(key, handler);
			shopsByKey.put(key, def);
			fileHandlersByKey.put(key, handler);
		} catch (Exception e) {
			log.error("Failed to parse shop '{}': {}", key, e.getMessage(), e);
		}
	}

}
