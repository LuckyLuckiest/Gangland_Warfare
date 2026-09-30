package org.luckyraven.gangland.copsncrooks.seam;

import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.civilians.npc.CivilianNpcRegistry;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.data.economy.NpcMoneyDropSource;
import org.luckyraven.gangland.item.money.MoneyDropContext;

/**
 * Seam 1 delegate: recognises cop and civilian NPCs for cash-drop classification. Runs at MONITOR on a dying body,
 * so cops are matched with the dying-safe {@link CopManager#findDyingCop} (never {@code isCopNpc}, which needs a
 * valid entity). Installed into the core {@code GanglandMoneyDropClassifier} holder by
 * {@code CopsNCrooksModuleConfig.installCoreSeams()}. See documentation/module-loader.md, "Core seams".
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
		if (copManager != null && copManager.findDyingCop(entity) != null) return MoneyDropContext.COP;
		if (civilianNpcRegistry != null && civilianNpcRegistry.getNpc(entity.getUniqueId()) != null) {
			return MoneyDropContext.CIVILIAN;
		}
		return null;
	}
}
