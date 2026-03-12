package me.luckyraven.copsncrooks.detainment;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
public class DetainedPlayer {

	private final UUID            playerId;
	private final int             jailId;
	private       DetainmentState state;
}
