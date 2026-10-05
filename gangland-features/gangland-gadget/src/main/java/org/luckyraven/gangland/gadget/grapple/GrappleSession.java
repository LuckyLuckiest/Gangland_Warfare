package org.luckyraven.gangland.gadget.grapple;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * One grapple from cast to release. Two phases: the shot is in flight ({@link #isAttached()} false: the hook is being
 * stepped forward along {@link #getDirection()}), then the hook has latched onto a block ({@link #getAnchor()} set)
 * and the rope reels the player in. The vanilla {@link FishHook} is kept for its client-rendered line and is removed
 * whenever the session ends.
 */
@Getter
public class GrappleSession {

	private final Player   player;
	private final Grapple  grapple;
	private final FishHook hook;
	private final Vector   direction;

	/** Where the hook tip is while the shot is in flight. */
	private final Vector shotPosition;

	@Setter
	private double shotTravelled = 0.0;

	@Nullable
	private Location anchor;

	@Setter
	private double ropeLength;

	@Setter
	private double reelSpeed = 0.0;

	/** The player's position on the previous tick; its delta is the player's real velocity (client-authoritative). */
	@Setter
	private Vector lastPosition;

	@Setter
	private int elapsedTicks = 0;

	public GrappleSession(Player player, Grapple grapple, FishHook hook, Vector origin, Vector direction) {
		this.player       = player;
		this.grapple      = grapple;
		this.hook         = hook;
		this.shotPosition = origin.clone();
		this.direction    = direction.clone().normalize();
	}

	public boolean isAttached() {
		return anchor != null;
	}

	/**
	 * Latches the rope onto {@code anchor} with the player at {@code playerPosition}: the rope starts exactly as long
	 * as the current distance, so the swing begins from wherever the player is.
	 */
	void attach(Location anchor, Vector playerPosition) {
		this.anchor       = anchor;
		this.ropeLength   = playerPosition.distance(anchor.toVector());
		this.lastPosition = playerPosition.clone();
	}
}
