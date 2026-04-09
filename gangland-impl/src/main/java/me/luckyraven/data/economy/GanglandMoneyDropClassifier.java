package me.luckyraven.data.economy;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.civilian.CivilianService;
import me.luckyraven.copsncrooks.npc.police.CopService;
import me.luckyraven.item.money.MoneyDropClassifier;
import me.luckyraven.item.money.MoneyDropContext;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Classifier implementation that knows how to recognise cops-n-crooks NPCs. Lives in gangland-impl so the listener
 * (which is in gangland-item) doesn't have to import {@code CopService}/{@code CivilianService}.
 */
@RequiredArgsConstructor
public class GanglandMoneyDropClassifier implements MoneyDropClassifier {

	private final CopService      copService;
	private final CivilianService civilianService;

	@Override
	public MoneyDropContext classify(LivingEntity entity) {
		if (entity instanceof Player) return MoneyDropContext.PLAYER;

		if (copService != null && copService.getCopManager() != null
		    && copService.getCopManager().isCopNpc(entity)) {
			return MoneyDropContext.COP;
		}

		if (civilianService != null && civilianService.getNpc(entity.getUniqueId()) != null) {
			return MoneyDropContext.CIVILIAN;
		}

		return MoneyDropContext.MOB;
	}

}
