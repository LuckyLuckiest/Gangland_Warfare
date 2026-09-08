package org.luckyraven.gangland.sign;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * Maps a legacy weapon/ammo/wearable trade-sign header ({@code weapon-buy}, {@code weapon-sell}, {@code ammo-buy},
 * {@code ammo-sell}, {@code wearable-buy}, {@code wearable-sell} — without the {@code <prefix>-} that
 * {@link SignManager#setupSigns()} adds) onto the generic {@code item-buy}/{@code item-sell} replacement plus the
 * line-3 definition prefix the old, module-owned sign type implied (e.g. {@code weapon:}), so a sign placed before
 * this stream keeps resolving once Bartizan is installed and publishes the matching item vocabulary (A2 Q-G, R5).
 *
 * <p>Backed by {@code Settings.Signs.Legacy_Aliases}, six {@code "<newHeaderKey>:<definitionPrefix>"} strings — see
 * {@code settings.yml}. An unrecognised header is left untouched: {@link #rewrite} returns {@code null}.
 */
public final class LegacySignRewriter {

	/** One legacy header's replacement: the generic sign-type key suffix and the line-3 definition prefix. */
	public record Rewritten(String headerKey, String definitionPrefix) {
	}

	private final Map<String, Rewritten> aliases;

	public LegacySignRewriter(String weaponBuy, String weaponSell, String ammoBuy, String ammoSell,
	                          String wearableBuy, String wearableSell) {
		this.aliases = Map.of(
				"weapon-buy", parse(weaponBuy),
				"weapon-sell", parse(weaponSell),
				"ammo-buy", parse(ammoBuy),
				"ammo-sell", parse(ammoSell),
				"wearable-buy", parse(wearableBuy),
				"wearable-sell", parse(wearableSell));
	}

	private static Rewritten parse(String alias) {
		String[] parts = alias.split(":", 2);
		return new Rewritten(parts[0], parts.length > 1 ? parts[1] : "");
	}

	/**
	 * @param legacyHeader a sign-type key with the {@code <prefix>-} already stripped, e.g. {@code "weapon-buy"} or
	 *                     {@code "WEAPON-BUY"} — matching is case-insensitive, since a placed sign's formatted header
	 *                     line reads upper-case; may be {@code null} (never thrown on, always treated as unrecognised)
	 *
	 * @return the generic replacement, or {@code null} if {@code legacyHeader} does not name one of the six legacy
	 *         headers — left untouched by design, so a non-legacy (or already-generic) sign header passes straight
	 *         through unmodified
	 */
	@Nullable
	public Rewritten rewrite(@Nullable String legacyHeader) {
		if (legacyHeader == null) return null;

		return aliases.get(legacyHeader.toLowerCase(Locale.ROOT));
	}

}
