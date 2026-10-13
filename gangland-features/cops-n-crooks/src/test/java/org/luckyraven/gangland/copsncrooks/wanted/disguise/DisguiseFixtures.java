package org.luckyraven.gangland.copsncrooks.wanted.disguise;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DisguiseSettings;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Shared test doubles of the disguise lane: a player whose look can be changed, Bartizan-free wearable keys, words. */
final class DisguiseFixtures {

	private DisguiseFixtures() {
	}

	/** {@code WantedMessages} that answers every key with its shipped fallback and replaces the placeholders. */
	static WantedMessages words() {
		WantedMessages messages = mock(WantedMessages.class);
		when(messages.format(any(WantedMessages.Key.class), anyMap())).thenAnswer(inv -> {
			String line = inv.<WantedMessages.Key>getArgument(0).fallback;
			for (Map.Entry<String, String> e : inv.<Map<String, String>>getArgument(1).entrySet()) {
				line = line.replace("%" + e.getKey() + "%", e.getValue());
			}
			return line;
		});
		return messages;
	}

	static SuspectDescriptions descriptions(Wearer wearer, DisguiseSettings settings) {
		return new SuspectDescriptions(() -> settings, wearer.keys(), words());
	}

	/** A player whose helmet, chest, held item and vehicle the test changes between calls. */
	static final class Wearer {

		final Player player = mock(Player.class);
		final UUID   id     = UUID.randomUUID();

		private final Map<ItemStack, String> keys = new IdentityHashMap<>();
		private ItemStack helmet;
		private ItemStack chest;
		private ItemStack hand;
		private boolean   inCar;

		Wearer() {
			PlayerInventory inventory = mock(PlayerInventory.class);
			when(player.getUniqueId()).thenReturn(id);
			when(player.getInventory()).thenReturn(inventory);
			when(inventory.getHelmet()).thenAnswer(inv -> helmet);
			when(inventory.getChestplate()).thenAnswer(inv -> chest);
			when(inventory.getItemInMainHand()).thenAnswer(inv -> hand);
			when(player.getVehicle()).thenAnswer(inv -> inCar ? mock(Entity.class) : null);
		}

		WearableKeys keys() {
			return item -> item == null ? null : keys.get(item);
		}

		Wearer helmet(Material material) {
			helmet = material == null ? null : item(material);
			return this;
		}

		/** A Bartizan wearable helmet: material plus its wearable key. */
		Wearer helmet(Material material, String wearableKey) {
			helmet = item(material);
			keys.put(helmet, wearableKey);
			return this;
		}

		Wearer chest(Material material) {
			chest = material == null ? null : item(material);
			return this;
		}

		Wearer chest(Material material, String wearableKey) {
			chest = item(material);
			keys.put(chest, wearableKey);
			return this;
		}

		/** A leather chestplate dyed {@code colour}. */
		Wearer leatherChest(Color colour) {
			chest = item(Material.LEATHER_CHESTPLATE);
			LeatherArmorMeta meta = mock(LeatherArmorMeta.class);
			when(meta.getColor()).thenReturn(colour);
			when(chest.getItemMeta()).thenReturn(meta);
			return this;
		}

		Wearer hand(Material material) {
			hand = material == null ? null : item(material);
			return this;
		}

		Wearer inCar(boolean value) {
			inCar = value;
			return this;
		}

		/** Takes the helmet and chest off. */
		Wearer naked() {
			helmet = null;
			chest  = null;
			return this;
		}

		private static ItemStack item(Material material) {
			ItemStack stack = mock(ItemStack.class);
			when(stack.getType()).thenReturn(material);
			return stack;
		}
	}
}
