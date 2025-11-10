package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.bukkit.sign.Sign;
import me.luckyraven.bukkit.sign.SignManager;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;

@ListenerHandler
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
			SignManager.validateSign(gangland, lines);

			Location signLocation = event.getBlock().getLocation();
			Sign     sign         = SignManager.createSign(gangland, lines, signLocation);

			// Translate creation form to display form on the placed sign
			SignManager.translateCreationToDisplay(lines);
			for (int i = 0; i < 4; i++) event.setLine(i, lines[i]);

			player.sendMessage(ChatUtil.prefixMessage("Successfully created the sign!"));
		} catch (IllegalArgumentException exception) {
			player.sendMessage(ChatUtil.errorMessage("Invalid sign: " + exception.getMessage()));
		}
	}
}
