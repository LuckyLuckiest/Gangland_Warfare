package org.luckyraven.gangland.data.detainment.inventory;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.luckyraven.gangland.copsncrooks.detainment.inventory.SeizedInventory;
import org.luckyraven.gangland.copsncrooks.detainment.inventory.SeizedInventoryService;
import org.luckyraven.gangland.persistence.repository.IRepository;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@CustomLog
public final class GanglandSeizedInventoryService implements SeizedInventoryService {

	private final IRepository<SeizedInventory> repository;
	private final Map<UUID, SeizedInventory>   cache = new ConcurrentHashMap<>();

	public GanglandSeizedInventoryService(IRepository<SeizedInventory> repository) {
		this.repository = repository;
		for (SeizedInventory seized : repository.loadAll()) {
			cache.put(seized.getPlayerId(), seized);
		}
		// Cache is the source of truth; the auto-save sweep re-writes every entry each tick which is idempotent.
		repository.setDataSupplier(cache::values);
	}

	@SuppressWarnings("unused")
	public static Player onlinePlayer(UUID playerId) {
		return playerId == null ? null : Bukkit.getPlayer(playerId);
	}

	private static String serialize(ItemStack[] main, ItemStack[] armour, ItemStack offhand) throws Exception {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream();
		     BukkitObjectOutputStream writer = new BukkitObjectOutputStream(out)) {
			writer.writeInt(main.length);
			for (ItemStack item : main) writer.writeObject(item);

			writer.writeInt(armour.length);
			for (ItemStack item : armour) writer.writeObject(item);

			writer.writeObject(offhand);
			writer.flush();
			return Base64Coder.encodeLines(out.toByteArray());
		}
	}

	private static void applyInventory(Player player, String serialized) throws Exception {
		byte[] bytes = Base64Coder.decodeLines(serialized);
		try (ByteArrayInputStream in = new ByteArrayInputStream(bytes);
		     BukkitObjectInputStream reader = new BukkitObjectInputStream(in)) {
			int         mainSize = reader.readInt();
			ItemStack[] main     = new ItemStack[mainSize];
			for (int i = 0; i < mainSize; i++) main[i] = (ItemStack) reader.readObject();

			int         armourSize = reader.readInt();
			ItemStack[] armour     = new ItemStack[armourSize];
			for (int i = 0; i < armourSize; i++) armour[i] = (ItemStack) reader.readObject();

			ItemStack offhand = (ItemStack) reader.readObject();

			PlayerInventory inventory = player.getInventory();
			inventory.setContents(main);
			inventory.setArmorContents(armour);
			inventory.setItemInOffHand(offhand);
			player.updateInventory();
		}
	}

	@Override
	public void snapshot(Player player) {
		PlayerInventory inventory = player.getInventory();
		ItemStack[]     main      = inventory.getContents();
		ItemStack[]     armour    = inventory.getArmorContents();
		ItemStack       offhand   = inventory.getItemInOffHand();

		String serialized;
		try {
			serialized = serialize(main, armour, offhand);
		} catch (Exception e) {
			log.error("Failed to serialize inventory for {}: {}", player.getName(), e.getMessage());
			return;
		}

		SeizedInventory entry = new SeizedInventory(player.getUniqueId(), serialized, System.currentTimeMillis());
		cache.put(player.getUniqueId(), entry);
		repository.save(entry);
	}

	@Override
	public boolean restore(Player player) {
		SeizedInventory seized = cache.remove(player.getUniqueId());
		if (seized == null) return false;

		repository.delete(seized);

		try {
			applyInventory(player, seized.getSerializedContents());
		} catch (Exception e) {
			log.error("Failed to restore seized inventory for {}: {}", player.getName(), e.getMessage());
			return false;
		}
		return true;
	}

	@Override
	public boolean has(UUID playerId) {
		return cache.containsKey(playerId);
	}

	@Override
	public void clear(UUID playerId) {
		SeizedInventory seized = cache.remove(playerId);
		if (seized != null) repository.delete(seized);
	}

	/**
	 * Utility used by unit tests / admin tooling to coerce into the service without a player handle.
	 */
	@SuppressWarnings("unused")
	public SeizedInventory peek(UUID playerId) {
		return cache.get(playerId);
	}
}
