package me.luckyraven.weapon.raytrace;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.weapon.projectile.ProjectileState;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable per-shot working state for {@link WeaponRaytracer}. Holds the live ray position and direction along with the
 * iteration counters and accumulated tracer points.
 * <p>
 * For instant hitscan shots, this lives on the stack inside {@code WeaponRaytracer.fireInstant}. For stepped slow
 * projectiles (rockets, throwables) it lives on the heap, owned by the {@code SteppedProjectileTask} that drives the
 * visual entity, so penetration / ricochet counters persist across ticks.
 */
@Getter
@Setter
public class RaytraceContext {

	private final RaytraceRequest request;
	private final ProjectileState state;
	private final List<Location>  tracerSegments;

	private Location currentOrigin;
	private Vector   currentDir;
	private double   remaining;
	private int      iterations;

	public RaytraceContext(RaytraceRequest request, ProjectileState state) {
		this.request        = request;
		this.state          = state;
		this.tracerSegments = new ArrayList<>();
		this.currentOrigin  = request.getOrigin().clone();
		this.currentDir     = request.getDirection().clone().normalize();
		this.remaining      = request.getMaxDistance();
		this.iterations     = 0;
	}

}
