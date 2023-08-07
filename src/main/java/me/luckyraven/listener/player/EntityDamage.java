package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.bounty.Bounty;
import me.luckyraven.bounty.BountyEvent;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.UserDatabase;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.timer.RepeatingTimer;
import me.luckyraven.util.ChatUtil;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.jetbrains.annotations.Nullable;

public class EntityDamage implements Listener {

	private final UserManager<Player> userManager;
	private final Gangland            gangland;

	public EntityDamage(Gangland gangland) {
		this.gangland = gangland;
		this.userManager = gangland.getInitializer().getUserManager();
	}

	@EventHandler
	public void onPlayerEntityDeath(EntityDamageByEntityEvent event) {
		Player damager;
		if (event.getDamager() instanceof Player player) damager = player;
		else if (event.getDamager() instanceof Projectile projectile) {
			if (projectile.getShooter() instanceof Player player) damager = player;
			else return;
		} else return;

		if (!(event.getEntity() instanceof LivingEntity livingEntity &&
				livingEntity.getHealth() <= event.getFinalDamage())) return;

		User<Player> user = userManager.getUser(damager.getPlayer());

		// check if it was a player or a mob
		User<Player> deadUser = null;
		if (event.getEntity() instanceof Player player) {
			deadUser = userManager.getUser(player);

			user.setKills(user.getKills() + 1);

			// when does the attacked user have a bounty
			if (deadUser.getBounty().hasBounty()) {
				double amount = deadUser.getBounty().getAmount();

				user.setBalance(user.getBalance() + amount);
				deadUser.getBounty().resetBounty();

				user.getUser().sendMessage(ChatUtil.color("&a+" + amount));
			} else {
				// TODO change the values when there is a level system
				// the start value would be the player level
				Bounty userBounty = user.getBounty();

				BountyEvent bountyEvent = new BountyEvent(user);
				if (userBounty.getRepeatingTimer() == null && SettingAddon.isBountyTimerEnable()) {
					if (userBounty.getAmount() < SettingAddon.getBountyTimerMax()) {
						// create a timer and start it
						userBounty.createTimer(gangland, SettingAddon.getBountyTimeInterval(),
						                       timer -> bountyExecutor(user, bountyEvent, timer)).start();
					}
				} else {
					double eachKill = SettingAddon.getBountyEachKillValue();
					double amount   = eachKill + userBounty.getAmount();

					if (amount <= SettingAddon.getBountyMaxKill()) {
						bountyEvent.setAmountApplied(eachKill);

						gangland.getServer().getPluginManager().callEvent(bountyEvent);

						if (!bountyEvent.isCancelled()) user.getBounty().setAmount(amount);
					}
				}

				// change wanted level

			}
		} else user.setMobKills(user.getMobKills() + 1);

		updateDatabase(user, deadUser);
	}

	private void bountyExecutor(User<Player> user, BountyEvent bountyEvent, RepeatingTimer timer) {
		Bounty userBounty = user.getBounty();
		double currentBounty = user.getBounty().getAmount() == 0D ? SettingAddon.getBountyEachKillValue() /
				SettingAddon.getBountyTimerMultiple() : user.getBounty().getAmount();

		if (userBounty.getAmount() >= SettingAddon.getBountyTimerMax()) timer.stop();
		else {
			double amount = currentBounty * SettingAddon.getBountyTimerMultiple();
			bountyEvent.setAmountApplied(amount - currentBounty);

			// call the event
			gangland.getServer().getPluginManager().callEvent(bountyEvent);

			if (!bountyEvent.isCancelled())
				// change the value
				userBounty.setAmount(amount);

			updateDatabase(user, null);
		}
	}

	private void updateDatabase(User<Player> user, @Nullable User<Player> otherUser) {
		for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
			if (handler instanceof UserDatabase userDatabase) {
				DatabaseHelper helper = new DatabaseHelper(gangland, handler);

				helper.runQueries(database -> {
					userDatabase.updateDataTable(user);
					if (otherUser != null) userDatabase.updateDataTable(otherUser);

				});
				break;
			}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerDeath(PlayerDeathEvent event) {
		User<Player> user = userManager.getUser(event.getEntity());

		user.setDeaths(user.getDeaths() + 1);
		updateDatabase(user, null);
	}

}
