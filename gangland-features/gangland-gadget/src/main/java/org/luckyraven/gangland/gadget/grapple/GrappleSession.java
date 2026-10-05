package org.luckyraven.gangland.gadget.grapple;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * One grapple from cast to release. Two phases: the shot is in flight ({@link #isAttached()} false: the hook is driven
 * along {@link #getDirection()}), then the hook has latched onto a block ({@link #getAnchor()} set) and the rope swings
 * and reels the player in. The vanilla {@link FishHook} is kept for its client-rendered line and is removed whenever
 * the session ends.
 */
@Getter
public class GrappleSession {

	private final Player   player;
	private final Grapple  grapple;
	private final FishHook hook;
	private final Vector   direction;

	@Setter
	private double shotTravelled = 0.0;

	@Nullable
	private Location anchor;

	@Setter
	private double ropeLength;

	@Setter
	private double reelSpeed = 0.0;

	/** The player's body centre on the previous tick; its delta is what the client really did. */
	@Setter
	private Vector lastPosition;

	/** The velocity the rope last gave the player (or measured while slack); integrated server-side every tick. */
	@Setter
	private Vector ropeVelocity = new Vector();

	@Setter
	private int elapsedTicks = 0;

	public GrappleSession(Player player, Grapple grapple, FishHook hook, Vector bodyCentre, Vector direction) {
		this.player       = player;
		this.grapple      = grapple;
		this.hook         = hook;
		this.lastPosition = bodyCentre.clone();
		this.direction    = direction.clone().normalize();
	}

	public boolean isAttached() {
		return anchor != null;
	}

	/**
	 * Latches the rope onto {@code anchor} with the player's body centre at {@code playerPosition}, moving at
	 * {@code velocity}: the rope starts exactly as long as the current distance, so the swing begins from wherever the
	 * player is, carrying their run-up into it.
	 */
	void attach(Location anchor, Vector playerPosition, Vector velocity) {
		this.anchor       = anchor;
		this.ropeLength   = playerPosition.distance(anchor.toVector());
		this.lastPosition = playerPosition.clone();
		this.ropeVelocity = velocity.clone();
	}
}
