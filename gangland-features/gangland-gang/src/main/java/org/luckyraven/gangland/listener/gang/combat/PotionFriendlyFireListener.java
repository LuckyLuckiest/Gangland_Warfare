package org.luckyraven.gangland.listener.gang.combat;

import com.cryptomorin.xseries.XPotion;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.luckyraven.gangland.gang.combat.FriendlyFirePolicy;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * GP-19: splash potions and lingering clouds do not hit protected friends. With {@code Friendly_Fire.Potions:
 * NEGATIVE_ONLY} (default) a potion that carries any harmful effect is stopped; healing and buff potions still reach
 * friends. {@code ALL} stops every potion. A mixed potion (harmful plus helpful) is stopped whole for that victim:
 * {@code PotionSplashEvent.setIntensity(victim, 0)} cannot strip one effect at a time.
 */
@ListenerHandler
public class PotionFriendlyFireListener implements Listener {

	private final FriendlyFirePolicy          policy;
	private final GangRules                   rules;
	private       Predicate<PotionEffectType> debuff = PotionFriendlyFireListener::isXSeriesDebuff;

	public PotionFriendlyFireListener(FriendlyFirePolicy policy, GangRules rules) {
		this.policy = policy;
		this.rules  = rules;
	}

	/** Test seam: which effect types count as harmful (XSeries cannot resolve effect types without a server). */
	void setDebuff(Predicate<PotionEffectType> debuff) {
		this.debuff = debuff;
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onSplash(PotionSplashEvent event) {
		ThrownPotion potion = event.getEntity();
		if (!(potion.getShooter() instanceof Player thrower)) return;

		List<PotionEffectType> types = new ArrayList<>();
		for (PotionEffect effect : potion.getEffects()) types.add(effect.getType());
		if (!stripped(types)) return;

		for (LivingEntity affected : new ArrayList<>(event.getAffectedEntities())) {
			if (protectedFrom(thrower, affected)) event.setIntensity(affected, 0D);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onCloud(AreaEffectCloudApplyEvent event) {
		AreaEffectCloud cloud = event.getEntity();
		if (!(cloud.getSource() instanceof Player thrower)) return;

		List<PotionEffectType> types = new ArrayList<>();
		for (PotionEffect effect : cloud.getCustomEffects()) types.add(effect.getType());
		PotionData base = cloud.getBasePotionData();
		if (base != null && base.getType().getEffectType() != null) types.add(base.getType().getEffectType());
		if (!stripped(types)) return;

		event.getAffectedEntities().removeIf(affected -> protectedFrom(thrower, affected));
	}

	/** Whether a potion with these effects is held back from protected friends. */
	private boolean stripped(Collection<PotionEffectType> types) {
		if ("ALL".equalsIgnoreCase(rules.getFriendlyFirePotions())) return true;

		for (PotionEffectType type : types) {
			if (type != null && debuff.test(type)) return true;
		}
		return false;
	}

	private boolean protectedFrom(Player thrower, LivingEntity affected) {
		return affected instanceof Player victim
		       && policy.pvpBlocked(thrower.getUniqueId(), victim.getUniqueId(), victim.getLocation());
	}

	private static boolean isXSeriesDebuff(PotionEffectType type) {
		for (XPotion potion : XPotion.DEBUFFS) {
			if (type.equals(potion.getPotionEffectType())) return true;
		}
		return false;
	}
}
