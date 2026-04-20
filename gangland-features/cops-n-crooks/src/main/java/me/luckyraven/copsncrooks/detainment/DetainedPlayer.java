package me.luckyraven.copsncrooks.detainment;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class DetainedPlayer {

	private final UUID            playerId;
	private       Integer         jailId;
	private       DetainmentState state;
	/**
	 * Epoch ms at which the transit timer fires, teleporting the player to jail. Null when not HANDCUFFED.
	 */
	private       Long            transitExpiresAt;
	/**
	 * Epoch ms at which the "Serve Sentence" countdown completes, auto-releasing the player. Null when not JAILED or
	 * when the player has not opted into serving yet.
	 */
	private       Long            sentenceExpiresAt;
	/**
	 * Snapshot of the player's wanted level at arrest time. Used to compute bail / bribe / sentence costs so they stay
	 * stable after the wanted clear on jail entry. Null when not arrested.
	 */
	private       Integer         wantedAtArrest;

	public DetainedPlayer(UUID playerId, Integer jailId, DetainmentState state) {
		this(playerId, jailId, state, null, null, null);
	}

	public DetainedPlayer(UUID playerId, Integer jailId, DetainmentState state, Long transitExpiresAt,
	                      Long sentenceExpiresAt, Integer wantedAtArrest) {
		this.playerId          = playerId;
		this.jailId            = jailId;
		this.state             = state;
		this.transitExpiresAt  = transitExpiresAt;
		this.sentenceExpiresAt = sentenceExpiresAt;
		this.wantedAtArrest    = wantedAtArrest;
	}
}
