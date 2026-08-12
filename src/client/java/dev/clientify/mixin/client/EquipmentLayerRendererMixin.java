package dev.clientify.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.clientify.client.modules.HitColorModule;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Substitutes the hardcoded NO_OVERLAY on armor pieces when armor-flash is armed. */
@Mixin(EquipmentLayerRenderer.class)
public abstract class EquipmentLayerRendererMixin {
	@ModifyExpressionValue(
			method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
			at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/texture/OverlayTexture;NO_OVERLAY:I")
	)
	private int clientify$armorOverlay(int original) {
		return HitColorModule.overrideArmorOverlay(original);
	}
}
