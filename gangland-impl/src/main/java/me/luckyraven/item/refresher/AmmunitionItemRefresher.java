package me.luckyraven.item.refresher;

import lombok.RequiredArgsConstructor;
import me.luckyraven.core.ItemBuilder;
import me.luckyraven.core.utilities.ChatUtil;
import me.luckyraven.item.ItemRefresher;
import me.luckyraven.weapon.ammo.Ammunition;
import me.luckyraven.weapon.ammo.AmmunitionManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Rebuilds an ammunition ItemStack into a factory-fresh copy that mirrors what
 * {@link me.luckyraven.item.converter.AmmunitionConverter} produces — the same pipeline used by the lootchest drop
 * path. Always builds with {@code player = null} so shop-delivered ammo stacks byte-identically with lootchest-dropped
 * ammo.
 */
@RequiredArgsConstructor
public class AmmunitionItemRefresher implements ItemRefresher {

	private final AmmunitionManager ammunitionManager;

	@Override
	public boolean canRefresh(ItemStack source) {
		return resolveAmmunition(source) != null;
	}

	@Override
	@Nullable
	public ItemStack refresh(ItemStack source, @Nullable Player context) {
		Ammunition ammunition = resolveAmmunition(source);
		if (ammunition == null) return null;

		return ammunition.buildItem(null, source.getAmount());
	}

	@Nullable
	private Ammunition resolveAmmunition(ItemStack source) {
		if (source == null) return null;

		if (Ammunition.isAmmunition(source)) {
			String name = new ItemBuilder(source).getStringTagData(Ammunition.NBT_KEY);
			if (name != null && !name.isEmpty()) {
				Ammunition tagged = ammunitionManager.getAmmunition(name);
				if (tagged != null) return tagged;
			}
		}

		ItemMeta meta        = source.getItemMeta();
		String   displayName = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : null;

		for (String key : ammunitionManager.getAmmunitionKeys()) {
			Ammunition candidate = ammunitionManager.getAmmunition(key);
			if (candidate == null) continue;
			if (candidate.getMaterial() != source.getType()) continue;

			String candidateName = ChatUtil.color(candidate.getDisplayName());
			if (Objects.equals(candidateName, displayName)) return candidate;
		}
		return null;
	}

}
