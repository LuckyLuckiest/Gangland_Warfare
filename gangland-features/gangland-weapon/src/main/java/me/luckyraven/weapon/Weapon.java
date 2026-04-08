package me.luckyraven.weapon;

import com.cryptomorin.xseries.XPotion;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import me.luckyraven.exception.PluginException;
import me.luckyraven.item.repair.Repairable;
import me.luckyraven.item.repair.RepairableType;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.Placeholder;
import me.luckyraven.weapon.dto.*;
import me.luckyraven.weapon.durability.DurabilityCalculator;
import me.luckyraven.weapon.projectile.recoil.RecoilManager;
import me.luckyraven.weapon.projectile.spread.SpreadManager;
import me.luckyraven.weapon.reload.Reload;
import me.luckyraven.weapon.types.WeaponType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Getter
@Setter
public abstract class Weapon implements Repairable, Cloneable, Comparable<Weapon> {

	private final String                 name;
	private final String                 displayName;
	private final WeaponType             category;
	private final Material               material;
	private final int                    customModelData;
	private final short                  durability;
	private final List<String>           lore;
	private final boolean                dropHologram;
	@Setter(AccessLevel.NONE)
	private final List<String>           deathMessages;
	// Runtime state
	private final Map<WeaponTag, Object> tags;
	// Reload configuration (immutable — set at construction)
	@Nullable
	private final ReloadData             reloadData;
	@Nullable
	private final AmmunitionData         ammunitionData;
	// Core identity
	@Setter(AccessLevel.NONE)
	private       UUID                   uuid;
	// Configuration groups (mutable — set by WeaponAddon after construction)
	private       DurabilityData         durabilityData;
	private       SoundData              soundData;
	private       ReloadActionBarData    reloadActionBarData;
	private       ModifiersData          modifiersData;
	private       RecoilData             recoilData;
	private       ScopeData              scopeData;
	private       SpreadData             spreadData;
	// Runtime state
	private       int                    currentMagCapacity;
	private       SelectiveFire          currentSelectiveFire;
	/**
	 * Modes the weapon is permitted to cycle through. Set by the parser from the {@code Allowed_Modes} yml key. Empty
	 * or {@code null} means "no restriction" — the legacy 3-mode SINGLE/BURST/AUTO cycle is used. The configured
	 * starting {@link #currentSelectiveFire} must always be present in this set when the set is non-empty.
	 */
	private       Set<SelectiveFire>     allowedSelectiveFires;

	private String changingDisplayName;
	private short  currentDurability;

	// Managers (non-serialized)
	@Setter(AccessLevel.NONE)
	private DurabilityCalculator durabilityCalculator;
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private Reload               reload;
	@Setter(AccessLevel.NONE)
	private RecoilManager        recoil;
	@Setter(AccessLevel.NONE)
	private SpreadManager        spread;
	// Placeholder resolver for display name + lore (injected by WeaponAddon after construction).
	@Nullable
	private Placeholder          placeholder;

	protected Weapon(UUID uuid, String name, String displayName, WeaponType category, Material material,
	                 int customModelData, short durability, List<String> lore, boolean dropHologram,
	                 @Nullable List<String> deathMessages, @Nullable ReloadData reloadData,
	                 @Nullable AmmunitionData ammunitionData) {
		this.uuid              = uuid;
		this.name              = name;
		this.displayName       = displayName;
		this.category          = category;
		this.material          = material;
		this.customModelData   = customModelData;
		this.durability        = durability;
		this.currentDurability = durability;
		this.lore              = lore;
		this.dropHologram      = dropHologram;
		this.deathMessages     = deathMessages;
		this.reloadData        = reloadData;
		this.ammunitionData    = ammunitionData;

		this.currentMagCapacity = ammunitionData != null ? ammunitionData.getMaxMagCapacity() : 0;

		this.tags                 = new TreeMap<>();
		this.recoil               = new RecoilManager(this);
		this.spread               = new SpreadManager(this);
		this.reload               = ammunitionData != null && reloadData != null ?
		                            reloadData.getType().createInstance(this, ammunitionData.getAmmoType()) :
		                            null;
		this.changingDisplayName  = buildDisplayName();
		this.durabilityCalculator = new DurabilityCalculator(this);
	}

	// --- Death message ---

	public abstract Weapon copyWithUUID(UUID newUuid);

	// --- Scope operations ---

	public static String getTagProperName(WeaponTag tag) {
		return tag.name().toLowerCase().replace("_", "-");
	}

	public Optional<String> pickDeathMessage() {
		if (deathMessages == null || deathMessages.isEmpty()) return Optional.empty();
		return Optional.of(deathMessages.get(ThreadLocalRandom.current().nextInt(deathMessages.size())));
	}

	public void scope(Player player, boolean bypass) {
		if (scopeData == null) return;
		if (!bypass && scopeData.isScoped()) return;

		scopeData.setScoped(true);

		applyEffect(player, XPotion.SLOWNESS, scopeData.getLevel());
	}

	public void unScope(Player player, boolean bypass) {
		if (scopeData == null) return;
		if (!bypass && !scopeData.isScoped()) return;

		scopeData.setScoped(false);

		removeEffect(player, XPotion.SLOWNESS);
	}

	public boolean isReloading() {
		return reload != null && reload.isReloading();
	}

	public void reload(JavaPlugin plugin, Player player, boolean removeAmmunition) {
		if (reload == null) return;
		reload.reload(plugin, player, removeAmmunition);
	}

	public void stopReloading() {
		if (reload == null) return;
		reload.stopReloading();
	}

	public boolean isMagazineFull() {
		if (ammunitionData == null) return true;
		return currentMagCapacity >= ammunitionData.getMaxMagCapacity();
	}

	public boolean isMagazineEmpty() {
		if (reloadData == null) return false;
		return currentMagCapacity <= 0;
	}

	public void addAmmunition(int amount) {
		if (ammunitionData == null) return;
		currentMagCapacity = Math.min(ammunitionData.getMaxMagCapacity(), currentMagCapacity + amount);
	}

	/**
	 * Consumes one shot's worth of ammunition. Returns {@code false} if the magazine is empty. Subclasses may override
	 * to change the consume amount (e.g. Gun uses projectileData.getConsumed()).
	 */
	public boolean consumeShot() {
		if (isMagazineEmpty()) return false;
		currentMagCapacity = Math.max(0, currentMagCapacity - 1);
		return true;
	}

	public boolean requiresReload() {
		return reloadData != null && !isMagazineFull();
	}

	@NotNull
	@Override
	public ItemStack buildItem() {
		return buildItem(null);
	}

	@NotNull
	public ItemStack buildItem(@Nullable Player player) {
		ItemBuilder builder = new ItemBuilder(material);
		builder.setDisplayName(resolvePlaceholder(player, changingDisplayName))
		       .setLore(resolvePlaceholder(player, lore));

		short currentDamage = (short) Math.floor(
				(durability - currentDurability) * (builder.getItemMaxDurability() / (double) durability));
		builder.setDurability(currentDamage);

		if (customModelData > 0) {
			builder.setCustomModelData(customModelData);
		}

		initializeTags(builder);
		builder.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
		return builder.build();
	}

	public void updateWeaponData(ItemBuilder itemBuilder) {
		updateWeaponData(itemBuilder, null);
	}

	public void updateWeaponData(ItemBuilder itemBuilder, @Nullable Player player) {
		this.changingDisplayName = buildDisplayName();
		itemBuilder.setDisplayName(resolvePlaceholder(player, changingDisplayName));

		boolean updatedSelectiveFire = false, updatedCurrentAmmo = false;

		for (WeaponTag tag : WeaponTag.values()) {
			if (containsTag(itemBuilder, tag)) continue;

			switch (tag) {
				case UUID -> tags.put(tag, uuid != null ? uuid.toString() : "");
				case WEAPON -> tags.put(tag, name);
				case SELECTIVE_FIRE -> {
					tags.put(tag, getSelectiveFireForTag());
					updatedSelectiveFire = true;
				}
				case AMMO_LEFT -> {
					tags.put(tag, getAmmoLeftForTag());
					updatedCurrentAmmo = true;
				}
			}
			itemBuilder.addTag(getTagProperName(tag), tags.get(tag));
		}

		if (!updatedSelectiveFire) updateTag(itemBuilder, WeaponTag.SELECTIVE_FIRE, getSelectiveFireForTag());
		if (!updatedCurrentAmmo) updateTag(itemBuilder, WeaponTag.AMMO_LEFT, getAmmoLeftForTag());
	}

	// --- Durability operations ---

	public void updateWeapon(Player player, ItemBuilder itemBuilder, int slot) {
		player.getInventory().setItem(slot, itemBuilder.build());
	}

	public void removeWeapon(Player player, int slot) {
		player.getInventory().setItem(slot, new ItemStack(Material.AIR));
	}

	public void increaseDurability(ItemBuilder itemBuilder, int amount) {
		durabilityCalculator.setDurability(itemBuilder, (short) (currentDurability + amount));
	}

	public void decreaseDurability(ItemBuilder itemBuilder, int amount) {
		durabilityCalculator.setDurability(itemBuilder, (short) (currentDurability - amount));
	}

	// --- Tag operations ---

	public boolean isBroken() {
		return currentDurability <= 0;
	}

	public void applyOnHitDurability(Player player, int slot) {
		int onShot = durabilityData.getOnShot();
		if (onShot <= 0) return;
		ItemStack item = player.getInventory().getItem(slot);
		if (item == null) return;
		ItemBuilder builder = new ItemBuilder(item);
		decreaseDurability(builder, onShot);
		updateWeapon(player, builder, slot);
	}

	// --- Copy / clone ---

	public boolean containsTag(ItemBuilder itemBuilder, WeaponTag tag) {
		return itemBuilder.hasNBTTag(getTagProperName(tag));
	}

	/**
	 * Base clone: shallow-copies via {@code Object.clone()}, then runs {@link #initClone(Weapon)}. Concrete subclasses
	 * override this, call {@code super.clone()} to get the base copy, then clone any additional mutable fields they
	 * own.
	 */
	@Override
	public Weapon clone() {
		try {
			Weapon copy = (Weapon) super.clone();
			copy.initClone(this);
			return copy;
		} catch (CloneNotSupportedException e) {
			throw new PluginException(e);
		}
	}

	// --- Comparable ---

	@Override
	public int compareTo(@NotNull Weapon other) {
		return Comparator.comparing(Weapon::getName, String.CASE_INSENSITIVE_ORDER)
		                 .thenComparing(Weapon::getCategory)
		                 .thenComparing(Weapon::getMaterial)
		                 .thenComparingInt(w -> w.durability)
		                 .compare(this, other);
	}

	@Override
	public String toString() {
		return String.format("Weapon{uuid='%s',name='%s',category='%s',material='%s'}", uuid, name, category, material);
	}

	// --- Repairable ---

	@NotNull
	@Override
	public String getRepairableId() {
		return name;
	}

	@Override
	public int getCurrentRepairDurability() {
		return currentDurability;
	}

	@Override
	public void setCurrentRepairDurability(int durability) {
		setCurrentDurability((short) durability);
	}

	@Override
	public int getMaxRepairDurability() {
		return durability;
	}

	@NotNull
	@Override
	public RepairableType getRepairableType() {
		return RepairableType.WEAPON;
	}

	public void applyPush(Player player) {
		// Never apply push if player is not on solid ground
		if (!isPlayerGrounded(player)) {
			return;
		}

		if (!player.isSneaking()) {
			push(player, recoilData.getPushPowerUp(), recoilData.getPushVelocity());
			return;
		}

		if (scopeData != null && scopeData.isScoped()) {
			push(player, recoilData.getPushPowerUp() / 2, recoilData.getPushVelocity() / 2);
		}
		// When sneaking and not scoped, no push is applied (implicitly returns)
	}

	/**
	 * Runs {@code text} through the injected placeholder resolver so configured {@code %gangland_*%} placeholders in
	 * the display name or lore are replaced before the item is rendered. Returns the original text unchanged when the
	 * resolver is absent or the input is empty.
	 */
	protected String resolvePlaceholder(@Nullable Player player, @Nullable String text) {
		if (text == null || text.isEmpty() || placeholder == null) return text;
		return placeholder.convert(player, text);
	}

	/**
	 * Resolves every line of a lore list via {@link #resolvePlaceholder(Player, String)}. Returns the original list
	 * unchanged when it is {@code null} or empty, or when the resolver is absent.
	 */
	protected List<String> resolvePlaceholder(@Nullable Player player, @Nullable List<String> loreLines) {
		if (loreLines == null || loreLines.isEmpty() || placeholder == null) return loreLines;
		List<String> resolved = new ArrayList<>(loreLines.size());
		for (String line : loreLines) {
			resolved.add(line == null ? null : placeholder.convert(player, line));
		}
		return resolved;
	}

	/**
	 * Shows ammo counter for any weapon that has reloadData configured.
	 */
	protected String buildDisplayName() {
		if (ammunitionData == null) return displayName + "&r";

		return String.format("%s&r &8«&6%d&7/&6%d&8»&r", displayName, currentMagCapacity,
		                     ammunitionData.getMaxMagCapacity());
	}

	protected SelectiveFire getSelectiveFireForTag() {
		return currentSelectiveFire != null ? currentSelectiveFire : SelectiveFire.SINGLE;
	}

	protected int getAmmoLeftForTag() {
		return currentMagCapacity;
	}

	protected void setUUID(UUID uuid) {
		this.uuid = uuid;
	}

	protected void updateTag(ItemBuilder itemBuilder, WeaponTag tag, Object value) {
		tags.replace(tag, value);
		itemBuilder.modifyTag(getTagProperName(tag), value);
	}

	protected void initializeTags(ItemBuilder itemBuilder) {
		tags.put(WeaponTag.UUID, uuid != null ? uuid.toString() : "");
		tags.put(WeaponTag.WEAPON, name);
		tags.put(WeaponTag.SELECTIVE_FIRE, getSelectiveFireForTag());
		tags.put(WeaponTag.AMMO_LEFT, getAmmoLeftForTag());
		tags.forEach((tag, value) -> itemBuilder.addTag(getTagProperName(tag), value));
	}

	/**
	 * Re-initialises shared mutable data after a {@code super.clone()} shallow copy. Subclasses must call this inside
	 * their own {@code clone()} implementations.
	 */
	protected void initClone(Weapon source) {
		this.tags.clear();
		this.durabilityData      = source.durabilityData != null ? source.durabilityData.clone() : null;
		this.soundData           = source.soundData != null ? source.soundData.clone() : null;
		this.reloadActionBarData = source.reloadActionBarData != null ? source.reloadActionBarData.clone() : null;
		this.modifiersData       = source.modifiersData != null ? source.modifiersData.clone() : null;
		this.recoilData          = source.recoilData != null ? source.recoilData.clone() : null;
		this.scopeData           = source.scopeData != null ? source.scopeData.clone() : null;

		if (this.scopeData != null) this.scopeData.setScoped(false);

		this.spreadData           = source.spreadData != null ? source.spreadData.clone() : null;
		this.recoil               = new RecoilManager(this);
		this.spread               = new SpreadManager(this);
		this.durabilityCalculator = new DurabilityCalculator(this);

		// ammunitionData and reloadData are final — Object.clone() already shallow-copies them.
		// Only reset runtime state derived from them.
		this.currentMagCapacity = source.ammunitionData != null ? source.ammunitionData.getMaxMagCapacity() : 0;
		this.reload             = source.reload != null ? source.reload.clone() : null;
		if (this.reload != null) this.reload.rebindWeapon(this);
		this.currentSelectiveFire = source.currentSelectiveFire;
	}

	protected void applyEffect(Player player, XPotion potion, int amplifier) {
		XPotion.of(potion.name())
		       .map(XPotion::getPotionEffectType)
		       .ifPresent(type -> player.addPotionEffect(
					   new PotionEffect(type, PotionEffect.INFINITE_DURATION, amplifier)));
	}

	protected void removeEffect(Player player, XPotion potion) {
		XPotion.of(potion.name()).map(XPotion::getPotionEffectType).ifPresent(player::removePotionEffect);
	}

	private void push(Player player, double powerUp, double push) {
		// Safety check - clamp values to reasonable limits
		powerUp = Math.clamp(powerUp, -0.5, 0.5);
		push    = Math.clamp(push, -0.5, 0.5);

		if (push > 0) push *= -1;

		// Don't apply if values are effectively zero
		if (Math.abs(powerUp) < 0.001 && Math.abs(push) < 0.001) {
			return;
		}

		Location location  = player.getLocation();
		Vector   direction = location.getDirection().multiply(push);
		Vector   upward    = new Vector(0, powerUp, 0);
		Vector   velocity  = direction.add(upward);

		// Clamp final velocity to prevent "moved too quickly" warnings
		double maxSpeed = 1.0;
		if (velocity.length() > maxSpeed) {
			velocity.normalize().multiply(maxSpeed);
		}

		player.setVelocity(velocity);
	}

	/**
	 * Checks if the player is firmly on the ground. Returns false if jumping, flying, in creative flight, swimming,
	 * etc.
	 */
	private boolean isPlayerGrounded(Player player) {
		// Check if player is flying (creative/spectator or elytra)
		if (player.isFlying() || player.isGliding()) {
			return false;
		}

		// Check if player is swimming or in water
		if (player.isSwimming() || player.isInWater()) {
			return false;
		}

		// Check if player is climbing (ladders, vines)
		if (player.isClimbing()) {
			return false;
		}

		// Check the block below - must be solid ground
		Location playerLoc  = player.getLocation();
		Block    blockBelow = playerLoc.subtract(0, 0.1, 0).getBlock();

		if (!blockBelow.getType().isSolid()) {
			return false;
		}

		// Check player's Y velocity - if moving up or down significantly, not grounded
		double yVelocity = player.getVelocity().getY();
		return !(Math.abs(yVelocity) > 0.1);
	}

}
