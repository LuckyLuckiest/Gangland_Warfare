package org.luckyraven.gangland.gadget.item;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.gadget.jetpack.Jetpack;
import org.luckyraven.gangland.gadget.jetpack.config.JetpackAddon;
import org.luckyraven.keystone.item.ItemRefresher;

/**
 * Rebuilds a jetpack ItemStack into a factory-fresh copy (full fuel, fresh lore), mirroring
 * {@link CarItemRefresher} — house rule {@code feedback_item_refresher_pattern}: stateful items refresh on every
 * shop/trader delivery, not once at placement.
 */
@RequiredArgsConstructor
public class JetpackItemRefresher implements ItemRefresher {

	private final JetpackAddon jetpackAddon;

	@Override
	public boolean canRefresh(ItemStack source) {
		return Jetpack.isJetpackItem(source);
	}

	@Override
	@Nullable
	public ItemStack refresh(ItemStack source, @Nullable Player context) {
		String jetpackId = Jetpack.getJetpackId(source);
		if (jetpackId == null || jetpackId.isEmpty()) return null;

		Jetpack jetpack = jetpackAddon.getJetpack(jetpackId);
		if (jetpack == null) return null;

		ItemStack built = context != null ? jetpack.buildItem(context) : jetpack.buildItem();
		if (built == null) return null;

		built.setAmount(source.getAmount());
		return built;
	}
}
