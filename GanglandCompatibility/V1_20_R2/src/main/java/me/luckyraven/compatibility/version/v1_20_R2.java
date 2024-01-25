package me.luckyraven.compatibility.version;

import me.luckyraven.feature.weapon.projectile.recoil.RecoilCompatibility;
import net.minecraft.network.protocol.game.PacketPlayOutPosition;
import net.minecraft.world.entity.RelativeMovement;
import org.bukkit.craftbukkit.v1_20_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class v1_20_R2 extends RecoilCompatibility {

	private final Set<RelativeMovement> ABSOLUTE_FLAGS = new HashSet<>(
			Arrays.asList(RelativeMovement.a, RelativeMovement.b, RelativeMovement.c));
	private final Set<RelativeMovement> RELATIVE_FLAGS = new HashSet<>(
			Arrays.asList(RelativeMovement.a, RelativeMovement.b, RelativeMovement.c, RelativeMovement.e,
						  RelativeMovement.d));

	@Override
	public void modifyCameraRotation(@NotNull Player player, float yaw, float pitch, boolean position) {
		float newYaw   = -yaw + 1;
		float newPitch = pitch - 1;

		PacketPlayOutPosition packet = new PacketPlayOutPosition(0D, 0D, 0D, newYaw, newPitch,
																 position ? RELATIVE_FLAGS : ABSOLUTE_FLAGS, 0);

		(((CraftPlayer) player).getHandle()).c.b(packet);
	}
}
