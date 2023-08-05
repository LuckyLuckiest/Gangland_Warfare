package me.luckyraven.bukkit;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadWriteItemNBT;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import me.luckyraven.util.ChatUtil;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

public class ItemBuilder {

	private static final Map<String, Object> SPECIAL_NBT = new HashMap<>();
	private final        ItemStack           itemStack;

	public ItemBuilder(ItemStack itemStack) {
		this.itemStack = itemStack;
	}

	public ItemBuilder(Material material) {
		this.itemStack = new ItemStack(material);
	}

	public static Map<String, Object> getSpecialNBTs() {
		return SPECIAL_NBT;
	}

	public ItemBuilder setDisplayName(@Nullable String displayName) {
		if (displayName == null) displayName = "";
		if (displayName.isEmpty()) displayName = " ";

		@Nullable String finalDisplayName = displayName;

		modifyNBT(nbt -> nbt.modifyMeta(
				(readableNBT, itemMeta) -> itemMeta.setDisplayName(ChatUtil.color(finalDisplayName))));
		return this;
	}

	public ItemBuilder setLore(String... lore) {
		List<String> loreList = new ArrayList<>(Arrays.stream(lore).map(ChatUtil::color).toList());

		modifyNBT(nbt -> nbt.modifyMeta((readableNBT, itemMeta) -> itemMeta.setLore(loreList)));
		return this;
	}

	public ItemBuilder setLore(@Nullable List<String> lore) {
		List<String> loreList;
		if (lore == null) loreList = new ArrayList<>();
		else loreList = new ArrayList<>(lore.stream().map(ChatUtil::color).toList());

		modifyNBT(nbt -> nbt.modifyMeta((readableNBT, itemMeta) -> itemMeta.setLore(loreList)));
		return this;
	}

	public ItemBuilder addEnchantment(Enchantment enchantment, int level) {
		modifyNBT(nbt -> nbt.modifyMeta((readableNBT, itemMeta) -> itemMeta.addEnchant(enchantment, level, true)));
		return this;
	}

	public ItemBuilder addItemFlags(ItemFlag... flags) {
		modifyNBT(nbt -> nbt.modifyMeta((readableNBT, itemMeta) -> itemMeta.addItemFlags(flags)));
		return this;
	}

	public ItemBuilder setUnbreakable(boolean unbreakable) {
		modifyNBT(nbt -> nbt.setBoolean("Unbreakable", unbreakable));
		return this;
	}

	public ItemBuilder setCustomModelData(int customModelData) {
		modifyNBT(nbt -> nbt.setInteger("CustomModelData", customModelData));
		return this;
	}

	public ItemBuilder setAmount(int amount) {
		modifyNBT(nbt -> nbt.setInteger("Count", amount));
		return this;
	}

	public ItemBuilder setDurability(short durability) {
		modifyNBT(nbt -> nbt.setShort("Damage", durability));
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

	public ItemBuilder customHead(String base64) {
		if (itemStack.getType() != Material.PLAYER_HEAD) return this;

		modifyNBT(nbt -> {
			ReadWriteNBT skullOwnerCompound = nbt.getOrCreateCompound("SkullOwner");

			skullOwnerCompound.setUUID("Id", UUID.randomUUID());

			skullOwnerCompound.getOrCreateCompound("Properties").getCompoundList("textures").addCompound().setString(
					"Value", base64);
		});

		return this;
	}

	public ItemStack build() {
		return itemStack;
	}

	public ItemBuilder addTag(String tag, Object value) {
		if (value instanceof String) modifyNBT(nbt -> nbt.setString(tag, (String) value));
		else if (value instanceof Byte) modifyNBT(nbt -> nbt.setByte(tag, (byte) value));
		else if (value instanceof byte[]) modifyNBT(nbt -> nbt.setByteArray(tag, (byte[]) value));
		else if (value instanceof Short) modifyNBT(nbt -> nbt.setShort(tag, (short) value));
		else if (value instanceof Integer) modifyNBT(nbt -> nbt.setInteger(tag, (int) value));
		else if (value instanceof int[]) modifyNBT(nbt -> nbt.setIntArray(tag, (int[]) value));
		else if (value instanceof Long) modifyNBT(nbt -> nbt.setLong(tag, (long) value));
		else if (value instanceof Double) modifyNBT(nbt -> nbt.setDouble(tag, (double) value));
		else if (value instanceof Float) modifyNBT(nbt -> nbt.setFloat(tag, (float) value));
		else if (value instanceof Boolean) modifyNBT(nbt -> nbt.setBoolean(tag, (boolean) value));
		else if (value instanceof ItemStack) modifyNBT(nbt -> nbt.setItemStack(tag, (ItemStack) value));
		else if (value instanceof ItemStack[]) modifyNBT(nbt -> nbt.setItemStackArray(tag, (ItemStack[]) value));
		else if (value instanceof UUID) modifyNBT(nbt -> nbt.setUUID(tag, (UUID) value));
		else if (value instanceof Enum) modifyNBT(nbt -> nbt.setEnum(tag, (Enum<?>) value));

		SPECIAL_NBT.put(tag, value);

		return this;
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
