package org.luckyraven.gangland.compatibility.recoil;

import com.viaversion.viaversion.api.ViaAPI;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.luckyraven.gangland.compatibility.VersionSetup;

@Setter
public class RecoilCompatibility {

	private ViaAPI<?> viaAPI;

	private VersionSetup versionSetup;

	public void modifyCameraRotation(@NotNull Player player, float yaw, float pitch, boolean position) {
		float newYaw   = -yaw + 1;
		float newPitch = pitch - 1;

		boolean rotate;
		if (viaAPI != null) {
			rotate = viaAPI.getPlayerVersion(player.getUniqueId()) >= ProtocolVersion.v1_13.getVersion();
		} else {
			rotate = versionSetup.getVersionNumber() >= 1.13;
		}

		if (rotate)
			// Use Entity#setRotation for future updates that are still not updated to avoid issues
			player.setRotation(player.getLocation().getYaw() + newYaw, player.getLocation().getPitch() + newPitch);
	}

}
