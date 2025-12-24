package me.luckyraven.sign.listener;

import lombok.RequiredArgsConstructor;
import me.luckyraven.sign.model.ParsedSign;
import me.luckyraven.sign.service.SignInformation;
import me.luckyraven.sign.service.SignInteractionService;
import me.luckyraven.sign.validation.SignValidationException;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Optional;

@ListenerHandler
@RequiredArgsConstructor
public class SignInteract implements Listener {

	private final SignInteractionService signService;
	private final SignInformation        information;

	@EventHandler(priority = EventPriority.HIGH)
	public void onSignInteract(PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
			return;
		}

		Block block = event.getClickedBlock();

		if (block == null || !(block.getState() instanceof Sign sign)) {
			return;
		}

		//noinspection deprecation
		String[] lines = sign.getLines();
		String   title = lines[0];

		if (title == null) {
			return;
		}

		Player player     = event.getPlayer();
		var    parsedSign = signService.getRegistry().findByLine(title);

		if (parsedSign.isEmpty()) {
			return;
		}

		Optional<ParsedSign> optParsed = Optional.empty();

		try {
			optParsed = signService.parseSign(lines, block.getLocation());
		} catch (SignValidationException ignored) { }

		if (optParsed.isEmpty()) {
			information.sendError(player, "Invalid sign!");

			return;
		}

		event.setCancelled(true);

		ParsedSign parsed = optParsed.get();

		signService.handlerInteraction(player, parsed);
	}

}
