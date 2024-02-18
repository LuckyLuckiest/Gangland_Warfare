package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.feature.bounty.Bounty;
import me.luckyraven.feature.bounty.BountyEvent;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.timer.RepeatingTimer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class EntityDamage implements Listener {

	private final UserManager<Player> userManager;
	private final Gangland            gangland;

	public EntityDamage(Gangland gangland) {
		this.gangland    = gangland;
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

		// the current damager
		User<Player> damagerUser = userManager.getUser(damager.getPlayer());

		// the entity killed is not a player
		if (!(event.getEntity() instanceof Player player)) {
			damagerUser.setMobKills(damagerUser.getMobKills() + 1);
			return;
		}

		// check if it was a player or a mob
		User<Player> deadUser = userManager.getUser(player);

		damagerUser.setKills(damagerUser.getKills() + 1);

		// when does the attacked user have a bounty
		if (deadUser.getBounty().hasBounty()) {
			double amount = deadUser.getBounty().getAmount();

			damagerUser.getEconomy().deposit(amount);
			deadUser.getBounty().resetBounty();
			damagerUser.getUser().sendMessage(ChatUtil.color("&a+" + amount));
		} else {
			// TODO change the values when there is a level system
			// the start value would be the player level
			Bounty      userBounty  = damagerUser.getBounty();
			BountyEvent bountyEvent = new BountyEvent(userBounty);

			bountyEvent.setUserBounty(damagerUser);
			if (userBounty.getRepeatingTimer() == null && SettingAddon.isBountyTimerEnabled()) {
				if (userBounty.getAmount() < SettingAddon.getBountyTimerMax()) {
					// create a timer and start it
					userBounty.createTimer(gangland, SettingAddon.getBountyTimeInterval(),
										   timer -> bountyExecutor(damagerUser, bountyEvent, timer)).start(false);
				}
			} else {
				double eachKill = SettingAddon.getBountyEachKillValue();
				double amount   = eachKill + userBounty.getAmount();

				if (amount > SettingAddon.getBountyMaxKill()) return;

				bountyEvent.setAmountApplied(eachKill);
				gangland.getServer().getPluginManager().callEvent(bountyEvent);

				if (!bountyEvent.isCancelled()) damagerUser.getBounty().setAmount(amount);
			}
		}
		// change wanted level
	}

	private void bountyExecutor(User<Player> user, BountyEvent bountyEvent, RepeatingTimer timer) {
		Bounty userBounty = user.getBounty();
		double currentBounty = user.getBounty().getAmount() == 0D ?
							   SettingAddon.getBountyEachKillValue() / SettingAddon.getBountyTimerMultiple() :
							   user.getBounty().getAmount();

		if (userBounty.getAmount() >= SettingAddon.getBountyTimerMax()) timer.stop();
		else {
			double amount = currentBounty * SettingAddon.getBountyTimerMultiple();
			bountyEvent.setAmountApplied(amount - currentBounty);

			// call the event
			gangland.getServer().getPluginManager().callEvent(bountyEvent);

			if (!bountyEvent.isCancelled())
				// change the value
				userBounty.setAmount(amount);
		}
	}

}
