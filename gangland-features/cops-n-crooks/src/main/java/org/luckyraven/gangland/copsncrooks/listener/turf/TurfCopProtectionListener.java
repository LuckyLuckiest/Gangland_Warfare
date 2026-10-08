package org.luckyraven.gangland.copsncrooks.listener.turf;

import lombok.RequiredArgsConstructor;
import org.bukkit.event.Listener;
import org.luckyraven.bartizan.api.event.WeaponRaytraceImpactEvent;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.turf.npc.guard.TurfCopGuard;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

// RED STUB (0.16.1 T-189): handlers present, behaviour intentionally absent.
@ListenerHandler
@RequiredArgsConstructor
public final class TurfCopProtectionListener implements Listener {

	private final CopManager       copManager;
	private final TurfCopGuard     guard;
	private final UserLookupContract users;

	public void onDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
	}

	public void onWeaponImpact(WeaponRaytraceImpactEvent event) {
	}
}
