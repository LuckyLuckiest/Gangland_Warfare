package org.luckyraven.gangland.mail.command;

import org.luckyraven.gangland.gang.Gang;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The display-name to gang-id map behind the gang tab-completion of the invite and ally commands: a gang name is its own
 * token, a name shared by several entries becomes {@code name:id} so each stays selectable.
 */
public final class GangPickerMap {

	private GangPickerMap() {
	}

	/**
	 * @param gangs the candidate gangs; pass the whole list, a gang present twice counts twice towards a shared name
	 */
	public static Map<String, String> byDisplayName(List<Gang> gangs) {
		Map<String, Integer> nameCount = new HashMap<>();
		for (Gang gang : gangs) nameCount.merge(gang.getName(), 1, Integer::sum);

		Map<String, String> map = new HashMap<>();
		for (Gang gang : gangs) {
			String name        = gang.getName();
			String displayName = nameCount.get(name) > 1 ? name + ":" + gang.getId() : name;
			map.put(displayName, String.valueOf(gang.getId()));
		}
		return map;
	}

}
