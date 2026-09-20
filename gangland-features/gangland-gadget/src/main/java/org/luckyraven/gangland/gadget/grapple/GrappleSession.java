package org.luckyraven.gangland.gadget.grapple;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Tracks one in-progress grapple pull. Created when a launch lands on a block ({@code PlayerFishEvent.State
 * .IN_GROUND}) and destroyed when the pull ends (arrival, timeout, or a cancel trigger).
 */
@Getter
public class GrappleSession {

	private final Player   player;
	private final Grapple  grapple;
	private final Location anchor;

	@Setter
	private int elapsedTicks = 0;

	@Setter
	private double currentSpeed = 0.0;

	public GrappleSession(Player player, Grapple grapple, Location anchor) {
		this.player  = player;
		this.grapple = grapple;
		this.anchor  = anchor;
	}
}
