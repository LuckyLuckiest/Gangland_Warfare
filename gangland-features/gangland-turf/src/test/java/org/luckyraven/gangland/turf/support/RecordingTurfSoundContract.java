package org.luckyraven.gangland.turf.support;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.turf.contract.TurfSoundContract;

import java.util.ArrayList;
import java.util.List;

/**
 * Recording fake for {@link TurfSoundContract} — every SFX call appends the listener to the matching list instead
 * of touching Keystone's {@code SoundEffect}, so a test can assert "the start sound played for exactly these
 * players" by inspecting a plain list.
 */
public final class RecordingTurfSoundContract implements TurfSoundContract {

	public final List<Player> captureStart    = new ArrayList<>();
	public final List<Player> captureComplete = new ArrayList<>();
	public final List<Player> captureFailed   = new ArrayList<>();
	public final List<Player> captureTick     = new ArrayList<>();
	public final List<Player> ownerCleared    = new ArrayList<>();

	@Override
	public void playCaptureStart(Player listener) {
		captureStart.add(listener);
	}

	@Override
	public void playCaptureComplete(Player listener) {
		captureComplete.add(listener);
	}

	@Override
	public void playCaptureFailed(Player listener) {
		captureFailed.add(listener);
	}

	@Override
	public void playCaptureTick(Player listener) {
		captureTick.add(listener);
	}

	@Override
	public void playOwnerCleared(Player listener) {
		ownerCleared.add(listener);
	}
}
