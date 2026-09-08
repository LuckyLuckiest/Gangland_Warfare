package org.luckyraven.gangland.civilians.npc.combat;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
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

	public NpcRangedAttack create(LivingEntity shooter, @Nullable String weaponName, NpcDifficulty difficulty) {
		if (weaponName == null) return NpcRangedAttack.NONE;

		RegisteredServiceProvider<BartizanApi> rsp = Bukkit.getServicesManager().getRegistration(BartizanApi.class);
		if (rsp == null) return NpcRangedAttack.NONE;

		return rsp.getProvider()
		          .npcWeapons()
		          .create(shooter, weaponName, difficulty.getFireRateMultiplier(), difficulty.getAimError());
	}

}
