package goldbag.goldbag;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.List;

import static org.bukkit.Bukkit.getPlayer;

public class GoldBagCommands implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        List<String> aliases = command.getAliases();
        DatabaseHandler databaseHandler = new DatabaseHandler();
        Plugin pl = Bukkit.getPluginManager().getPlugin("GoldBag");
        FileConfiguration config = ((GoldBag) pl).getConfigConfig();
        if (sender instanceof Player) {
            if(command.getName().equalsIgnoreCase("purse")){
                if(sender.hasPermission("goldpurse.admin")){
                    if(args.length == 3){
                        Player p = getPlayer(args[1]);
                        int bal;
                        switch (args[0]){
                            case "give":
                                if(databaseHandler.userExists(p.getUniqueId())){
                                    bal = Integer.parseInt(args[2]);
                                    databaseHandler.addBalance(p.getUniqueId(), bal);
                                    sender.sendMessage("§r§6§l[GoldBag]§6: Gave " + args[1] + " " + bal);
                                } else {
                                    sender.sendMessage("§r§6§l[GoldBag]§6: §4User does not exist");
                                }
                                return true;
                            case "take":
                                // take balance from args[1] of amount args[2]
                                if(databaseHandler.userExists(p.getUniqueId())){
                                    bal = Integer.parseInt(args[2]);
                                    databaseHandler.removeBalance(p.getUniqueId(), bal);
                                    sender.sendMessage("§r§6§l[GoldBag]§6: Removed " + bal + " from " + args[1]);
                                } else {
                                    sender.sendMessage("§r§6§l[GoldBag]§6: §4User does not exist");
                                }
                                return true;
                            case "set":
                                if(databaseHandler.userExists(p.getUniqueId())){
                                    bal = Integer.parseInt(args[2]);
                                    databaseHandler.setBalance(p.getUniqueId(), bal);
                                    sender.sendMessage("§r§6§l[GoldBag]§6: Set " + args[1] + " balance to " + bal);
                                } else {
                                    sender.sendMessage("§r§6§l[GoldBag]§6: §4User does not exist");
                                }
                                return true;
                        }
                    }
                    else if (args.length == 0) {
                        PurseGUI i = new PurseGUI();
                        Inventory gui = i.getGUI((Player) sender);
                        ((Player) sender).openInventory(gui);
                        return true;
                    }
                    else {
                        switch (args.length){
                            case 1:
                                sender.sendMessage("§r§6§l[GoldBag]§6: §4Please input user and the amount");
                                return true;
                            case 2:
                                sender.sendMessage("§r§6§l[GoldBag]§6: §4Please input the amount");
                                return true;
                        }
                    }
                }
                else {
                    PurseGUI i = new PurseGUI();
                    Inventory gui = i.getGUI((Player) sender);
                    ((Player) sender).openInventory(gui);
                    return true;
                }
            }
            else if(aliases.contains("money") || command.getName().equalsIgnoreCase("balance")){
                if(config.getString("savetype").equalsIgnoreCase("mysql")){
                    if(args.length == 0){
                        sender.sendMessage("§r§6§l[GoldBag]§6: Your balance is: " + databaseHandler.getBalance(((Player) sender).getUniqueId()));
                        return true;
                    } else if (args.length >= 1){
                        if(databaseHandler.userExists(getPlayer(args[0]).getUniqueId())){
                            sender.sendMessage("§r§6§l[GoldBag]§6: " + args[0] + " balance is: " + databaseHandler.getBalance(getPlayer(args[0]).getUniqueId()));
                        }
                        else{
                            sender.sendMessage("§r§6§l[GoldBag]§6: §4User does not exist");
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
//MAIN PURSE GUI
class PurseGUI{
    public Inventory getGUI(Player player){
        DatabaseHandler dataBaseHandler = new DatabaseHandler();
        Inventory i = Bukkit.createInventory(null,9,  "§6§lPurse");
        ItemStack temp = new ItemStack(Material.PAPER);
        ItemMeta meta = temp.getItemMeta();
        meta.setDisplayName("§r§6§lCreate Note");
        temp.setItemMeta(meta);
        i.setItem(1, temp);
        temp = new ItemStack(Material.CHEST);
        meta = temp.getItemMeta();
        meta.setDisplayName("§r§6§lDeposit");
        temp.setItemMeta(meta);
        i.setItem(3, temp);
        temp = new ItemStack(Material.RAW_GOLD);
        meta = temp.getItemMeta();
        meta.setDisplayName("§r§6§lBalance: " + dataBaseHandler.getBalance(player.getUniqueId()));
        temp.setItemMeta(meta);
        i.setItem(4, temp);
        temp = new ItemStack(Material.WRITABLE_BOOK);
        meta = temp.getItemMeta();
        meta.setDisplayName("§r§6§lWithdraw");
        temp.setItemMeta(meta);
        i.setItem(5, temp);
        temp = new ItemStack(Material.SPECTRAL_ARROW);
        meta = temp.getItemMeta();
        meta.setDisplayName("§r§6§lPay");
        temp.setItemMeta(meta);
        i.setItem(7, temp);
        return i;
    }
}



// Dealing with flatFile


/*
    goldpurse.use
    /purse (right click with raw gold or raw gold block) - open purse interface.
    /money, /balance, /purse balance - show purse balance in chat

    OP:
    goldpurse.admin
    /purse give playername # - adds to a purse
    /purse take playername # - removes from a purse
    /purse set playername # - sets a purse value
    /purse create virtualname - creates a virtual purse**
*/