package dev.clientify.mixin.client;

import dev.clientify.client.modules.PingModule;
import dev.clientify.client.modules.TotemModule;
import dev.clientify.client.util.TotemCounts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	/**
	 * TAIL = after PacketUtils.ensureRunningOnSameThread's re-dispatch, so this only fires on
	 * the main thread. The pong echoes the request's send timestamp, so RTT is computable no
	 * matter who sent the request (us, vanilla's F3+2 monitor, or another mod).
	 */
	@Inject(method = "handlePongResponse", at = @At("TAIL"))
	private void clientify$pong(ClientboundPongResponsePacket packet, CallbackInfo ci) {
		PingModule.onPongResponse(packet.time());
	}

	/**
	 * The event the server sends everyone when a totem saves someone. Vanilla handles it here
	 * rather than in the entity, alongside guardian beams and sniffers, so this is where it is.
	 */
	private static final byte TOTEM_POP = 35;

	/**
	 * Counts other players' totem pops for the Totem Counter.
	 *
	 * <p>TAIL for the same reason the pong hook uses it — past the point where the packet has been
	 * handed to the main thread.
	 */
	@Inject(method = "handleEntityEvent", at = @At("TAIL"))
	private void clientify$totemPop(ClientboundEntityEventPacket packet, CallbackInfo ci) {
		if (packet.getEventId() != TOTEM_POP || !TotemModule.counting()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null && packet.getEntity(mc.level) instanceof Player player) {
			TotemCounts.pop(player.getUUID());
		}
	}
}
