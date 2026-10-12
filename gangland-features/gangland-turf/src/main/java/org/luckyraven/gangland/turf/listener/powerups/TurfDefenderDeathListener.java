package org.luckyraven.gangland.turf.listener.powerups;

import lombok.CustomLog;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.civilians.events.CivilianDeathEvent;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.item.money.MoneyAddon;
import org.luckyraven.gangland.item.money.MoneyDepositService;
import org.luckyraven.gangland.item.money.MoneyItem;
import org.luckyraven.gangland.item.money.MoneyItemFactory;
import org.luckyraven.gangland.turf.npc.defender.TurfDefenderDeployer;
import org.luckyraven.gangland.turf.npc.defender.TurfDefenderDeployer.GuardBounty;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.function.ObjIntConsumer;

/**
 * Tells the deployer which guards died (T-251), and pays the guard bounty (T-700): a player who is neither the owning
 * gang nor allied to it and kills a guard finds cash worth {@code Garrison.Bounty_Share} of the guard's price at its
 * feet. The owner paid that price when it bought the guard, so nothing is minted; owners and allies get nothing, and
 * the guard drops no civilian cash (see {@code TurfDefenderMoneyDropSource}).
 */
@CustomLog
@ListenerHandler
public final class TurfDefenderDeathListener implements Listener {

	private final TurfDefenderDeployer defenders;
	private final GangMembership       gangs;

	private ObjIntConsumer<Location> dropper;

	public TurfDefenderDeathListener(TurfDefenderDeployer defenders, GangMembership gangs, MoneyAddon money,
	                                 MoneyDepositService deposits) {
		this.defenders = defenders;
		this.gangs     = gangs;
		this.dropper   = (at, amount) -> dropCash(money, deposits, at, amount);
	}

	/** Test seam: replaces the physical cash drop. */
	void setDropper(ObjIntConsumer<Location> dropper) {
		this.dropper = dropper;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onCivilianDeath(CivilianDeathEvent event) {
		CivilianNpc npc = event.getCivilianNpc();
		defenders.markKilled(npc);

		Player killer = event.getKiller();
		if (killer == null) return;
		GuardBounty bounty = defenders.bountyOf(npc);
		if (bounty == null || bounty.ownerGangId() < 0 || bounty.amount() <= 0) return;

		int killerGang = gangs.gangIdOf(killer.getUniqueId());
		if (killerGang == bounty.ownerGangId() || gangs.gangsAllied(killerGang, bounty.ownerGangId())) return;

		LivingEntity body = npc.getEntity();
		Location     at   = body != null ? body.getLocation() : killer.getLocation();
		dropper.accept(at, bounty.amount());
	}

	private static void dropCash(MoneyAddon money, MoneyDepositService deposits, Location at, int amount) {
		World world = at.getWorld();
		if (world == null) return;
		MoneyItem variation = money.getDefaultVariation();
		if (variation == null) {
			log.warn("Guard bounty of {} not paid: money.yml has no default variation", amount);
			return;
		}
		ItemStack item = MoneyItemFactory.build(variation, amount, deposits);
		world.dropItemNaturally(at, item);
	}
}
