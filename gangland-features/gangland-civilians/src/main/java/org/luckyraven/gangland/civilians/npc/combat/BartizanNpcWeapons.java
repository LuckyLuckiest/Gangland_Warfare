package org.luckyraven.gangland.civilians.npc.combat;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.bartizan.api.BartizanApi;
import org.luckyraven.keystone.npc.NpcDifficulty;
import org.luckyraven.keystone.npc.spi.NpcRangedAttack;

/**
 * A factory hook, <strong>not</strong> an {@link NpcRangedAttack} implementation (PICK Amendments ruling (b)):
 * Bartizan's {@code NpcWeaponController} is the SPI's only production implementation. This class resolves
 * {@link BartizanApi} lazily on every call — never cached in a field set at bean construction, since Bartizan may
 * enable after Gangland (P2 R8) — and hands the result to {@code AbstractNpc.setRangedAttack(...)}.
 *
 * <p>Called from {@code CivilianNpcFactory} (this module) and {@code CopNpcFactory} (cops-n-crooks, group K) at
 * spawn time. Returns {@link NpcRangedAttack#NONE} when {@code weaponName} is {@code null} or Bartizan is absent, so
 * every caller degrades to the vanilla/melee fallback with no null checks of its own.
 */
public class BartizanNpcWeapons {

	/**
	 * Resolves a {@link NpcRangedAttack} for {@code weaponName}, or {@link NpcRangedAttack#NONE} when the name is
	 * {@code null}, Bartizan is absent, or the name is not a weapon Bartizan recognises (review M1). Bartizan's
	 * {@code NpcWeaponFactoryImpl.create} throws {@code IllegalArgumentException} for an unknown name (Bartizan
	 * commit {@code 38913d5}), so an unvalidated {@code material:}/bare-material pool entry would abort the spawn —
	 * the {@link org.luckyraven.bartizan.api.item.WeaponItemApi#isValidWeaponName(String)} guard below is what keeps
	 * that from happening.
	 */
	public NpcRangedAttack create(LivingEntity shooter, @Nullable String weaponName, NpcDifficulty difficulty) {
		if (weaponName == null) return NpcRangedAttack.NONE;

		RegisteredServiceProvider<BartizanApi> rsp = Bukkit.getServicesManager().getRegistration(BartizanApi.class);
		if (rsp == null) return NpcRangedAttack.NONE;

		BartizanApi api = rsp.getProvider();
		if (!api.items().isValidWeaponName(weaponName)) return NpcRangedAttack.NONE;

		return api.npcWeapons()
		          .create(shooter, weaponName, difficulty.getFireRateMultiplier(), difficulty.getAimError());
	}

	/**
	 * Builds the item to place in an NPC's main hand for {@code weaponName}, or {@code null} when Bartizan is
	 * absent, {@code weaponName} is {@code null}, or the name is unrecognised (review M2) — the caller then falls
	 * back to the vanilla weapon pool instead of leaving the NPC bare-handed.
	 */
	@Nullable
	public ItemStack buildItem(@Nullable String weaponName) {
		if (weaponName == null) return null;

		RegisteredServiceProvider<BartizanApi> rsp = Bukkit.getServicesManager().getRegistration(BartizanApi.class);
		if (rsp == null) return null;

		BartizanApi api = rsp.getProvider();
		if (!api.items().isValidWeaponName(weaponName)) return null;

		return api.items().buildItem(weaponName);
	}

}
