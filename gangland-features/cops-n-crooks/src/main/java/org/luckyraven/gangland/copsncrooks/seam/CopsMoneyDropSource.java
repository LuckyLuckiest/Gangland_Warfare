package org.luckyraven.gangland.copsncrooks.seam;

import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.civilian.CivilianNpcRegistry;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.data.economy.NpcMoneyDropSource;
import org.luckyraven.gangland.item.money.MoneyDropContext;

/**
 * Seam 1 delegate: recognises cop and civilian NPCs for cash-drop classification. Installed into the core
 * {@code GanglandMoneyDropClassifier} holder by {@code CopsNCrooksModuleConfig.installCoreSeams()}. See
 * documentation/module-loader.md, "Core seams".
 */
public final class CopsMoneyDropSource implements NpcMoneyDropSource {

	private final CopManager           copManager;
	private final CivilianNpcRegistry  civilianNpcRegistry;

	public CopsMoneyDropSource(CopManager copManager, CivilianNpcRegistry civilianNpcRegistry) {
		this.copManager          = copManager;
		this.civilianNpcRegistry = civilianNpcRegistry;
	}

	@Override
	@Nullable
	public MoneyDropContext classify(LivingEntity entity) {
		if (copManager != null && copManager.isCopNpc(entity)) return MoneyDropContext.COP;
		if (civilianNpcRegistry != null && civilianNpcRegistry.getNpc(entity.getUniqueId()) != null) {
			return MoneyDropContext.CIVILIAN;
		}
		return null;
	}
}
