package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.util.ChatUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;

public class RegisterSign implements Listener {

	private final Gangland gangland;

	public RegisterSign(Gangland gangland) {
		this.gangland = gangland;
	}

	@EventHandler
	public void onSignPlace(SignChangeEvent event) {
		Player   player = event.getPlayer();
		String[] lines  = event.getLines();

		if (!lines[0].startsWith("glw-")) return;

		try {
//			SignManager.validateSign(gangland, lines);

			Location signLocation = event.getBlock().getLocation();
//			SignManager sign         = SignManager.createSign(gangland, lines, signLocation);

//			event.setLine(0, ChatUtil.color("&b" + sign.type().name()));
//			event.setLine(1, ChatUtil.color("&7" + sign.item()));

//			if (sign.type() != SignManager.Type.VIEW) {
//				event.setLine(2, ChatUtil.color("&5" + NumberUtil.valueFormat(sign.amount())));
//				event.setLine(3, ChatUtil.color("&a$" + NumberUtil.valueFormat(sign.price())));
//			}

			player.sendMessage(ChatUtil.prefixMessage("Successfully created the sign!"));
//			player.sendMessage(sign.location().toString());
		} catch (IllegalArgumentException exception) {
			player.sendMessage(ChatUtil.errorMessage("Invalid sign: " + exception.getMessage()));
		}
	}
}
