package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.Initializer;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.feature.Executor;
import me.luckyraven.feature.bounty.Bounty;
import me.luckyraven.feature.bounty.BountyEvent;
import me.luckyraven.feature.bounty.BountyExecutor;
import me.luckyraven.feature.combo.KillCombo;
import me.luckyraven.feature.combo.KillComboEvent;
import me.luckyraven.feature.entity.EntityMarkManager;
import me.luckyraven.feature.wanted.Wanted;
import me.luckyraven.feature.wanted.WantedEvent;
import me.luckyraven.feature.wanted.WantedExecutor;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.listener.ListenerHandler;
import me.luckyraven.util.timer.Timer;
import me.luckyraven.util.utilities.ParticleUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

@ListenerHandler
public class EntityDamage implements Listener {

	private final Gangland            gangland;
	private final UserManager<Player> userManager;
	private final EntityMarkManager   entityMarkManager;
	private final KillCombo           killCombo;

	public EntityDamage(Gangland gangland) {
		this.gangland = gangland;

		Initializer initializer = gangland.getInitializer();

		this.userManager       = initializer.getUserManager();
		this.entityMarkManager = initializer.getEntityMarkManager();

		this.killCombo = new KillCombo(gangland, SettingAddon.getWantedKillCounter());
		setupKillComboCallbacks();
	}

	@EventHandler(priority = EventPriority.HIGH)
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

		// register when the entity dies
		boolean isEntityDead = !(entity instanceof LivingEntity livingEntity &&
								 livingEntity.getHealth() <= event.getFinalDamage());
		if (isEntityDead) return;

		// the current damager
		User<Player> damagerUser = userManager.getUser(damager);

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
		User<Player> deadUser = userManager.getUser(deadPlayer);

		damagerUser.setKills(damagerUser.getKills() + 1);

		// when does the attacked user have a bounty
		Bounty bounty = deadUser.getBounty();

		if (bounty.hasBounty()) {
			double amount = bounty.getAmount();

			damagerUser.getEconomy().deposit(amount);
			bounty.resetBounty();

			String message = MessageAddon.BANK_MONEY_DEPOSIT_PLAYER.toString();
			String replace = message.replace("%amount%", SettingAddon.formatDouble(amount));

			damagerUser.sendMessage(replace);

			// reset the wanted level of the dead player
			deadUser.getWanted().reset();

			// Reset kill combo if player was killed by someone with bounty
			if (SettingAddon.isWantedKillComboEnabled()) {
				killCombo.resetCombo(deadPlayer.getUniqueId());
			}
		} else handleBounty(damagerUser);

		// increase the wanted level for killing another player
		if (SettingAddon.isWantedKillComboEnabled()) killCombo.recordKill(damagerUser, deadPlayer);
		else handleWanted(damagerUser);
	}

	private boolean handleMobKills(Entity victim, User<Player> attacker) {
		if (victim instanceof Player) return true;

		attacker.setMobKills(attacker.getMobKills() + 1);

		// check if the entity is a civilian and increase the wanted level
		if (!entityMarkManager.countsForWanted(victim)) return false;

		// Record kill in combo system if enabled
		if (SettingAddon.isWantedKillComboEnabled()) killCombo.recordKill(attacker, victim);
		else handleWanted(attacker);

		return false;
	}

	private void createBloodParticle(Entity entity, double damage) {
		ParticleUtil.createBloodSplash(entity, damage);
	}

	private void setupKillComboCallbacks() {
		// Callback when wanted level should be triggered
		killCombo.setOnWantedLevelTrigger(this::onKillComboWantedTrigger);

		// Callback when combo resets
		killCombo.setOnComboReset(this::onKillComboReset);
	}

	private void onKillComboWantedTrigger(KillComboEvent event) {
		Player       player      = event.getPlayer();
		User<Player> damagerUser = userManager.getUser(player);

		// Apply wanted level increase based on kill combo
		handleWanted(damagerUser);
	}

	private void onKillComboReset(KillComboEvent event) {
		Player       player  = event.getPlayer();
		User<Player> user    = userManager.getUser(player);
		String       message = ChatUtil.color("&e&lKill combo reset!");

		user.sendMessage(message);
	}

	private void handleWanted(User<Player> damagerUser) {
		Wanted      wanted      = damagerUser.getWanted();
		WantedEvent wantedEvent = new WantedEvent(true, wanted);

		wantedEvent.setWantedUser(damagerUser);

		// Increment wanted level
		wanted.incrementLevel();

		// Start wanted timer if enabled
		if (SettingAddon.isWantedTimerEnabled() && wanted.isWanted()) {
			Executor executor = new WantedExecutor(gangland, wantedEvent, damagerUser);
			Timer    timer    = executor.createTimer();

			timer.start(true);
		}

		// Update bounty based on new wanted level
		int wantedLevel = wanted.getLevel();
		int userLevel   = damagerUser.getLevel().getLevelValue();

		Bounty bounty     = damagerUser.getBounty();
		double autoBounty = bounty.getAutoBountyIncrease(userLevel, wantedLevel);

		bounty.setAmount(bounty.getAmount() + autoBounty);

		// Start bounty timer if enabled
		BountyEvent bountyEvent = new BountyEvent(true, bounty);
		if (SettingAddon.isBountyTimerEnabled() && bounty.getAmount() < SettingAddon.getBountyTimerMax()) {
			Executor executor = new BountyExecutor(gangland, bountyEvent, damagerUser);
			Timer    timer    = executor.createTimer();

			timer.start(true);
		}

		// Notify player with kill combo information
		String format = String.format("&c&lWANTED LEVEL: &c%s &7(Bounty: &b+%s%.2f)", wanted.getLevelStars(),
									  SettingAddon.getMoneySymbol(), autoBounty);
		String message = ChatUtil.color(format);

		damagerUser.sendMessage(message);
	}

	private void handleBounty(User<Player> damagerUser) {
		Bounty      userBounty  = damagerUser.getBounty();
		BountyEvent bountyEvent = new BountyEvent(true, userBounty);

		bountyEvent.setUserBounty(damagerUser);

		if (SettingAddon.isBountyTimerEnabled() && userBounty.getAmount() < SettingAddon.getBountyTimerMax()) {
			Executor executor = new BountyExecutor(gangland, bountyEvent, damagerUser);
			Timer    timer    = executor.createTimer();

			timer.start(true);

			return;
		}

		double eachKill     = SettingAddon.getBountyEachKillValue();
		double scaledBounty = userBounty.calculateLevelScaledBounty(eachKill, damagerUser.getLevel().getLevelValue());
		double amount       = eachKill + userBounty.getAmount();

		if (amount > SettingAddon.getBountyMaxKill()) return;

		bountyEvent.setAmountApplied(scaledBounty);

		Bukkit.getScheduler().runTaskAsynchronously(gangland, () -> {
			Bukkit.getPluginManager().callEvent(bountyEvent);
		});

		if (bountyEvent.isCancelled()) return;

		damagerUser.getBounty().setAmount(amount);
	}

}
