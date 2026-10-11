package org.luckyraven.gangland.listener.gang.combat;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.potion.PotionEffectType;
import org.luckyraven.gangland.gang.combat.FriendlyFirePolicy;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.function.Predicate;

/** RED stub. */
@ListenerHandler
public class PotionFriendlyFireListener implements Listener {

	public PotionFriendlyFireListener(FriendlyFirePolicy policy, GangRules rules) {
	}

	PotionFriendlyFireListener(FriendlyFirePolicy policy, GangRules rules, Predicate<PotionEffectType> debuff) {
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onSplash(PotionSplashEvent event) {
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onCloud(AreaEffectCloudApplyEvent event) {
	}
}
