package me.luckyraven.bukkit;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadWriteItemNBT;
import me.luckyraven.util.ChatUtil;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class ItemBuilder {

	private final ItemStack itemStack;

	public ItemBuilder(ItemStack itemStack) {
		this.itemStack = itemStack;
	}

	public ItemBuilder(Material material) {
		this.itemStack = new ItemStack(material);
	}

	public ItemBuilder setDisplayName(@Nullable String displayName) {
		if (displayName == null) displayName = "";
		if (displayName.isEmpty()) displayName = "&0|";
		@Nullable String finalDisplayName = displayName;
		NBT.modify(itemStack, nbt -> {
			nbt.modifyMeta((readableNBT, itemMeta) -> itemMeta.setDisplayName(ChatUtil.color(finalDisplayName)));
		});
		return this;
	}

	public ItemBuilder setLore(String... lore) {
		List<String> loreList = new ArrayList<>(Arrays.stream(lore).map(ChatUtil::color).toList());
		NBT.modify(itemStack, nbt -> {
			nbt.modifyMeta((readableNBT, itemMeta) -> itemMeta.setLore(loreList));
		});
		return this;
	}

	public ItemBuilder setLore(@Nullable List<String> lore) {
		List<String> loreList;
		if (lore == null) loreList = new ArrayList<>();
		else loreList = new ArrayList<>(lore.stream().map(ChatUtil::color).toList());
		NBT.modify(itemStack, nbt -> {
			nbt.modifyMeta((readableNBT, itemMeta) -> itemMeta.setLore(loreList));
		});
		return this;
	}

	public ItemBuilder addEnchantment(Enchantment enchantment, int level) {
		NBT.modify(itemStack, nbt -> {
			nbt.modifyMeta((readableNBT, itemMeta) -> itemMeta.addEnchant(enchantment, level, true));
		});
		return this;
	}

	public ItemBuilder addItemFlags(ItemFlag... flags) {
		NBT.modify(itemStack, nbt -> {
			nbt.modifyMeta((readableNBT, itemMeta) -> itemMeta.addItemFlags(flags));
		});
		return this;
	}

	public ItemBuilder setUnbreakable(boolean unbreakable) {
		NBT.modify(itemStack, nbt -> {
			nbt.setBoolean("Unbreakable", unbreakable);
		});
		return this;
	}

	public ItemBuilder setCustomModelData(int customModelData) {
		NBT.modify(itemStack, nbt -> {
			nbt.setInteger("CustomModelData", customModelData);
		});
		return this;
	}

	public ItemBuilder setAmount(int amount) {
		NBT.modify(itemStack, nbt -> {
			nbt.setInteger("Count", amount);
		});
		return this;
	}

	public ItemBuilder setDurability(short durability) {
		NBT.modify(itemStack, nbt -> {
			nbt.setShort("Damage", durability);
		});
		return this;
	}

	public ItemBuilder modifyNBT(Consumer<ReadWriteItemNBT> nbtModifier) {
		NBT.modify(itemStack, nbtModifier);
		return this;
	}

	public ItemBuilder setItemMeta(ItemMeta itemMeta) {
		itemStack.setItemMeta(itemMeta);
		return this;
	}

	public ItemStack build() {
		return itemStack;
	}

	public void addTag(String tag, String value) {
		modifyNBT(nbt -> nbt.setString(tag, value));
	}

	public boolean hasNBTTag(String tag) {
		String value = NBT.get(itemStack, nbt -> nbt.getString(tag));

		if (value == null) return false;

		return !value.isEmpty();
	}

	@Override
	public String toString() {
		return NBT.itemStackToNBT(itemStack).toString();
	}

}
