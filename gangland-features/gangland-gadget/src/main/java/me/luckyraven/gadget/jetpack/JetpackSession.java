package me.luckyraven.gadget.jetpack;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.item.wearable.Wearable;
import org.bukkit.entity.Player;

/**
 * Tracks the active state of a player using a jetpack. Created when the jetpack is activated (double-tap space) and
 * destroyed when deactivated (land, remove chestplate, or disconnect).
 */
@Getter
public class JetpackSession {

	private final Player   player;
	private final Wearable jetpackWearable;

	@Setter
	private JetpackTask task;
	@Setter
	private boolean     thrusting;
	@Setter
	private boolean     gliding;

	public JetpackSession(Player player, Wearable jetpackWearable) {
		this.player          = player;
		this.jetpackWearable = jetpackWearable;
	}

}
