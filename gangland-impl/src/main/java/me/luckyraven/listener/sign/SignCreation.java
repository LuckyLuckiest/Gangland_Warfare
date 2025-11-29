package me.luckyraven.listener.sign;

import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.sign.service.SignInteractionService;
import me.luckyraven.sign.validation.SignValidationException;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;

@ListenerHandler
public class SignCreation implements Listener {

	private final SignInteractionService signService;

	public SignCreation(SignInteractionService signService) {
		this.signService = signService;
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onSignCreate(SignChangeEvent event) {
		String[] lines = event.getLines();

		if (lines[0] == null || !lines[0].toLowerCase().startsWith("glw-")) {
			return;
		}

		Player player = event.getPlayer();

		try {
			signService.validateSign(lines);

			String[] newLines = signService.formatForDisplay(lines, SettingAddon.getMoneySymbol());

			for (int i = 0; i < newLines.length; i++) {
				event.setLine(i, newLines[i]);
			}

			player.sendMessage(ChatUtil.prefixMessage("Sign created successfully!"));
		} catch (SignValidationException exception) {
			player.sendMessage(ChatUtil.errorMessage("Invalid sign: " + exception.getMessage()));
			event.setCancelled(true);
		}
	}

}
