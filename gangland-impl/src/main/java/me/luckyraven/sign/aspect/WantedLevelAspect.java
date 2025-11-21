package me.luckyraven.sign.aspect;

import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.feature.wanted.Wanted;
import me.luckyraven.sign.model.ParsedSign;
import org.bukkit.entity.Player;

public class WantedLevelAspect implements SignAspect {

	private final UserManager<Player> userManager;
	private final WantedType          wantedType;

	public WantedLevelAspect(UserManager<Player> userManager, WantedType wantedType) {
		this.userManager = userManager;
		this.wantedType  = wantedType;
	}

	@Override
	public AspectResult execute(Player player, ParsedSign sign) {
		User<Player> user   = userManager.getUser(player);
		Wanted       wanted = user.getWanted();
		int          amount = sign.getAmount();

		wanted.setLevel(amount);

		String format = "Wanted level " + wantedType.name().toLowerCase();

		if (wantedType == WantedType.CLEAR) {
			return AspectResult.successContinue(format);
		}

		return AspectResult.success(format + " by " + amount);
	}

	@Override
	public boolean canExecute(Player player, ParsedSign sign) {
		return true;
	}

	@Override
	public String getName() {
		return "WantedLevelAspect-" + wantedType.name();
	}

	public enum WantedType {
		INCREASE,
		DECREASE,
		CLEAR;
	}

}
