package me.luckyraven.bukkit.sign.aspect;

import me.luckyraven.bukkit.sign.model.ParsedSign;
import org.bukkit.entity.Player;

// TODO integrate it with the wanted level system
public class WantedLevelAspect implements SignAspect {

	private final WantedLevelManager wantedLevelManager;
	// true = add, false = remove
	private final boolean            add;

	public WantedLevelAspect(WantedLevelManager wantedLevelManager, boolean add) {
		this.wantedLevelManager = wantedLevelManager;
		this.add                = add;
	}

	@Override
	public AspectResult execute(Player player, ParsedSign sign) {
		int amount = sign.getAmount();

		wantedLevelManager.modifyWantedLevel(player, amount, add);

		String action = add ? "increased" : "decreased";

		return AspectResult.success("Wanted level " + action + " by " + amount);
	}

	@Override
	public boolean canExecute(Player player, ParsedSign sign) {
		return true;
	}

	@Override
	public String getName() {
		return "WantedLevelAspect-" + (add ? "Add" : "Remove");
	}

	@Override
	public int getPriority() {
		return 0;
	}

	@FunctionalInterface
	public interface WantedLevelManager {

		void modifyWantedLevel(Player player, int wantedLevel, boolean add);

	}
}
