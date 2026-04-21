package me.luckyraven.sign.listener;

import lombok.RequiredArgsConstructor;
import me.luckyraven.core.bean.listener.ListenerHandler;
import me.luckyraven.sign.aspect.AspectResult;
import me.luckyraven.sign.bulk.BulkActionManager;
import me.luckyraven.sign.bulk.BulkActionPreview;
import me.luckyraven.sign.bulk.BulkSignHandler;
import me.luckyraven.sign.bulk.PendingBulkAction;
import me.luckyraven.sign.model.ParsedSign;
import me.luckyraven.sign.registry.SignTypeDefinition;
import me.luckyraven.sign.service.SignInformation;
import me.luckyraven.sign.service.SignInteractionService;
import me.luckyraven.sign.validation.SignValidationException;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.List;
import java.util.Optional;

@ListenerHandler
@RequiredArgsConstructor
public class PlayerSignInteract implements Listener {

	private final SignInteractionService signService;
	private final SignInformation        information;
	private final BulkActionManager      bulkActionManager;

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

		Player player    = event.getPlayer();
		var    defByLine = signService.getRegistry().findByLine(title);

		if (defByLine.isEmpty()) {
			return;
		}

		Optional<ParsedSign> optParsed = Optional.empty();

		try {
			optParsed = signService.parseSign(lines, block.getLocation());
		} catch (SignValidationException ignored) { }

		if (optParsed.isEmpty()) {
			player.sendMessage(information.getInvalidSign());

			return;
		}

		event.setCancelled(true);

		ParsedSign parsed = optParsed.get();

		if (player.isSneaking()) {
			Optional<SignTypeDefinition> defOpt      = signService.getRegistry().getDefinition(parsed.getSignType());
			BulkSignHandler              bulkHandler = defOpt.map(SignTypeDefinition::getBulkHandler).orElse(null);

			if (bulkHandler != null) {
				handleBulkInteraction(player, parsed, bulkHandler);
				return;
			}
		}

		signService.handlerInteraction(player, parsed);
	}

	private void handleBulkInteraction(Player player, ParsedSign parsed, BulkSignHandler bulkHandler) {
		if (bulkActionManager.isPendingForSign(player, parsed)) {
			// Second shift-click on the same sign → confirm and execute
			PendingBulkAction action = bulkActionManager.confirm(player);

			if (action == null) {
				player.sendMessage(information.getBulkConfirmExpired());
				return;
			}

			List<AspectResult> results = action.getHandler().executeBulkAction(player, parsed);

			for (AspectResult result : results) {
				if (!result.isSuccess()) {
					information.sendError(player, result.getMessage());
					return;
				}

				if (result.getMessage() != null && !result.getMessage().isEmpty()) {
					information.sendSuccess(player, result.getMessage());
				}
			}

		} else {
			// First shift-click or a different sign → cancel any stale pending, then preview
			if (bulkActionManager.hasPending(player)) {
				bulkActionManager.cancel(player);
			}

			BulkActionPreview preview = bulkHandler.previewBulk(parsed);

			bulkActionManager.initiate(player, parsed, bulkHandler, preview);

			player.sendMessage(information.getBulkConfirmRequest(preview, bulkActionManager.getConfirmWindowSeconds()));
		}
	}

}
