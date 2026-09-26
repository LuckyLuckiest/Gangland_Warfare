package org.luckyraven.gangland.gadget.jetpack;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;

/**
 * Tracks the active state of a player using a jetpack. Created when the jetpack is activated (double-tap space) and
 * destroyed when deactivated (land, remove chestplate, or disconnect).
 */
@Getter
public class JetpackSession {

	private final Player  player;
	@Setter
	private       Jetpack jetpack;

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

	/**
	 * {@code player.getAllowFlight()} captured just before {@code JetpackService.activate} forces it {@code true}.
	 * Restored on deactivation instead of hardcoding {@code false}, so a creative player's native mayfly (or any
	 * other plugin's grant) survives an equip/unequip cycle (gi=52).
	 */
	@Setter
	private boolean previousAllowFlight;

	public JetpackSession(Player player, Jetpack jetpack) {
		this.player  = player;
		this.jetpack = jetpack;
	}

}
