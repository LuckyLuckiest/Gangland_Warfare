package me.luckyraven.copsncrooks.detainment.release;

import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the teleport target used when releasing a player from jail. Implementations should try the jail's configured
 * exit first, then the fallback waypoint named in {@code Settings}, then a final world-spawn fallback.
 */
public interface ReleaseExitContract {

	@Nullable
	Location resolveExit(int jailId);
}
