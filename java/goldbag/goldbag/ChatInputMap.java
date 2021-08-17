package goldbag.goldbag;

import java.util.HashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

public class ChatInputMap {

    private HashMap<String, Object> chatmap = new HashMap<String, Object>();
    public HashMap<String, Object> getChatMap(){return chatmap;}

    public void addToMap(Player p, Object o){
        addToMap(p.getName().toString(),(ChatInputStuff) o);
    }

    public void addToMap(String p, ChatInputStuff o){
        if(chatmap.containsKey(p)){
            ((ChatInputStuff)chatmap.get(p)).cleanup();
            chatmap.remove(p);
        }else{
            chatmap.put(p, o);
        }
    }

    public void doSomething(String name, String message, AsyncPlayerChatEvent event, Plugin plugin){
        if(chatmap.containsKey(name)){
            if(chatmap.get(name)!= null){
                ((ChatInputStuff)chatmap.get(name)).doSomething(this, name, message, event, plugin);
            }
        }
    }

    public boolean PlayerInChat(String name){return chatmap.containsKey(name);}

    public void removePlayer(String name){
        if(chatmap.containsKey(name)){
            ((ChatInputStuff)chatmap.get(name)).cleanup();
            chatmap.remove(name);
        }
    }

}
