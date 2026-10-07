package org.luckyraven.gangland.listener.player;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.gangland.core.downed.DownedPlayerRegistry;
import org.luckyraven.gangland.core.feature.Executor;
import org.luckyraven.keystone.timer.Timer;
import org.luckyraven.keystone.util.ChatUtil;
import org.luckyraven.keystone.util.ParticleUtil;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.core.bounty.Bounty;
import org.luckyraven.gangland.core.bounty.BountyExecutor;
import org.luckyraven.gangland.core.bounty.BountySettings;
import org.luckyraven.gangland.core.events.bounty.BountyEvent;
import org.luckyraven.gangland.core.events.user.UserBountyEvent;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.core.wanted.WantedKillTrackers;
import org.luckyraven.gangland.core.wanted.WantedSettings;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.gangland.data.gang.GangMembership;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

@ListenerHandler
public class EntityDamageListener implements Listener {

	private final Gangland            gangland;
	private final UserManager<Player> userManager;
	private final WantedKillTrackers  wantedKills;
	private final BountySettings      bountySettings;
	private final WantedStars         wantedStars;
	private final GangMembership      gangs;

	// ponytail: provocation memory and the crime-free takedown cap are fixed code constants (no keys); make them
	// settings only if an owner asks to tune them
	private static final long PROVOCATION_MS     = 60_000L;
	private static final long TAKEDOWN_HOUR_MS   = 3_600_000L;
	private static final int  TAKEDOWNS_PER_HOUR = 3;

	// ponytail: first hit wins inside Self_Defence.Window_Seconds (read per call); stale pairs are pruned on every
	// recorded hit and a quit ends the player's fights; the damage, provocation and cooldown maps are in memory only (a restart forgets them)
	private final Map<String, Long>     firstHit     = new HashMap<>();   // value = hit order, so same-millisecond hits still sort
	private long                        hitOrder;
	private final Map<String, Long>     lastExchange = new HashMap<>();
	private final Map<String, Double>   damage       = new HashMap<>();   // "attacker>victim" -> damage dealt this fight
	private final Map<String, Long>     lastHit      = new HashMap<>();   // "attacker>victim" -> last hit time, kept 60 s
	private final Map<String, Boolean>  provoked     = new HashMap<>();   // "attacker>victim" first strike followed a hit by the victim
	private final Map<String, Long>     cooldowns    = new HashMap<>();   // sorted pair -> exemption cooldown end
	private final Map<UUID, List<Long>> takedowns    = new HashMap<>();   // killer -> crime-free takedown times

	/** Test seam for the fight window. */
	LongSupplier clock = System::currentTimeMillis;

	public EntityDamageListener(Gangland gangland,
	                            @Qualifier("online") UserManager<Player> userManager,
	                            WantedKillTrackers wantedKills,
	                            BountySettings bountySettings,
	                            WantedSettings wantedSettings,
	                            WantedStars wantedStars,
	                            GangMembership gangs) {
		this.gangland          = gangland;
		this.userManager       = userManager;
		this.wantedKills       = wantedKills;
		this.bountySettings    = bountySettings;
		this.wantedStars       = wantedStars;
		this.gangs             = gangs;
		setupKillComboCallbacks();
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPlayerEntityDeath(EntityDamageByEntityEvent event) {
		Player damager;
		if (event.getDamager() instanceof Player player) damager = player;
		else if (event.getDamager() instanceof Projectile projectile) {
			if (projectile.getShooter() instanceof Player player) damager = player;
			else return;
		} else return;

		// make blood depending on the damage
		Entity entity = event.getEntity();

		createBloodParticle(entity, event.getDamage());

		// who struck first decides self-defence; recorded for every hit, before the death check
		if (entity instanceof Player struck && struck != damager && userManager.getUser(struck) != null &&
		    userManager.getUser(damager) != null) {
			recordHit(damager.getUniqueId(), struck.getUniqueId(), event.getFinalDamage());
		}

		// register when the entity dies
		boolean isEntityDead = !(entity instanceof LivingEntity livingEntity &&
		                         livingEntity.getHealth() <= event.getFinalDamage());
		if (isEntityDead) return;

		// Downed players (GTA-style) have health=0.5 and absorb all further damage — skip kill credit.
		if (entity instanceof Player target && DownedPlayerRegistry.isDowned(target.getUniqueId())) return;

		// the current damager
		User<Player> damagerUser = userManager.getUser(damager);
		if (damagerUser == null) return;

		// the entity killed is not a player
		boolean checkEntityType = handleMobKills(entity, damagerUser);

		if (!checkEntityType) {
			return;
		}

		// check if it was a player or a mob
		Player deadPlayer = (Player) entity;

		handlePlayerKills(deadPlayer, damagerUser);
	}

	private void handlePlayerKills(Player deadPlayer, User<Player> damagerUser) {
		// killing yourself (your own arrow) is no kill: no claim on your own head, no crime
		if (deadPlayer.getUniqueId().equals(damagerUser.getUser().getUniqueId())) return;

		User<Player> deadUser = userManager.getUser(deadPlayer);

		// If the "player" isn't a real User, treat it as an NPC death (e.g., cop NPC),
		// but still apply wanted/bounty logic to the damager when appropriate.
		if (deadUser == null) {
			damagerUser.setMobKills(damagerUser.getMobKills() + 1);

			// Only increase wanted if this NPC counts towards wanted (cops should, civilians may, etc.)
			if (wantedKills.countsForWanted(deadPlayer)) {
				if (wantedKills.routesKills()) {
					wantedKills.recordKill(damagerUser.getUser(), damagerUser.getWanted(), deadPlayer);
				} else {
					handleWanted(damagerUser);
				}
			}
			return;
		}

		// Real player kill
		damagerUser.setKills(damagerUser.getKills() + 1);

		boolean defence = selfDefence(damagerUser.getUser().getUniqueId(), deadPlayer.getUniqueId());
		forgetFight(damagerUser.getUser().getUniqueId(), deadPlayer.getUniqueId());

		// what the victim's head is worth: players' escrow always, the server-made notoriety only when configured
		Bounty     bounty   = deadUser.getBounty();
		boolean    payAll   = Settings.isBountyPayNotoriety();
		BigDecimal posted   = bounty.getPostedAmount();
		// the killer's own escrow comes back to him, but only other players' money makes the kill a takedown
		BigDecimal byOthers = bounty.getPostedAmountExcluding(Bounty.posterId(damagerUser.getUser()));
		BigDecimal payout   = payAll ? bounty.getAmount() : posted;
		boolean    allied   = gangs.alliedOrSame(damagerUser.getUser().getUniqueId(), deadPlayer.getUniqueId());
		boolean    paid     = !allied && payout.signum() > 0;

		if (paid) {
			damagerUser.getEconomy().depositAmount(payout);

			if (payAll) bounty.resetBounty();
			else bounty.claimPosted();

			String message = Messages.BOUNTY_CLAIMED.toString();
			String replace = message.replace("%amount%", Settings.formatAmount(payout))
			                        .replace("%player%", deadPlayer.getName());

			damagerUser.sendMessage(replace);

			// Reset kill combo if player was killed by someone with bounty
			if (wantedKills.isActive()) {
				wantedKills.resetCombo(deadPlayer.getUniqueId());
			}

			// a takedown of a bounty other players posted is not a crime
			// WB-48: dust bounties, a repeated pair and a fourth takedown in the hour still pay but are crimes
			if (takedownExempt(damagerUser.getUser().getUniqueId(), deadPlayer.getUniqueId(), byOthers)) return;
		}

		// defending yourself is not a crime, with or without the cop module; nor is defending your own turf (TF-49)
		if (defence) {
			stampCooldown(damagerUser.getUser().getUniqueId(), deadPlayer.getUniqueId());
			return;
		}

		if (wantedKills.exemptsKill(damagerUser.getUser(), deadPlayer)) return;

		if (!paid) handleBounty(damagerUser);

		// increase the wanted level for killing another player
		if (wantedKills.routesKills()) {
			wantedKills.recordKill(damagerUser.getUser(), damagerUser.getWanted(), deadPlayer);
		} else handleWanted(damagerUser);
	}

	private static String pair(UUID a, UUID b) {
		return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
	}

	private void forgetFight(UUID a, UUID b) {
		for (String key : new String[]{a + ">" + b, b + ">" + a}) {
			firstHit.remove(key);
			damage.remove(key);
			provoked.remove(key);
		}
		lastExchange.remove(pair(a, b));
	}

	/** Player-vs-player fights currently remembered (test seam). */
	int trackedFights() {
		return lastExchange.size();
	}

	/** Entries across the provocation, damage, cooldown and takedown memories (test seam). */
	int trackedMemories() {
		return damage.size() + lastHit.size() + provoked.size() + cooldowns.size() + takedowns.size();
	}

	/**
	 * A quit ends the fight (first strike, damage, the first strike's provocation flag), never the anti-abuse memory:
	 * last-hit stamps, pair cooldowns and the takedown tally survive a relog and expire by time in {@link #pruneStale}.
	 */
	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		String id = event.getPlayer().getUniqueId().toString();

		lastExchange.keySet().removeIf(key -> key.contains(id));
		firstHit.keySet().removeIf(key -> key.contains(id));
		damage.keySet().removeIf(key -> key.contains(id));
		provoked.keySet().removeIf(key -> key.contains(id));
	}

	private static long windowMs() {
		return Settings.getSelfDefenceWindowSeconds() * 1000L;
	}

	private void pruneStale(long now) {
		long window = windowMs();

		lastExchange.entrySet().removeIf(entry -> {
			if (now - entry.getValue() <= window) return false;

			String[] ids = entry.getKey().split("\\|");
			for (String key : new String[]{ids[0] + ">" + ids[1], ids[1] + ">" + ids[0]}) {
				firstHit.remove(key);
				damage.remove(key);
				provoked.remove(key);
			}
			return true;
		});
		lastHit.values().removeIf(time -> now - time > PROVOCATION_MS);
		cooldowns.values().removeIf(end -> now >= end);
		takedowns.values().removeIf(times -> {
			times.removeIf(time -> now - time >= TAKEDOWN_HOUR_MS);
			return times.isEmpty();
		});
	}

	private void recordHit(UUID attacker, UUID victim, double dealt) {
		long now = clock.getAsLong();

		pruneStale(now);
		Long last = lastExchange.get(pair(attacker, victim));

		if (last != null && now - last > windowMs()) forgetFight(attacker, victim);

		String key = attacker + ">" + victim;

		if (firstHit.putIfAbsent(key, ++hitOrder) == null) {
			// the first strike of this fight: did the victim hit the attacker in the minute before it?
			Long theirs = lastHit.get(victim + ">" + attacker);
			provoked.put(key, theirs != null && now - theirs <= PROVOCATION_MS);
		}
		damage.merge(key, dealt, Double::sum);
		lastHit.put(key, now);
		lastExchange.put(pair(attacker, victim), now);
	}

	private boolean selfDefence(UUID killer, UUID victim) {
		if (!Settings.isSelfDefenceEnabled()) return false;

		long now  = clock.getAsLong();
		Long last = lastExchange.get(pair(killer, victim));

		if (last != null && now - last > windowMs()) forgetFight(killer, victim);

		Long victimFirst = firstHit.get(victim + ">" + killer);
		Long killerFirst = firstHit.get(killer + ">" + victim);

		if (victimFirst == null || (killerFirst != null && victimFirst > killerFirst)) return false;
		if (gangs.alliedOrSame(killer, victim)) return false;
		if (damage.getOrDefault(victim + ">" + killer, 0.0) < Settings.getSelfDefenceMinDamage()) return false;
		if (provoked.getOrDefault(victim + ">" + killer, false)) return false;

		return !onCooldown(killer, victim, now);
	}

	private boolean onCooldown(UUID a, UUID b, long now) {
		Long end = cooldowns.get(pair(a, b));

		return end != null && now < end;
	}

	private void stampCooldown(UUID a, UUID b) {
		cooldowns.put(pair(a, b), clock.getAsLong() + Settings.getSelfDefencePairCooldownSeconds() * 1000L);
	}

	/** True (and stamps the pair cooldown and the killer's tally) when this takedown is crime-free. */
	private boolean takedownExempt(UUID killer, UUID victim, BigDecimal byOthers) {
		long now = clock.getAsLong();

		if (byOthers.compareTo(Settings.getBountyMinimum()) <= 0
		    || byOthers.compareTo(Settings.getBountyTakedownMinimum()) < 0
		    || onCooldown(killer, victim, now)) return false;

		List<Long> times = takedowns.computeIfAbsent(killer, id -> new ArrayList<>());

		times.removeIf(time -> now - time >= TAKEDOWN_HOUR_MS);

		if (times.size() >= TAKEDOWNS_PER_HOUR) return false;

		times.add(now);
		stampCooldown(killer, victim);
		return true;
	}

	private boolean handleMobKills(Entity victim, User<Player> attacker) {
		if (victim instanceof Player) return true;

		attacker.setMobKills(attacker.getMobKills() + 1);

		// check if the entity is a civilian and increase the wanted level
		if (!wantedKills.countsForWanted(victim)) return false;

		// Record kill in combo system if enabled
		if (wantedKills.routesKills()) {
			wantedKills.recordKill(attacker.getUser(), attacker.getWanted(), victim);
		} else handleWanted(attacker);

		return false;
	}

	private void createBloodParticle(Entity entity, double damage) {
		ParticleUtil.createBloodSplash(entity, damage);
	}

	private void setupKillComboCallbacks() {
		wantedKills.onWantedTrigger(this::onKillComboWantedTrigger);
		wantedKills.onComboReset(this::onKillComboReset);
		wantedKills.onVictimDeath(this::onPlayerDeathResetWanted);
	}

	private void onKillComboWantedTrigger(Player player) {
		User<Player> damagerUser = userManager.getUser(player);

		if (damagerUser == null) return;

		// Apply wanted level increase based on kill combo
		handleWanted(damagerUser);
	}

	private void onKillComboReset(Player player) {
		User<Player> user    = userManager.getUser(player);
		String       message = ChatUtil.color("&e&lKill combo reset!");

		if (user == null) return;

		user.sendMessage(message);
	}

	private void onPlayerDeathResetWanted(UUID deadPlayerId) {
		Player deadPlayer = Bukkit.getPlayer(deadPlayerId);

		// The player may have left between the death and this callback
		if (deadPlayer == null) return;

		User<Player> deadUser = userManager.getUser(deadPlayer);

		if (deadUser == null) return;

		// Reset the wanted level no matter how the player died
		deadUser.getWanted().reset(WantedCause.DEATH);
	}

	private void handleWanted(User<Player> damagerUser) {
		Wanted wanted = damagerUser.getWanted();

		// Raise the level and (re)start the decay clock; no star landed (maximum, cancelled) = no auto bounty
		if (wantedStars.raise(damagerUser, wanted.getIncrements(), WantedCause.CRIME) == 0) return;

		// Update bounty based on new wanted level
		int wantedLevel = wanted.getLevel();
		int userLevel   = damagerUser.getLevel().getLevelValue();

		Bounty     bounty     = damagerUser.getBounty();
		BigDecimal autoBounty = bounty.getAutoBountyIncrease(userLevel, wantedLevel);

		bounty.addNotoriety(autoBounty);

		startBountyTimer(damagerUser);

		// Notify player with kill combo information
		String format = String.format("&c&lWANTED LEVEL: &c%s &7(Bounty: &b+%s%s)", wanted.getLevelStars(),
		                              Settings.getMoneySymbol(), Settings.formatAmount(autoBounty));
		String message = ChatUtil.color(format);

		if (wantedStars.isStarChat()) damagerUser.sendMessage(message);
	}

	/** Starts the notoriety timer while it is on and below its cap; sync, because it fires events. */
	private void startBountyTimer(User<Player> damagerUser) {
		Bounty bounty = damagerUser.getBounty();

		if (!Settings.isBountyTimerEnabled()
		    || bounty.getNotoriety().compareTo(BigDecimal.valueOf(Settings.getBountyTimerMax())) >= 0) return;

		Executor executor = new BountyExecutor(gangland, new UserBountyEvent(false, damagerUser), damagerUser,
		                                       bountySettings);

		executor.createTimer().start(false);
	}

	private void handleBounty(User<Player> damagerUser) {
		Bounty     userBounty = damagerUser.getBounty();
		BigDecimal scaled     = userBounty.calculateLevelScaledBounty(Settings.getBountyEachKillValue(),
		                                                              damagerUser.getLevel().getLevelValue());

		if (userBounty.getNotoriety().add(scaled).compareTo(Settings.getBountyMaxKill()) > 0) return;

		BountyEvent bountyEvent = new UserBountyEvent(false, damagerUser, scaled);

		Bukkit.getPluginManager().callEvent(bountyEvent);

		if (bountyEvent.isCancelled()) return;

		userBounty.addNotoriety(scaled);

		startBountyTimer(damagerUser);
	}

}
