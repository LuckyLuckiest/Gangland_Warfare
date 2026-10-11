package org.luckyraven.gangland.fights.listener;

import com.cryptomorin.xseries.XPotion;
import org.bukkit.EntityEffect;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.fights.FightRules;
import org.luckyraven.gangland.fights.arena.ArenaRegistry;
import org.luckyraven.gangland.fights.fight.Fight;
import org.luckyraven.gangland.fights.fight.FightManager;
import org.luckyraven.gangland.fights.fight.FightState;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.ArrayList;

/**
 * The central guard of a fight (GP-31, GP-41): a hit that would kill a fighter is cancelled and turns into his
 * elimination, so nothing ever dies.
 *
 * <p>Ordering is the whole point. This runs at NORMAL on the base {@link EntityDamageEvent}, below the impl
 * {@code EntityDamageListener} (HIGH) and {@code CustomPlayerDeathListener} (HIGHEST), which both ignore cancelled
 * events: neither ever sees the lethal hit, so no kill credit, wanted level, bounty, money drop, downed state or
 * hospital respawn follows. Because the victim's health never reaches zero, no {@code EntityDeathEvent} fires and the
 * MONITOR {@code MoneyDropListener} is never reached for a fight death. Covering the base event (not only
 * {@code ByEntity}) catches fall, fire, void, poison, explosions and Bartizan's custom causes.
 */
@ListenerHandler
public final class FightDamageListener implements Listener {

	private final FightManager  manager;
	private final ArenaRegistry arenas;
	private final FightRules    rules;

	public FightDamageListener(FightManager manager, ArenaRegistry arenas, FightRules rules) {
		this.manager = manager;
		this.arenas  = arenas;
		this.rules   = rules;
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onDamage(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Player victim)) return;

		Player attacker      = attacker(event);
		Fight  attackerFight = attacker == null ? null : Fighters.active(manager, attacker.getUniqueId());
		Fight  victimFight   = Fighters.active(manager, victim.getUniqueId());

		if (victimFight == null) {
			// a fighter may only hurt his opponents; a bystander inside a busy arena is untouchable
			if (attackerFight != null || Fighters.busyArenaAt(manager, arenas, victim.getLocation()) != null) {
				event.setCancelled(true);
			}
			return;
		}

		FightState state = victimFight.state();
		if (state == FightState.COUNTDOWN || state == FightState.RESULT) {
			event.setCancelled(true);
			return;
		}
		if (attacker != null && attackerFight != victimFight) {
			event.setCancelled(true);
			return;
		}
		if (victim.getHealth() - event.getFinalDamage() > 0) return;

		event.setCancelled(true);
		// the impl kill-credit listener must never see the lethal hit, so the totem is popped here, not by vanilla
		if (rules.isRespectTotems() && popTotem(victim)) return;
		victim.setFireTicks(0);
		victim.setFallDistance(0F);
		for (PotionEffect effect : new ArrayList<>(victim.getActivePotionEffects())) {
			victim.removePotionEffect(effect.getType());
		}
		manager.forfeit(victim.getUniqueId(), "death");
	}

	/** The player behind the hit: the damager itself, or the shooter of a projectile. */
	private static @Nullable Player attacker(EntityDamageEvent event) {
		if (!(event instanceof EntityDamageByEntityEvent byEntity)) return null;
		if (byEntity.getDamager() instanceof Player player) return player;
		if (byEntity.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
			return shooter;
		}
		return null;
	}

	/** Vanilla totem save done by hand: consumes one totem, 1 health, fire out, fresh effects. False when none held. */
	private static boolean popTotem(Player player) {
		PlayerInventory inventory = player.getInventory();
		ItemStack       main      = inventory.getItemInMainHand();
		boolean         inMain    = main != null && main.getType() == Material.TOTEM_OF_UNDYING;
		ItemStack       held      = inMain ? main : inventory.getItemInOffHand();
		if (held == null || held.getType() != Material.TOTEM_OF_UNDYING) return false;

		ItemStack left = held.getAmount() > 1 ? held.clone() : null;
		if (left != null) left.setAmount(held.getAmount() - 1);
		if (inMain) inventory.setItemInMainHand(left);
		else inventory.setItemInOffHand(left);

		player.setHealth(1.0D);
		player.setFireTicks(0);
		for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
			player.removePotionEffect(effect.getType());
		}
		addEffect(player, XPotion.REGENERATION, 900, 1);
		addEffect(player, XPotion.ABSORPTION, 100, 1);
		addEffect(player, XPotion.FIRE_RESISTANCE, 800, 0);
		player.playEffect(EntityEffect.TOTEM_RESURRECT);
		return true;
	}

	private static void addEffect(Player player, XPotion potion, int ticks, int amplifier) {
		PotionEffectType type = potion.getPotionEffectType();
		if (type != null) player.addPotionEffect(new PotionEffect(type, ticks, amplifier));
	}
}
