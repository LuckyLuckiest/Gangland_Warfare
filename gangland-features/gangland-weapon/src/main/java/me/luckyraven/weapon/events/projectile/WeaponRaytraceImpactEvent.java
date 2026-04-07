package me.luckyraven.weapon.events.projectile;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.events.WeaponEvent;
import me.luckyraven.weapon.projectile.ProjectileState;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired by {@code WeaponRaytracer} once per raytrace impact (entity or block). Replaces the old
 * {@code EntityDamageByEntityEvent}+{@code ProjectileHitEvent} pair as the canonical "weapon hit something" hook.
 * <p>
 * Listeners (cops-n-crooks NPC AI, vehicle damage, future feature consumers) subscribe to this event to react uniformly
 * to weapon impacts regardless of which weapon action type fired the shot — gun, incendiary, biological, melee, or
 * throwable.
 * <p>
 * Cancelling this event suppresses the actual damage application but does <em>not</em> stop the raytracer from
 * incrementing penetration / ricochet counters. This matches the existing semantics of the legacy event chain.
 */
@Getter
public class WeaponRaytraceImpactEvent extends WeaponEvent implements Cancellable {

	private static final HandlerList handler = new HandlerList();

	private final LivingEntity    shooter;
	private final Entity          hitEntity;
	private final Block           hitBlock;
	private final BlockFace       hitBlockFace;
	private final Location        impactPoint;
	private final ProjectileState state;

	@Setter
	private double damage;

	private boolean cancelled;

	public WeaponRaytraceImpactEvent(Weapon weapon, LivingEntity shooter, @Nullable Entity hitEntity,
	                                 @Nullable Block hitBlock, @Nullable BlockFace hitBlockFace,
	                                 Location impactPoint, double damage, ProjectileState state) {
		super(weapon);
		this.shooter      = shooter;
		this.hitEntity    = hitEntity;
		this.hitBlock     = hitBlock;
		this.hitBlockFace = hitBlockFace;
		this.impactPoint  = impactPoint;
		this.damage       = damage;
		this.state        = state;
	}

	public static HandlerList getHandlerList() {
		return handler;
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(boolean cancel) {
		this.cancelled = cancel;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return handler;
	}

}
