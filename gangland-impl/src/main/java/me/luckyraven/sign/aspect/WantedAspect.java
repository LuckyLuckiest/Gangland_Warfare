package me.luckyraven.sign.aspect;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.wanted.Wanted;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.sign.model.ParsedSign;
import me.luckyraven.sign.type.WantedSign;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public class WantedAspect implements SignAspect {

	private final UserManager<Player> userManager;

	@Override
	public AspectResult execute(Player player, ParsedSign sign) {
		User<Player> user = userManager.getUser(player);

		if (user == null) return AspectResult.failure(Messages.PLAYER_NOT_FOUND.toString());

		Wanted wanted = user.getWanted();
		int    amount = sign.getAmount();

		WantedSign.WantedType wantedType = WantedSign.WantedType.valueOf(sign.getContent().toUpperCase());

		switch (wantedType) {
			case INCREASE -> {
				int currentLevel = wanted.getLevel();

				wanted.setLevel(currentLevel + amount);

				String string = Messages.WANTED_INCREASED.toString(Messages.Type.NO_CHANGE);
				String replace = string.replace("%amount%", String.valueOf(amount))
									   .replace("%stars%", wanted.getLevelStars());
				return AspectResult.success(replace);
			}
			case REMOVE -> {
				for (int i = 0; i < amount; i++) {
					wanted.decrementLevel();
				}

				String string = Messages.WANTED_DECREASED.toString(Messages.Type.NO_CHANGE);
				String replace = string.replace("%amount%", String.valueOf(amount))
									   .replace("%stars%", wanted.getLevelStars());
				return AspectResult.success(replace);
			}
			case CLEAR -> {
				wanted.reset();

				String string  = Messages.WANTED_CLEARED.toString(Messages.Type.NO_CHANGE);
				String replace = string.replace("%stars%", wanted.getLevelStars());
				return AspectResult.success(replace);
			}
			default -> {
				return AspectResult.failure("Unknown wanted operation type");
			}
		}
	}

	@Override
	public boolean canExecute(Player player, ParsedSign sign) {
		User<Player> user = userManager.getUser(player);

		if (user == null) {
			return false;
		}

		WantedSign.WantedType wantedType = WantedSign.WantedType.valueOf(sign.getContent().toUpperCase());

		if (wantedType != WantedSign.WantedType.INCREASE) {
			Wanted wanted = user.getWanted();

			return wanted.getLevel() > 0;
		}

		return true;
	}

	@Override
	public String getName() {
		return "WantedAspect";
	}

}
