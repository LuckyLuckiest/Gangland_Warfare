package me.luckyraven.data.economy;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.civilian.CivilianNpcRegistry;
import me.luckyraven.copsncrooks.npc.police.CopManager;
import me.luckyraven.item.money.MoneyDropClassifier;
import me.luckyraven.item.money.MoneyDropContext;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Classifier implementation that knows how to recognise cops-n-crooks NPCs. Lives in gangland-impl so the listener
 * (which is in gangland-item) doesn't have to import {@code CopManager}/{@code CivilianNpcRegistry}.
 */
@RequiredArgsConstructor
public class GanglandMoneyDropClassifier implements MoneyDropClassifier {

	private final CopManager          copManager;
	private final CivilianNpcRegistry civilianNpcRegistry;

	@Override
	public MoneyDropContext classify(LivingEntity entity) {
		if (entity instanceof Player) return MoneyDropContext.PLAYER;

		if (copManager != null && copManager.isCopNpc(entity)) {
			return MoneyDropContext.COP;
		}

		if (civilianNpcRegistry != null && civilianNpcRegistry.getNpc(entity.getUniqueId()) != null) {
			return MoneyDropContext.CIVILIAN;
		}

		return MoneyDropContext.MOB;
	}

}
