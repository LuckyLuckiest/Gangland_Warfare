package me.luckyraven.gang.contract;

import me.luckyraven.gang.Gang;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Read-side contract for gang lookups, exposed so other feature modules (notably gangland-turf) can resolve a
 * {@link Gang} by id without importing impl's {@code GangManager}.
 */
public interface GangLookupContract {

	@Nullable Gang findById(int gangId);

	Collection<Gang> getAll();
}
