package me.luckyraven.weapon.types.throwable;

/**
 * Discriminator for throwable behaviour. Selected by the {@code Type:} key in a throwable weapon yml.
 *
 * <ul>
 *   <li>{@link #EXPLOSIVE} — vanilla TNT-style detonation: explosion radius, damage, fire ticks. The legacy
 *       behaviour all existing throwable yml files inherit when the {@code Type:} key is absent.</li>
 *   <li>{@link #SMOKE} — no damage; spawns a particle cloud at the impact point that lingers for
 *       {@code Cloud_Duration} ticks and re-applies the configured potion effects to entities inside the cloud
 *       periodically.</li>
 *   <li>{@link #STUN} — no damage; on detonation applies the configured potion effects to every living entity
 *       within the explosion radius. Used for flashbang-style grenades.</li>
 * </ul>
 */
public enum ThrowableType {

	EXPLOSIVE,
	SMOKE,
	STUN;

	public static ThrowableType getType(String type) {
		if (type == null) return EXPLOSIVE;
		return switch (type.toLowerCase()) {
			case "smoke" -> SMOKE;
			case "stun" -> STUN;
			default -> EXPLOSIVE;
		};
	}

}
