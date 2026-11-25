package my.celium.org.betterpets.mixin;

import my.celium.org.betterpets.talk.ParrotTalk;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets tamed parrots hear what players say in chat. */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Inject(method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V",
            at = @At("HEAD"))
    private void betterpets$parrotsHear(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound type,
            CallbackInfo ci) {
        if (my.celium.org.betterpets.PetGate.on() && my.celium.org.betterpets.BetterPetsMod.isModEnabled()) {
            ParrotTalk.heard(sender, message.signedContent());
        }
    }
}
