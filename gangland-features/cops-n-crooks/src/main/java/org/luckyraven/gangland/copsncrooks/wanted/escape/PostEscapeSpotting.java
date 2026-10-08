package org.luckyraven.gangland.copsncrooks.wanted.escape;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.wanted.config.PostEscapeSettings;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.core.wanted.WantedStars;

import java.util.function.Supplier;

/**
 * Contact during the post-escape search (0.16.1 wanted-1): a squad that sights a searched player raises him by
 * {@code Wanted.Post_Escape.Spotted_Stars}. The raise is a wanted start, which ends the search and resumes the normal chase
 * with its HUD. 0 stars keeps the search harmless: the cops only trail him.
 *
 * @since 0.16.1
 */
public final class PostEscapeSpotting {

	private final PostEscapeSearch             search;
	private final WantedStars                  stars;
	private final UserManager<Player>          users;
	private final Supplier<PostEscapeSettings> settings;

	public PostEscapeSpotting(PostEscapeSearch search, WantedStars stars, UserManager<Player> users,
	                          Supplier<PostEscapeSettings> settings) {
		this.search   = search;
		this.stars    = stars;
		this.users    = users;
		this.settings = settings;
	}

	/** The AI-tick hook: raises a searched player the group's squad has sighted. Main thread, as every AI tick. */
	public void onAiTick(Player player, @Nullable CopGroup group) {
		int spotted = settings.get().spottedStars();
		if (spotted <= 0 || group == null || group.isEmpty()) return;
		if (!search.isSearching(player.getUniqueId()) || !group.getSquad().hasFreshSighting()) return;

		User<Player> user = users.getUser(player);
		if (user == null) return;

		// A sighting is a new crime scene for the chase; the raise ends the search through the wanted start
		stars.raise(user, spotted, WantedCause.CRIME);
	}
}
