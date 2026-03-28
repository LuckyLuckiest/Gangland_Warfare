package me.luckyraven.weapon.dto;

import lombok.Builder;
import lombok.Getter;
import me.luckyraven.weapon.projectile.ProjectileType;

@Getter
@Builder
public class ProjectileData {

	private final double         speed;
	private final ProjectileType type;
	private final double         damage;
	private final int            consumed;
	private final int            perShot;
	private final int            cooldown;
	private final int            distance;
	private final boolean        particle;

	@Override
	public String toString() {
		return String.format(
				"ProjectileData{speed=%.2f,type=%s,damage=%.2f,consumed=%d,perShot=%d,cooldown=%d,distance=%d,particle=%b}",
				speed, type, damage, consumed, perShot, cooldown, distance, particle);
	}

}
