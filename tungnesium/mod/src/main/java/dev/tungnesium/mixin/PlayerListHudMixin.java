package dev.tungnesium.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.tungnesium.TungConfig;
import dev.tungnesium.ui.TabRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;

@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void tungnesium$render(DrawContext context, int scaledWindowWidth, Scoreboard scoreboard,
                                   ScoreboardObjective objective, CallbackInfo ci) {
        if (!TungConfig.tab) return;
        TabRenderer.render(context, scaledWindowWidth);
        ci.cancel();
    }
}
