package me.luckyraven.sign.aspect;

import lombok.RequiredArgsConstructor;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.feature.wanted.Wanted;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.sign.model.ParsedSign;
import me.luckyraven.sign.type.WantedSign;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public class WantedAspect implements SignAspect {

	private final UserManager<Player> userManager;

	@Override
	public AspectResult execute(Player player, ParsedSign sign) {
		User<Player> user   = userManager.getUser(player);
		Wanted       wanted = user.getWanted();
		int          amount = sign.getAmount();

		WantedSign.WantedType wantedType = WantedSign.WantedType.valueOf(sign.getContent().toUpperCase());

		switch (wantedType) {
			case INCREASE -> {
				int currentLevel = wanted.getLevel();

				wanted.setLevel(currentLevel + amount);

				String string = MessageAddon.WANTED_INCREASED.toString(MessageAddon.Type.NO_CHANGE);
				String replace = string.replace("%amount%", String.valueOf(amount))
									   .replace("%stars%", wanted.getLevelStars());
				return AspectResult.success(replace);
			}
			case REMOVE -> {
				for (int i = 0; i < amount; i++) {
					wanted.decrementLevel();
				}

				String string = MessageAddon.WANTED_DECREASED.toString(MessageAddon.Type.NO_CHANGE);
				String replace = string.replace("%amount%", String.valueOf(amount))
									   .replace("%stars%", wanted.getLevelStars());
				return AspectResult.success(replace);
			}
			case CLEAR -> {
				wanted.reset();

				String string  = MessageAddon.WANTED_CLEARED.toString(MessageAddon.Type.NO_CHANGE);
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
