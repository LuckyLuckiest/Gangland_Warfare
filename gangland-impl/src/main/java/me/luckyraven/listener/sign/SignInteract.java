package me.luckyraven.listener.sign;

import me.luckyraven.bukkit.sign.model.ParsedSign;
import me.luckyraven.bukkit.sign.service.SignInteractionService;
import me.luckyraven.bukkit.sign.validation.SignValidationException;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.Optional;

@ListenerHandler
public class SignInteract implements Listener {

	private final JavaPlugin             plugin;
	private final SignInteractionService signService;
	private final BukkitScheduler        scheduler;

	public SignInteract(SignInteractionService signService, JavaPlugin plugin) {
		this.plugin      = plugin;
		this.signService = signService;
		this.scheduler   = plugin.getServer().getScheduler();
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onSignCreate(SignChangeEvent event) {
		String[] lines = event.getLines();

		if (lines[0] == null || !lines[0].toLowerCase().startsWith("glw-")) {
			return;
		}

		Player player = event.getPlayer();

		scheduler.runTaskAsynchronously(plugin, () -> {
			try {
				signService.validateSign(lines);

				scheduler.runTask(plugin, () -> {
					String[] newLines = signService.formatForDisplay(lines);

					for (int i = 0; i < newLines.length; i++) {
						event.setLine(i, newLines[i]);
					}

					player.sendMessage(ChatUtil.prefixMessage("Sign created successfully!"));
				});
			} catch (SignValidationException exception) {
				scheduler.runTask(plugin, () -> {
					event.setCancelled(true);
					player.sendMessage(ChatUtil.errorMessage("Invalid sign: " + exception.getMessage()));
				});
			}
		});
	}

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

		if (lines[0] == null || !lines[0].toLowerCase().contains("glw")) {
			return;
		}

		Player player = event.getPlayer();
		event.setCancelled(true);

		scheduler.runTaskAsynchronously(plugin, () -> {
			Optional<ParsedSign> optParsed = Optional.empty();
			try {
				optParsed = signService.parseSign(lines, block.getLocation());
			} catch (SignValidationException ignored) { }

			if (optParsed.isEmpty()) {
				scheduler.runTask(plugin, () -> {
					player.sendMessage(ChatUtil.errorMessage("Invalid sign!"));
				});

				return;
			}

			ParsedSign parsed = optParsed.get();

			scheduler.runTask(plugin, () -> {
				signService.handlerInteraction(player, parsed);
			});
		});
	}

}
