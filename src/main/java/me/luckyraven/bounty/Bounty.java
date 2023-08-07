package me.luckyraven.bounty;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.timer.RepeatingTimer;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Consumer;

import java.util.HashMap;
import java.util.Map;

public class Bounty {

	private final Map<CommandSender, Double> userSetBounty;

	private @Getter
	@Setter         double         amount;
	private @Getter RepeatingTimer repeatingTimer;

	public Bounty() {
		this.amount = 0D;
		this.userSetBounty = new HashMap<>();
	}

	public RepeatingTimer createTimer(JavaPlugin plugin, long seconds, Consumer<RepeatingTimer> timer) {
		this.repeatingTimer = new RepeatingTimer(plugin, 20L * seconds, timer);
		return repeatingTimer;
	}

	public boolean hasBounty() {
		return amount != 0D;
	}

	public void resetBounty() {
		this.amount = 0D;
		this.userSetBounty.clear();
	}

	public int size() {
		return userSetBounty.size();
	}

	public double getSetAmount(CommandSender sender) {
		return userSetBounty.get(sender);
	}

	public void addBounty(CommandSender sender, double amount) {
		if (userSetBounty.containsKey(sender)) {
			double value = userSetBounty.get(sender);
			userSetBounty.put(sender, value + amount);
		} else userSetBounty.put(sender, amount);
		this.amount += amount;
	}

	public void removeBounty(CommandSender sender) {
		double amount = userSetBounty.get(sender);
		userSetBounty.remove(sender);
		this.amount = Math.max(0D, this.amount - amount);
	}

	public boolean containsBounty(CommandSender sender) {
		return userSetBounty.containsKey(sender);
	}

	@Override
	public String toString() {
		return String.format("{amount=%.2f}", amount);
	}

}
