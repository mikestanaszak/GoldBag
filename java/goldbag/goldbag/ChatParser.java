package goldbag.goldbag;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

import static org.bukkit.Bukkit.*;

public class ChatParser extends ChatInputStuff {
    private int prompt;
    private int amount;
    private int stage = 0;
    public ChatParser(int type){
        prompt = type;
    }
    @Override public void doSomething(ChatInputMap map, String username, String message, AsyncPlayerChatEvent event, Plugin plugin){
        event.setCancelled(true);
        DatabaseHandler databaseHandler = new DatabaseHandler();
        switch(prompt){
            case 1:
                //PAY CASE
                switch(stage){
                    case 0:
                        Player p = Bukkit.getPlayer(username);
                        try {
                            amount = Integer.parseInt(message);
                        } catch (Exception e){
                            e.printStackTrace();
                            map.removePlayer(username);
                            event.getPlayer().sendMessage("§r§6§l[GoldBag]§6: §4Please input a correct amount of money... No extra characters");
                            return;
                        }
                        if(amount <= databaseHandler.getBalance(p.getUniqueId()) && amount > 0){
                            stage++;
                            event.getPlayer().sendMessage("§r§6§l[GoldBag]§6: Who would you like to send this to?");
                        }
                        else{
                            map.removePlayer(username);
                            Bukkit.getPlayer(username).sendMessage("§r§6§l[GoldBag]§6: §4You do not have enough money!");
                        }
                        return;
                    case 1:
                        Player p1 = Bukkit.getPlayer(message);
                        if(databaseHandler.userExists(p1.getUniqueId())){
                            databaseHandler.addBalance(p1.getUniqueId(), amount);
                            databaseHandler.removeBalance(p1.getUniqueId(), amount);
                            Bukkit.getPlayer(username).sendMessage("§r§6§l[GoldBag]§6: Payment sent!");
                            if(Bukkit.getPlayer(message) != null){
                                Bukkit.getPlayer(message).sendMessage("§r§6§l[GoldBag]§6: " + username + " has sent you " + amount);
                            }
                            map.removePlayer(username);
                        } else {
                            map.removePlayer(username);
                            Bukkit.getPlayer(username).sendMessage("§r§6§l[GoldBag]§6: §4This player does not exist!");
                        }
                        return;
                }
            case 2:
                Player p = Bukkit.getPlayer(username);
                //CREATE NOTE CASE
                try {
                    amount = Integer.parseInt(message);
                } catch (Exception e){
                    e.printStackTrace();
                    map.removePlayer(username);
                    event.getPlayer().sendMessage("§r§6§l[GoldBag]§6: §4Please input a correct amount of money... No extra characters");
                    return;
                }
                if(amount <= 0){
                    map.removePlayer(username);
                    Bukkit.getPlayer(username).sendMessage("§r§6§l[GoldBag]§6: §4Please input a positive number.");
                    return;
                }
                if(amount <= databaseHandler.getBalance(p.getUniqueId())){
                    map.removePlayer(username);
                    databaseHandler.removeBalance(p.getUniqueId(), amount);
                    ItemStack note =  new ItemStack(Material.PAPER);
                    ItemMeta meta = note.getItemMeta();
                    meta.setDisplayName("§r§6** Bank note **");
                    ArrayList<String> lore = new ArrayList();
                    lore.add(String.valueOf(amount));
                    meta.setLore(lore);
                    note.setItemMeta(meta);
                    p = event.getPlayer();
                    spawnEntity(p, note);
                }
                else{
                    map.removePlayer(username);
                    Bukkit.getPlayer(username).sendMessage("§r§6§l[GoldBag]§6: §4You do not have enough money!");
                }
                return;
        }
    }
    public void spawnEntity(Player p, ItemStack i){

        //SPAWNING ENTITY SYNC IF INV IS FULL OF PLAYER
        if(p.getInventory().firstEmpty() != -1){
            p.getInventory().addItem(i);
        }
        else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    p.getWorld().dropItem(p.getLocation(), i);
                }
            }.runTask(Bukkit.getPluginManager().getPlugin("GoldBag"));
        }
    }
    @Override
    public void cleanup() {

    }
}
