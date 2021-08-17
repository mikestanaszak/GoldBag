package goldbag.goldbag;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

public class ChatInputStuff {

    private int stage;
    public int getStage(){return stage;}
    public void setStage(int a){stage = a;}

    private String message;
    public String getMessage(){return message;}

    private String user;
    public String getUser(){return user;}

    public Player getPlayer(){return Bukkit.getPlayer(user);}

    public void doSomething(ChatInputMap map, String username, String message, AsyncPlayerChatEvent event, Plugin plugin){

    }

    public void cleanup() {

    }

}
