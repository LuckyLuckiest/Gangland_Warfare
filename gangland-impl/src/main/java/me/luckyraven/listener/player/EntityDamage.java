package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.feature.Executor;
import me.luckyraven.feature.bounty.Bounty;
import me.luckyraven.feature.bounty.BountyEvent;
import me.luckyraven.feature.bounty.BountyExecutor;
import me.luckyraven.feature.entity.EntityMarkManager;
import me.luckyraven.feature.wanted.Wanted;
import me.luckyraven.feature.wanted.WantedEvent;
import me.luckyraven.feature.wanted.WantedExecutor;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.listener.ListenerHandler;
import me.luckyraven.util.timer.Timer;
import me.luckyraven.util.utilities.ParticleUtil;
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

	public EntityDamage(Gangland gangland) {
		this.gangland          = gangland;
		this.userManager       = gangland.getInitializer().getUserManager();
		this.entityMarkManager = gangland.getInitializer().getEntityMarkManager();
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
		if (!(entity instanceof Player player)) {
			damagerUser.setMobKills(damagerUser.getMobKills() + 1);

			// check if the entity is a civilian and increase wanted level
			if (entityMarkManager.countsForWanted(entity)) {
				handleWantedLevelIncrease(damagerUser);
			}

			return;
		}

		// check if it was a player or a mob
		User<Player> deadUser = userManager.getUser(player);

		damagerUser.setKills(damagerUser.getKills() + 1);

		// when does the attacked user have a bounty
		Bounty bounty = deadUser.getBounty();
		if (bounty.hasBounty()) {
			double amount = bounty.getAmount();

			damagerUser.getEconomy().deposit(amount);
			bounty.resetBounty();
			damagerUser.getUser().sendMessage(ChatUtil.color("&a+" + amount));

			// reset the wanted level of the dead player
			deadUser.getWanted().reset();
		} else {
			handleBountyIncrease(damagerUser);
		}

		// increase the wanted level for killing another player
		handleWantedLevelIncrease(damagerUser);
	}

	private void createBloodParticle(Entity entity, double damage) {
		ParticleUtil.createBloodSplash(entity, damage);
	}

	private void handleWantedLevelIncrease(User<Player> damagerUser) {
		Wanted      wanted      = damagerUser.getWanted();
		WantedEvent wantedEvent = new WantedEvent(true, wanted);

		wantedEvent.setWantedUser(damagerUser);

		wanted.incrementLevel();

		if (SettingAddon.isWantedTimerEnabled() && wanted.isWanted()) {
			Executor executor = new WantedExecutor(gangland, wantedEvent, damagerUser);
			Timer    timer    = executor.createTimer();

			timer.start(true);
		}

		// update bounty based on the new wanted level
		int wantedLevel = wanted.getLevel();
		int userLevel   = damagerUser.getLevel().getLevelValue();

		Bounty bounty     = damagerUser.getBounty();
		double autoBounty = bounty.getAutoBountyIncrease(userLevel, wantedLevel);

		bounty.setAmount(bounty.getAmount() + autoBounty);

		BountyEvent bountyEvent = new BountyEvent(true, bounty);
		if (SettingAddon.isBountyTimerEnabled() && bounty.getAmount() < SettingAddon.getBountyTimerMax()) {
			Executor executor = new BountyExecutor(gangland, bountyEvent, damagerUser);
			Timer    timer    = executor.createTimer();

			timer.start(true);
		}

		String format = String.format("&c&lWANTED LEVEL: &c%s &7(Bounty: &b+%s%.2f)", wanted.getLevelStars(),
									  SettingAddon.getMoneySymbol(), autoBounty);
		String message = ChatUtil.color(format);

		damagerUser.getUser().sendMessage(message);
	}

	private void handleBountyIncrease(User<Player> damagerUser) {
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
		gangland.getServer().getPluginManager().callEvent(bountyEvent);

		if (bountyEvent.isCancelled()) return;

		damagerUser.getBounty().setAmount(amount);
	}

}
