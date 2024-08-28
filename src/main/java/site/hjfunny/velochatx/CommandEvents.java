package site.hjfunny.velochatx;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;

public class CommandEvents {
    @Subscribe(order = PostOrder.NORMAL)
    public void onCommand(CommandExecuteEvent evt){
        
    }
}
