package me.luckyraven.weapon.ammo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.luckyraven.core.ItemBuilder;
import me.luckyraven.core.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
@Getter
public class Ammunition implements Comparable<Ammunition> {

	/**
	 * Canonical NBT tag stamped on every built ammunition ItemStack. Shared with predicates/serializers so no call site
	 * has to hardcode {@code NBT_KEY}.
	 */
	public static final String NBT_KEY = "ammo";

	private final String       name;
	private final String       displayName;
	private final Material     material;
	private final int          customModelData;
	private       List<String> lore;

	/**
	 * Placeholder resolver injected by {@code AmmunitionAddon} after construction so {@link #buildItem(Player)} can
	 * resolve {@code %gangland_*%} tokens in the display name and lore.
	 */
	@Setter
	@Nullable
	private Placeholder placeholder;

	public Ammunition(String name, String displayName, Material material, int customModelData, List<String> lore) {
		this.name            = name;
		this.displayName     = displayName;
		this.material        = material;
		this.customModelData = customModelData;
		this.lore            = lore;
	}

	public static boolean isAmmunition(ItemStack item) {
		if (item == null || item.getType().equals(Material.AIR) || item.getAmount() == 0) return false;

		return new ItemBuilder(item).hasNBTTag(NBT_KEY);
	}

	public static Ammunition getHeldAmmunition(AmmunitionManager ammunitionManager, ItemStack itemHeld) {
		if (itemHeld == null || itemHeld.getType().equals(Material.AIR) || itemHeld.getAmount() == 0) return null;
		if (!isAmmunition(itemHeld)) return null;

		ItemBuilder itemBuilder    = new ItemBuilder(itemHeld);
		String      ammunitionName = itemBuilder.getStringTagData(NBT_KEY);

		return ammunitionManager.getAmmunition(ammunitionName);
	}

	@Override
	public int compareTo(@NotNull Ammunition other) {
		int result;

		// Compare by ammo internal name (identity)
		result = this.name.compareToIgnoreCase(other.name);
		if (result != 0) return result;

		// Compare by material
		result = this.material.compareTo(other.material);

		return result;
	}


	public ItemStack buildItem() {
		return buildItem(null, 1);
	}

	public ItemStack buildItem(int amount) {
		return buildItem(null, amount);
	}

	public ItemStack buildItem(@Nullable Player player) {
		return buildItem(player, 1);
	}

	public ItemStack buildItem(@Nullable Player player, int amount) {
		ItemBuilder builder = new ItemBuilder(material);

		builder.setDisplayName(resolvePlaceholder(player, displayName)).setLore(resolvePlaceholder(player, lore));
		builder.setAmount(amount);

		if (customModelData > 0) {
			builder.setCustomModelData(customModelData);
		}

		builder.addTag(NBT_KEY, name);

		return builder.build();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Ammunition that = (Ammunition) o;

		return Objects.equals(name, that.name) && material == that.material;
	}

	@Override
	public String toString() {
		return String.format("Ammunition{ammo='%s',name='%s',material='%s'}", name, displayName, material.toString());
	}

	private String resolvePlaceholder(@Nullable Player player, @Nullable String text) {
		if (text == null || text.isEmpty() || placeholder == null) return text;
		return placeholder.convert(player, text);
	}

	private List<String> resolvePlaceholder(@Nullable Player player, @Nullable List<String> loreLines) {
		if (loreLines == null || loreLines.isEmpty() || placeholder == null) return loreLines;
		List<String> resolved = new ArrayList<>(loreLines.size());
		for (String line : loreLines) {
			resolved.add(line == null ? null : placeholder.convert(player, line));
		}
		return resolved;
	}

}
