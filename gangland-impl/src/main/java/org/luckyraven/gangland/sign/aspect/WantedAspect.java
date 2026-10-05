package org.luckyraven.gangland.sign.aspect;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.gangland.sign.model.ParsedSign;
import org.luckyraven.gangland.sign.type.WantedSign;

@RequiredArgsConstructor
public class WantedAspect implements SignAspect {

	private final UserManager<Player> userManager;
	private final WantedStars         wantedStars;

	@Override
	public AspectResult execute(Player player, ParsedSign sign) {
		User<Player> user = userManager.getUser(player);

		if (user == null) return AspectResult.failure(Messages.PLAYER_NOT_FOUND.toString());

		Wanted wanted = user.getWanted();
		int    amount = sign.getAmount();

		WantedSign.WantedType wantedType = parseType(sign);

		if (wantedType == null) return AspectResult.failure("Unknown wanted operation type");

		switch (wantedType) {
			case INCREASE -> {
				wantedStars.raise(user, amount, WantedCause.SIGN);

				String string = Messages.WANTED_INCREASED.toString(Messages.Type.NO_CHANGE);
				String replace = string.replace("%amount%", String.valueOf(amount))
				                       .replace("%stars%", wanted.getLevelStars());
				return AspectResult.success(replace);
			}
			case REMOVE -> {
				wanted.setLevel(Math.max(0, wanted.getLevel() - amount), WantedCause.SIGN);

				String string = Messages.WANTED_DECREASED.toString(Messages.Type.NO_CHANGE);
				String replace = string.replace("%amount%", String.valueOf(amount))
				                       .replace("%stars%", wanted.getLevelStars());
				return AspectResult.success(replace);
			}
			case CLEAR -> {
				wanted.reset(WantedCause.SIGN);

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

		WantedSign.WantedType wantedType = parseType(sign);

		if (wantedType == null) return false;

		if (wantedType != WantedSign.WantedType.INCREASE) {
			Wanted wanted = user.getWanted();

			return wanted.getLevel() > 0;
		}

		return true;
	}

	/** The sign's operation, or {@code null} when its content is not one (a hand-edited or stale sign). */
	private static WantedSign.WantedType parseType(ParsedSign sign) {
		String content = sign.getContent();
		if (content == null) return null;

		try {
			return WantedSign.WantedType.valueOf(content.toUpperCase());
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	@Override
	public String getName() {
		return "WantedAspect";
	}

}
