package org.luckyraven.gangland.copsncrooks.wanted.escape;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.wanted.config.PostEscapeSettings;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.keystone.npc.NpcSquad;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Contact during the post-escape search (0.16.1 wanted-1): a squad that sights a searched player raises him by
 * {@code Wanted.Post_Escape.Spotted_Stars}. The raise is a wanted start, which ends the search and resumes the normal chase
 * with its HUD. 0 stars keeps the search harmless: the cops only trail him.
 * <p>
 * A tip-off is not a sighting (0.16.1 wanted-8): a stuck-recycle or hand-off stamps the squad's sighting at the suspect's
 * real position, so a fresh sighting that the group tipped off within {@link NpcSquad#SIGHTING_GRACE_MS} is ignored, as
 * {@code EvasionClock} ignores it.
 *
 * @since 0.16.1
 */
public final class PostEscapeSpotting {

	private final PostEscapeSearch             search;
	private final WantedStars                  stars;
	private final UserManager<Player>          users;
	private final Supplier<PostEscapeSettings> settings;
	/** The radio clock the tip-off stamps are written on (ms). */
	private final LongSupplier                 clock;

	public PostEscapeSpotting(PostEscapeSearch search, WantedStars stars, UserManager<Player> users,
	                          Supplier<PostEscapeSettings> settings, LongSupplier clock) {
		this.search   = search;
		this.stars    = stars;
		this.users    = users;
		this.settings = settings;
		this.clock    = clock;
	}

	/** The AI-tick hook: raises a searched player the group's squad has sighted. Main thread, as every AI tick. */
	public void onAiTick(Player player, @Nullable CopGroup group) {
		int spotted = settings.get().spottedStars();
		if (spotted <= 0 || group == null || group.isEmpty()) return;
		if (!search.isSearching(player.getUniqueId()) || !group.getSquad().hasFreshSighting()) return;
		if (group.tippedOffWithin(clock.getAsLong(), NpcSquad.SIGHTING_GRACE_MS)) return;

		User<Player> user = users.getUser(player);
		if (user == null) return;

		// A sighting is a new crime scene for the chase; the raise ends the search through the wanted start. No crime was
		// committed, so the cause is UNKNOWN: the heat floor applies and the chase learner does not count it (wanted-9).
		stars.raise(user, spotted, WantedCause.UNKNOWN);
	}
}
