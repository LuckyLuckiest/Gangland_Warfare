package org.luckyraven.gangland.copsncrooks.npc.police.config;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.npc.RetreatSettings;
import org.luckyraven.gangland.npc.TacticsConfig;
import org.luckyraven.keystone.npc.NpcDifficulty;
import org.luckyraven.keystone.npc.NpcEngagement;
import org.luckyraven.keystone.npc.NpcFanPlacement;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A cop's job inside its squad ({@code copsncrooks/cop_roles.yml} {@code Roles.<Name>}), laid over its tier when it spawns: the
 * role shifts where the cop stands and how it fights, and its {@link Kit kits} dress and arm it. Picked per spawn from
 * {@code Squad_Composition} by {@link #nextRole}.
 *
 * @param name             the role's key under {@code Roles}; compositions refer to it.
 * @param displayName      the role's plain word ({@code Display.Name}, default the name), e.g. for radio lines.
 * @param placement        where on the squad's fan the cop prefers to stand.
 * @param rangedMin        the cop's own firing band, {@code null} for the settings.yml band; set together with
 *                         {@code rangedMax}.
 * @param rangedMax        see {@code rangedMin}; clamped under the held weapon's reach at spawn ({@link #rangedBand}).
 * @param healthMultiplier multiplies the tier's {@code Health}.
 * @param leaderPriority   the cop's claim to lead its squad (radio voice, {@code Leader_Down}); the highest leads.
 * @param strafeDegrees    replaces the tier's {@code Tactics.Strafe_Degrees}, {@code null} keeps it.
 * @param fireRateScale    multiplies the tier's {@code Fire_Rate_Multiplier} (below 1 fires slower).
 * @param difficultyBonus  steps above the tier's {@code Difficulty}, capped at the top one (better aim).
 * @param retreat          replaces {@code Cops.Retreat} for this role, {@code null} keeps it.
 * @param blockFraction    the share of damage taken off a hit from inside the front cone (0 = no block).
 * @param blockConeDegrees the front cone's full width, in degrees.
 * @param medic            the role treats hurt squad mates (field care).
 * @param commander        the squad falls back briefly and radios {@code Commander_Down} when this cop dies.
 * @param color            {@code Display.Color}, put before the symbol and the word in {@link #display()}.
 * @param symbol           {@code Display.Symbol}, e.g. the Medic's cross; empty for none.
 * @param kit              the role's own weapons and gear ({@code Weapon_Pool} / {@code Gear}), on every tier.
 * @param tierKits         per tier number ({@code Tiers.<level or tier name>}), read over {@code kit} on that tier.
 */
public record CopRole(
		String name,
		String displayName,
		NpcFanPlacement placement,
		@Nullable Double rangedMin,
		@Nullable Double rangedMax,
		double healthMultiplier,
		int leaderPriority,
		@Nullable Double strafeDegrees,
		double fireRateScale,
		int difficultyBonus,
		@Nullable RetreatSettings retreat,
		double blockFraction,
		double blockConeDegrees,
		boolean medic,
		boolean commander,
		String color,
		String symbol,
		Kit kit,
		Map<Integer, Kit> tierKits
) {

	/** The narrowest band a reach clamp leaves: the Keystone band must keep {@code min < max}. */
	static final double MIN_BAND_WIDTH = 2.0;

	public CopRole {
		tierKits = Map.copyOf(tierKits);
	}

	/** The pre-gear shape: a plain display, no tier kits, and {@code offHand} as the role's only gear. */
	public CopRole(String name, String displayName, NpcFanPlacement placement, @Nullable Double rangedMin,
	               @Nullable Double rangedMax, double healthMultiplier, @Nullable ItemStack offHand, int leaderPriority,
	               @Nullable Double strafeDegrees, double fireRateScale, int difficultyBonus,
	               @Nullable RetreatSettings retreat, double blockFraction, double blockConeDegrees, boolean medic,
	               boolean commander) {
		this(name, displayName, placement, rangedMin, rangedMax, healthMultiplier, leaderPriority, strafeDegrees,
		     fireRateScale, difficultyBonus, retreat, blockFraction, blockConeDegrees, medic, commander, "", "",
		     offHand == null ? Kit.EMPTY : new Kit(null, List.of(), null, null, null, null,
		                                           new Gear(offHand.getType(), null, false)),
		     Map.of());
	}

	/** {@code "&c\u271A Medic"}: the colour, the symbol and the word, the callsign's {@code %role%}. */
	public String display() {
		return color + (symbol.isEmpty() ? "" : symbol + " ") + displayName;
	}

	/**
	 * The kit a cop of this role wears on tier {@code tier}: that tier's own kit read slot by slot over the role's.
	 * On a melee tier ({@code canUseWeapons} false) the role's own {@code Weapon_Pool} is skipped, so the cop keeps the
	 * tier's melee weapon, unless the tier's own kit names one.
	 */
	public Kit kitFor(int tier, boolean canUseWeapons) {
		Kit base = canUseWeapons ? kit : kit.withoutWeapons();
		Kit own  = tierKits.get(tier);
		return own != null ? own.over(base) : base;
	}

	/** The item the cop holds in its off hand on tier {@code tier} (the Defender's shield); {@code null} for none. */
	public @Nullable ItemStack offHandFor(int tier) {
		Gear offHand = kitFor(tier, true).offHand();
		return offHand != null ? offHand.toItem() : null;
	}

	/**
	 * The tier with this role laid over it: {@code Health} multiplied, {@code Difficulty} stepped up,
	 * {@code Fire_Rate_Multiplier} scaled and {@code Strafe_Degrees} replaced; a LEGACY tier stays LEGACY. The
	 * {@link #kitFor kit} for the tier replaces each armour slot it sets and the weapon pool (a pool with no vanilla
	 * item keeps the tier's vanilla items, the fallback when no Bartizan gun resolves). Every other field, the display
	 * name included, is the tier's.
	 */
	public CopTierConfig overlay(CopTierConfig tier) {
		NpcEngagement engagement = tier.tactics().engagement();
		if (strafeDegrees != null && engagement.enabled())
			engagement = new NpcEngagement(true, strafeDegrees, engagement.repositionMs(), engagement.movingAimError());

		NpcDifficulty[] difficulties = NpcDifficulty.values();
		NpcDifficulty difficulty = difficulties[Math.min(difficulties.length - 1,
		                                                 tier.difficulty().ordinal() + difficultyBonus)];

		Kit     worn = kitFor(tier.tier(), tier.canUseWeapons());
		boolean pool = worn.weaponNames() != null;

		return new CopTierConfig(tier.tier(), tier.displayName(), tier.health() * healthMultiplier, tier.damage(),
		                         tier.speed(), tier.cuffRadius(), tier.canUseWeapons(), tier.skipCuffing(),
		                         pool ? worn.weaponNames() : tier.weaponNamePool(),
		                         pool && !worn.weaponItems().isEmpty() ? worn.weaponItems() : tier.weaponPool(),
		                         Gear.wear(worn.helmet(), tier.helmet()),
		                         Gear.wear(worn.chestplate(), tier.chestplate()),
		                         Gear.wear(worn.leggings(), tier.leggings()), Gear.wear(worn.boots(), tier.boots()),
		                         difficulty,
		                         new TacticsConfig(engagement, tier.tactics().formationArc()),
		                         tier.fireRateMultiplier() * fireRateScale);
	}

	/**
	 * The firing band {@code {min, max}} for a cop holding a weapon that reaches {@code reach} blocks ({@code null}:
	 * unknown, no clamp): the max never passes the reach, and the min stays at least {@link #MIN_BAND_WIDTH} under
	 * it. {@code null} when the role declares no band.
	 */
	public double @Nullable [] rangedBand(@Nullable Double reach) {
		if (rangedMin == null || rangedMax == null) return null;
		double max = reach != null ? Math.min(rangedMax, reach) : rangedMax;
		double min = Math.max(0, Math.min(rangedMin, max - MIN_BAND_WIDTH));
		return new double[]{min, max};
	}

	/**
	 * Whether a hit from {@code from} lands inside this role's front cone, around the way {@code self} faces
	 * (horizontally: height is ignored). Never with no {@code Block_Fraction}.
	 */
	public boolean blocks(Location self, Location from) {
		if (blockFraction <= 0) return false;
		double dx = from.getX() - self.getX(), dz = from.getZ() - self.getZ();
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length < 1e-6) return true; // point blank: whatever is in the cop's face

		double yaw = Math.toRadians(self.getYaw());
		double cos = (-Math.sin(yaw) * dx + Math.cos(yaw) * dz) / length;
		return cos >= Math.cos(Math.toRadians(blockConeDegrees / 2));
	}

	/**
	 * The role the next cop joining a group gets: walks {@code composition} in order, each entry filled by one live
	 * cop holding that role, and returns the first unfilled entry; with every entry filled, the last one again (backup
	 * and extra cops). {@code liveRoles} are the group's counted cops' roles ({@code null} for a role-less cop).
	 * {@code null} when there is no composition (roles off).
	 */
	public static @Nullable CopRole nextRole(@Nullable List<CopRole> composition,
	                                         Collection<? extends @Nullable CopRole> liveRoles) {
		int slot = nextSlot(composition, liveRoles);
		return slot < 0 ? null : composition.get(slot);
	}

	/**
	 * The index {@link #nextRole} picks: the first composition entry no live cop fills, else the last index; {@code -1}
	 * when there is no composition. The index also addresses {@code CopConfigProvider.getSquadTiers}.
	 */
	public static int nextSlot(@Nullable List<CopRole> composition,
	                           Collection<? extends @Nullable CopRole> liveRoles) {
		if (composition == null || composition.isEmpty()) return -1;

		Map<String, Integer> held = new HashMap<>();
		for (CopRole role : liveRoles)
			if (role != null) held.merge(role.name(), 1, Integer::sum);

		for (int slot = 0; slot < composition.size(); slot++) {
			String name  = composition.get(slot).name();
			int    count = held.getOrDefault(name, 0);
			if (count == 0) return slot;
			held.put(name, count - 1);
		}
		return composition.size() - 1;
	}

	/**
	 * Weapons and clothes ({@code Weapon_Pool} and {@code Gear}), for a whole role or one of its tiers. A {@code null}
	 * slot or pool is not set here and falls through ({@link #over}); {@link Gear#NONE} empties a slot.
	 *
	 * @param weaponNames the Bartizan guns ({@code weapon:<name>} entries, prefix removed); {@code null}: no pool here.
	 * @param weaponItems the pool's vanilla items: held when no Bartizan gun resolves.
	 */
	public record Kit(@Nullable List<String> weaponNames, List<ItemStack> weaponItems, @Nullable Gear helmet,
	                  @Nullable Gear chestplate, @Nullable Gear leggings, @Nullable Gear boots,
	                  @Nullable Gear offHand) {

		public static final Kit EMPTY = new Kit(null, List.of(), null, null, null, null, null);

		public Kit {
			weaponNames = weaponNames == null ? null : List.copyOf(weaponNames);
			weaponItems = List.copyOf(weaponItems);
		}

		/** This kit with every slot and the pool it leaves unset taken from {@code base}. */
		public Kit over(Kit base) {
			boolean pool = weaponNames != null;
			return new Kit(pool ? weaponNames : base.weaponNames, pool ? weaponItems : base.weaponItems,
			               helmet != null ? helmet : base.helmet, chestplate != null ? chestplate : base.chestplate,
			               leggings != null ? leggings : base.leggings, boots != null ? boots : base.boots,
			               offHand != null ? offHand : base.offHand);
		}

		Kit withoutWeapons() {
			return new Kit(null, List.of(), helmet, chestplate, leggings, boots, offHand);
		}
	}

	/**
	 * One piece of gear: a material, a leather dye ({@code Leather_Color}, leather only) and an enchantment glow. Kept
	 * as a description and built per spawn ({@link #toItem}), so reading the config never needs a server.
	 */
	public record Gear(Material material, @Nullable Color leatherColor, boolean glow) {

		/** An explicitly empty slot ({@code ""}): it takes the tier's armour off too. */
		public static final Gear NONE = new Gear(Material.AIR, null, false);

		/** The item for this piece; {@code null} for {@link #NONE}. */
		public @Nullable ItemStack toItem() {
			if (material == Material.AIR) return null;
			ItemStack item = new ItemStack(material);
			if (leatherColor == null && !glow) return item;

			ItemMeta meta = item.getItemMeta();
			if (meta == null) return item;
			if (leatherColor != null && meta instanceof LeatherArmorMeta leather) leather.setColor(leatherColor);
			if (glow) {
				// getByKey, not XEnchantment: the vanilla key is stable since the 1.13 rename (see SlotItemFactory)
				Enchantment unbreaking = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
				if (unbreaking != null) meta.addEnchant(unbreaking, 1, true);
				meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
			}
			item.setItemMeta(meta);
			return item;
		}

		/** {@code piece}'s item when set, else the tier's own {@code fallback}. */
		static @Nullable ItemStack wear(@Nullable Gear piece, @Nullable ItemStack fallback) {
			return piece != null ? piece.toItem() : fallback;
		}
	}
}
