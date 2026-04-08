package me.luckyraven.item.contract;

import me.luckyraven.item.wearable.Wearable;
import org.jetbrains.annotations.Nullable;

/**
 * Narrow contract used by {@code WearableEquipListener} (in gangland-item) to look up registered {@link Wearable}
 * definitions without importing the gangland-weapon {@code WearableService} class.
 *
 * <p>The gangland-impl/-weapon side provides an implementation that delegates to the existing
 * {@code WearableService}/{@code WearableAddon}.
 */
public interface WearableEquipService {

	/**
	 * Returns the registered wearable for the given key, or {@code null} if no wearable is registered under that key.
	 */
	@Nullable
	Wearable getWearable(String key);

}
