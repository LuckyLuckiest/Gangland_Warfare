package me.luckyraven.sign.listener;

import lombok.RequiredArgsConstructor;
import me.luckyraven.sign.service.SignInformation;
import me.luckyraven.sign.service.SignInteractionService;
import me.luckyraven.sign.validation.SignValidationException;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;

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

			information.sendSuccess(player, "Sign created successfully!");
		} catch (SignValidationException exception) {
			information.sendError(player, "Invalid sign: " + exception.getMessage());
			event.setCancelled(true);
		}
	}

}
