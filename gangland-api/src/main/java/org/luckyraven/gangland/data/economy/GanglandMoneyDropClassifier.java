package org.luckyraven.gangland.data.economy;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.item.money.MoneyDropClassifier;
import org.luckyraven.gangland.item.money.MoneyDropContext;
import org.luckyraven.keystone.npc.NpcSupport;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Classifier implementation that recognises players and, once a module installs one, cops-n-crooks NPCs too. Lives
 * in gangland-impl so the listener (which is in gangland-item) doesn't have to import a feature module type, and
 * always registers as a bean so {@code MoneyDropListener} constructs whether or not a module is installed.
 */
public class GanglandMoneyDropClassifier implements MoneyDropClassifier {

	private volatile NpcMoneyDropSource npcSource;
	private final List<NpcMoneyDropSource> firstSources = new CopyOnWriteArrayList<>();

	public void install(NpcMoneyDropSource source) {
		this.npcSource = source;
	}

	/**
	 * Adds a source consulted, in registration order, before the {@link #install} slot; the first non-null answer wins.
	 * Lets a second module (turf guards) claim its NPCs without replacing cops' slot.
	 *
	 * @since 2.6
	 */
	public void installFirst(NpcMoneyDropSource source) {
		this.firstSources.add(source);
	}

	/**
	 * The module source goes first: a Citizens PLAYER-type NPC (every cop, some civilians) is a {@link Player}.
	 * PLAYER is kept for real players, and an NPC nobody recognises is {@link MoneyDropContext#NPC}.
	 */
	@Override
	public MoneyDropContext classify(LivingEntity entity) {
		for (NpcMoneyDropSource first : firstSources) {
			MoneyDropContext context = first.classify(entity);
			if (context != null) return context;
		}
		NpcMoneyDropSource source = this.npcSource;
		if (source != null) {
			MoneyDropContext context = source.classify(entity);
			if (context != null) return context;
		}
		if (NpcSupport.isNpc(entity)) return MoneyDropContext.NPC;
		return entity instanceof Player ? MoneyDropContext.PLAYER : MoneyDropContext.MOB;
	}

}
