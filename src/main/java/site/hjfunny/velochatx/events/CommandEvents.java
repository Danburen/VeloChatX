package site.hjfunny.velochatx.events;

import com.mojang.brigadier.tree.RootCommandNode;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.command.PlayerAvailableCommandsEvent;
import com.velocitypowered.api.event.player.TabCompleteEvent;
import com.velocitypowered.api.proxy.Player;
import me.waterwood.plugin.WaterPlugin;
import site.hjfunny.velochatx.VeloChatX;
import site.hjfunny.velochatx.commands.MentionCommand;

import java.util.ArrayList;
import java.util.List;

public class CommandEvents {
    @Subscribe(order = PostOrder.FIRST)
    public void onTabComplete(TabCompleteEvent evt){
        Player player = evt.getPlayer();
        WaterPlugin.getLogger().info("tac:"+player.getUsername());
    }
}