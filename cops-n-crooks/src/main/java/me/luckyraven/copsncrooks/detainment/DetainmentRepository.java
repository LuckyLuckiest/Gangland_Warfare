package me.luckyraven.copsncrooks.detainment;

import java.util.UUID;

public interface DetainmentRepository {

	DetainmentState loadState(UUID playerId);

	void saveState(UUID playerId, DetainmentState state);

}
