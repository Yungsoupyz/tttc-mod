package dev.tungnesium.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.tungnesium.TungConfig;
import dev.tungnesium.ui.SidebarRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.scoreboard.ScoreboardObjective;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At("HEAD"), cancellable = true)
    private void tungnesium$sidebar(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
        if (!TungConfig.scoreboard) return;
        SidebarRenderer.render(context, objective);
        ci.cancel();
    }
}
