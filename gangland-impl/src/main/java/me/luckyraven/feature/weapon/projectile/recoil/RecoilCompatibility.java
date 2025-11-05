package me.luckyraven.feature.weapon.projectile.recoil;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import lombok.Setter;
import me.luckyraven.Gangland;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Setter
public class RecoilCompatibility {

	private Gangland gangland;

	public void modifyCameraRotation(@NotNull Player player, float yaw, float pitch, boolean position) {
		float newYaw   = -yaw + 1;
		float newPitch = pitch - 1;

		boolean rotate = true;
		if (gangland != null) {
			if (gangland.getViaAPI() != null) rotate = gangland.getViaAPI().getPlayerVersion(player.getUniqueId()) >=
													   ProtocolVersion.v1_13.getVersion();
			else rotate = gangland.getInitializer().getVersionSetup().getVersionNumber() >= 1.13;
		}

		if (rotate)
			// Use Entity#setRotation for future updates that are still not updated to avoid issues
			player.setRotation(player.getLocation().getYaw() + newYaw, player.getLocation().getPitch() + newPitch);
	}

}
