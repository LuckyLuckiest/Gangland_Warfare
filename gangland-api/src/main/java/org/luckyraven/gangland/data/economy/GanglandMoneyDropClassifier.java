package org.luckyraven.gangland.data.economy;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.item.money.MoneyDropClassifier;
import org.luckyraven.gangland.item.money.MoneyDropContext;

/**
 * Classifier implementation that recognises players and, once a module installs one, cops-n-crooks NPCs too. Lives
 * in gangland-impl so the listener (which is in gangland-item) doesn't have to import a feature module type, and
 * always registers as a bean so {@code MoneyDropListener} constructs whether or not a module is installed.
 */
public class GanglandMoneyDropClassifier implements MoneyDropClassifier {

	private volatile NpcMoneyDropSource npcSource;

	public void install(NpcMoneyDropSource source) {
		this.npcSource = source;
	}

	@Override
	public MoneyDropContext classify(LivingEntity entity) {
		if (entity instanceof Player) return MoneyDropContext.PLAYER;

		NpcMoneyDropSource source = this.npcSource;
		if (source != null) {
			MoneyDropContext context = source.classify(entity);
			if (context != null) return context;
		}
		return MoneyDropContext.MOB;
	}

}
