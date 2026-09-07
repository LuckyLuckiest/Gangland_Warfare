package org.luckyraven.gangland.inventory.filter;

import org.bukkit.entity.Player;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.gangland.inventory.InventoryHandler;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Builds click handlers for canonical filter buttons (sort-cycle, clear, cycle-enum, text-input-sink). Callers wire
 * these to YAML static slots or use them directly when assembling panels.
 */
public final class SearchButtonFactory {

	private final FilterStore    store;
	private final FilterRegistry registry;

	public SearchButtonFactory(FilterStore store, FilterRegistry registry) {
		this.store    = store;
		this.registry = registry;
	}

	public TriConsumer<Player, InventoryHandler, ItemBuilder> sortClick(String bindingId, Consumer<Player> reopen) {
		return (player, inv, b) -> {
			FilterBinding binding = registry.get(bindingId);
			if (binding == null) return;
			SearchFilter current = store.get(bindingId, player);
			SearchFilter next    = current.withSort(binding.nextSort(current.sort()));
			store.set(bindingId, player, next);
			reopen.accept(player);
		};
	}

	public TriConsumer<Player, InventoryHandler, ItemBuilder> clearClick(String bindingId, Consumer<Player> reopen) {
		return (player, inv, b) -> {
			FilterBinding binding = registry.get(bindingId);
			if (binding == null) return;
			store.set(bindingId, player, binding.empty());
			reopen.accept(player);
		};
	}

	/**
	 * Sink for an anvil/chat text input — updates the filter's text value for {@code field} and reopens the view. If
	 * the incoming text is blank, the field is cleared instead.
	 */
	public BiConsumer<Player, String> textFieldSetter(String bindingId, FilterField field, Consumer<Player> reopen) {
		return (player, text) -> {
			SearchFilter current = store.get(bindingId, player);
			SearchFilter next = (text == null || text.isBlank())
			                    ? current.without(field)
			                    : current.with(field, new FilterValue.TextValue(text.trim()));
			store.set(bindingId, player, next);
			reopen.accept(player);
		};
	}

	/**
	 * Cycles an enum-valued field through the provided option list; after the last option the field is cleared so the
	 * player can escape the filter.
	 */
	public TriConsumer<Player, InventoryHandler, ItemBuilder> cycleEnumClick(String bindingId, FilterField field,
	                                                                         List<String> values,
	                                                                         Consumer<Player> reopen) {
		return (player, inv, b) -> {
			SearchFilter current  = store.get(bindingId, player);
			FilterValue  existing = current.get(field);
			int          idx      = -1;
			if (existing instanceof FilterValue.EnumValue ev) idx = values.indexOf(ev.value());
			int nextIdx = idx + 1;
			SearchFilter next = nextIdx >= values.size()
			                    ? current.without(field)
			                    : current.with(field, new FilterValue.EnumValue(values.get(nextIdx)));
			store.set(bindingId, player, next);
			reopen.accept(player);
		};
	}

}
