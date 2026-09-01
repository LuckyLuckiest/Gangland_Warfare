package org.luckyraven.gangland.sign.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.gangland.sign.service.SignInformation;
import org.luckyraven.gangland.sign.service.SignInteractionService;
import org.luckyraven.gangland.sign.validation.SignValidationException;

@ListenerHandler
@RequiredArgsConstructor
public class SignCreation implements Listener {

	private final SignInteractionService signService;
	private final SignInformation        information;

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onSignCreate(SignChangeEvent event) {
		String[] lines = event.getLines();

		if (lines[0] == null || !lines[0].toLowerCase().startsWith(signService.getPrefix().toLowerCase())) {
			return;
		}

		Player player = event.getPlayer();

		try {
			signService.validateSign(lines);

			String[] newLines = signService.formatForDisplay(lines, information.getMoneySymbol());

			for (int i = 0; i < newLines.length; i++) {
				event.setLine(i, newLines[i]);
			}

			player.sendMessage(information.getSignCreated());
		} catch (SignValidationException exception) {
			player.sendMessage(information.getSignCreationFailed(exception.getMessage()));
			event.setCancelled(true);
		}
	}

}
