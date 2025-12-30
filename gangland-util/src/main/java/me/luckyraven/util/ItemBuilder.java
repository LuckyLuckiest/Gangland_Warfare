package me.luckyraven.util;

import com.cryptomorin.xseries.XMaterial;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadWriteItemNBT;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.nbtapi.iface.ReadableItemNBT;
import lombok.Getter;
import me.luckyraven.exception.PluginException;
import me.luckyraven.util.utilities.ChatUtil;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public class ItemBuilder implements Cloneable {

	private final ItemStack itemStack;

	private @Getter String            displayName;
	private @Getter List<String>      lore;
	private @Getter List<Enchantment> enchantments = new ArrayList<>();

	public ItemBuilder(ItemStack itemStack) {
		this.itemStack = itemStack;
	}

	/**
	 * This constructor is primarily used to simply create a new instance of the ItemBuilder, never use this constructor
	 * to create an indirect instance of an ItemStack.
	 * <p>
	 * There will be multiple problems risen from creating a new instance of ItemBuilder with a material, mainly the
	 * NBTTag wouldn't be recognized since it would be saved in the ItemStack and not the Material.
	 *
	 * @param material The material type used.
	 */
	public ItemBuilder(Material material) {
		this.itemStack = new ItemStack(material);
	}

	public ItemBuilder setDisplayName(@Nullable String displayName) {
		if (displayName == null || displayName.isEmpty()) displayName = " ";

		this.displayName = displayName;

		modifyNBT(nbt -> nbt.modifyMeta(
				(readableNBT, itemMeta) -> itemMeta.setDisplayName(ChatUtil.color(this.displayName))));
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

		this.lore = loreList;

		modifyNBT(nbt -> nbt.modifyMeta((readableNBT, itemMeta) -> itemMeta.setLore(this.lore)));
		return this;
	}

	public ItemBuilder addEnchantment(Enchantment enchantment, int level) {
		enchantments.add(enchantment);

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

	public ItemBuilder setMaxStackSize(int size) {
		boolean isAtLeast1_20_5 = ProtocolVersion.v1_20_5.getVersion() >=
								  Via.getAPI().getServerVersion().lowestSupportedProtocolVersion().getVersion();
		if (isAtLeast1_20_5) {
			modifyNBT(nbt -> nbt.setInteger("max_stack_size", size));
		}

		return this;
	}

	public ItemBuilder setAmount(int amount) {
		itemStack.setAmount(amount);
		return this;
	}

	public ItemBuilder decreaseDurability(short amount) {
		if (itemStack == null || itemStack.getType() == Material.AIR ||
			!(itemStack.getItemMeta() instanceof Damageable damageable)) {
			return this;
		}

		int currentDurability = damageable.getDamage();

		return setDurability((short) (currentDurability + amount));
	}

	/**
	 * The item durability is the current in-game durability set to the item.
	 *
	 * @return item durability
	 */
	public short getItemMaxDurability() {
		if (itemStack == null || itemStack.getType() == Material.AIR) {
			return (short) 0;
		}

		return itemStack.getType().getMaxDurability();
	}

	public short getItemDamagedDurability() {
		if (itemStack == null || itemStack.getType() == Material.AIR ||
			!(itemStack.getItemMeta() instanceof Damageable damageable)) {
			return (short) 0;
		}

		return (short) damageable.getDamage();
	}

	public ItemBuilder setDurability(short durability) {
		if (itemStack == null || itemStack.getType() == Material.AIR ||
			!(itemStack.getItemMeta() instanceof Damageable damageable)) {
			return this;
		}

		damageable.setDamage(durability);
		itemStack.setItemMeta(damageable);

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
		if (itemStack.getType() != XMaterial.PLAYER_HEAD.get()) return this;

		modifyNBT(nbt -> {
			ReadWriteNBT skullOwnerCompound = nbt.getOrCreateCompound("SkullOwner");

			skullOwnerCompound.setUUID("Id", UUID.randomUUID());

			ReadWriteNBT propertiesCompound = skullOwnerCompound.getOrCreateCompound("Properties");

			// clear existing textures
			propertiesCompound.removeKey("textures");
			propertiesCompound.getCompoundList("textures").addCompound().setString("Value", base64);
		});

		return this;
	}

	public ItemBuilder customHead(UUID uniqueId) {
		if (itemStack.getType() != XMaterial.PLAYER_HEAD.get()) return this;

		modifyNBT(nbt -> {
			ReadWriteNBT skullOwnerCompound = nbt.getOrCreateCompound("SkullOwner");

			skullOwnerCompound.setUUID("Id", uniqueId);
		});

		return this;
	}

	public ItemStack build() {
		return itemStack;
	}

	public ItemBuilder modifyTag(String tag, Object value) {
		Consumer<ReadWriteItemNBT> tagModifier = nbt -> addTag(tag, value);
		modifyNBT(tagModifier);
		return this;
	}

	/**
	 * Adds the specific NBT tag.
	 *
	 * @param tag the name
	 * @param value the associated value
	 *
	 * @return this class instance
	 */
	public ItemBuilder addTag(String tag, Object value) {
		switch (value) {
			case Byte b -> modifyNBT(nbt -> nbt.setByte(tag, (byte) value));
			case byte[] bytes -> modifyNBT(nbt -> nbt.setByteArray(tag, (byte[]) value));
			case Short i -> modifyNBT(nbt -> nbt.setShort(tag, (short) value));
			case Integer i -> modifyNBT(nbt -> nbt.setInteger(tag, (int) value));
			case int[] ints -> modifyNBT(nbt -> nbt.setIntArray(tag, (int[]) value));
			case Long l -> modifyNBT(nbt -> nbt.setLong(tag, (long) value));
			case Double v -> modifyNBT(nbt -> nbt.setDouble(tag, (double) value));
			case Float v -> modifyNBT(nbt -> nbt.setFloat(tag, (float) value));
			case Boolean b -> modifyNBT(nbt -> nbt.setBoolean(tag, (boolean) value));
			case ItemStack stack -> modifyNBT(nbt -> nbt.setItemStack(tag, (ItemStack) value));
			case ItemStack[] itemStacks -> modifyNBT(nbt -> nbt.setItemStackArray(tag, (ItemStack[]) value));
			case UUID uuid -> modifyNBT(nbt -> nbt.setUUID(tag, (UUID) value));
			case Enum<?> anEnum -> modifyNBT(nbt -> nbt.setEnum(tag, (Enum<?>) value));
			case null, default -> modifyNBT(nbt -> nbt.setString(tag, value != null ? value.toString() : "null"));
		}

		return this;
	}

	public boolean hasNBTTag(String tag) {
		String value = getStringTagData(tag);

		if (value == null) return false;

		return !value.isEmpty();
	}

	public String getStringTagData(String tag) {
		return NBT.get(itemStack, (Function<ReadableItemNBT, String>) nbt -> nbt.getString(tag));
	}

	public int getIntegerTagData(String tag) {
		return NBT.get(itemStack, (Function<ReadableItemNBT, Integer>) nbt -> nbt.getInteger(tag));
	}

	public Material getType() {
		return itemStack.getType();
	}

	@Override
	public String toString() {
		return NBT.itemStackToNBT(itemStack).toString();
	}

	@Override
	public ItemBuilder clone() {
		try {
			return (ItemBuilder) super.clone();
		} catch (CloneNotSupportedException exception) {
			throw new PluginException();
		}
	}

}
