package org.luckyraven.gangland.copsncrooks.setup;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.item.nbt.ItemNbtAccessor;
import org.luckyraven.keystone.item.nbt.NbtType;

import java.util.IdentityHashMap;
import java.util.Map;

/** Test double of the NBT seam that keeps tags per ItemStack instance (the testkit one is global per tag name). */
public final class StackTags implements ItemNbtAccessor {

	private final Map<ItemStack, Map<String, Object>> tags = new IdentityHashMap<>();

	@Override
	public boolean isAvailable() {
		return true;
	}

	@Override
	public boolean has(ItemStack stack, String tag) {
		return tags.getOrDefault(stack, Map.of()).containsKey(tag);
	}

	@Override
	public @Nullable Object get(ItemStack stack, String tag) {
		return tags.getOrDefault(stack, Map.of()).get(tag);
	}

	@Override
	public @Nullable String getString(ItemStack stack, String tag) {
		Object value = get(stack, tag);
		return value == null ? null : String.valueOf(value);
	}

	@Override
	public int getInt(ItemStack stack, String tag) {
		return get(stack, tag) instanceof Number number ? number.intValue() : 0;
	}

	@Override
	public void set(ItemStack stack, String tag, NbtType type, @Nullable Object value) {
		tags.computeIfAbsent(stack, key -> new java.util.HashMap<>()).put(tag, value);
	}

	@Override
	public void remove(ItemStack stack, String tag) {
		tags.getOrDefault(stack, new java.util.HashMap<>()).remove(tag);
	}

	@Override
	public @Nullable String describe(ItemStack stack) {
		return String.valueOf(tags.get(stack));
	}
}
