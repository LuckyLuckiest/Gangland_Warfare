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
	@Setter
	private       Wearable jetpackWearable;

	@Setter
	private          JetpackTask task;
	@Setter
	private          boolean     thrusting;
	@Setter
	private          boolean     gliding;
	@Setter
	private volatile boolean     inputJump;
	@Setter
	private volatile boolean     inputForward;
	@Setter
	private volatile boolean     inputBackward;
	@Setter
	private volatile boolean     inputLeft;
	@Setter
	private volatile boolean     inputRight;
	@Setter
	private volatile boolean     inputSneak;

	@Setter
	private boolean glideModeActive;

	public JetpackSession(Player player, Wearable jetpackWearable) {
		this.player          = player;
		this.jetpackWearable = jetpackWearable;
	}

}
