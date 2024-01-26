package me.luckyraven.feature.weapon.projectile.recoil;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class RecoilCompatibility {

	public void modifyCameraRotation(@NotNull Player player, float yaw, float pitch, boolean position) {
		float newYaw   = -yaw + 1;
		float newPitch = pitch - 1;

		// Use Entity#setRotation for future updates that are still not updated to avoid issues
		player.setRotation(player.getLocation().getYaw() + newYaw, player.getLocation().getPitch() + newPitch);
	}

}
