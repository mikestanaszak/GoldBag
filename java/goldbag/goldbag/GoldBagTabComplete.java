package goldbag.goldbag;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class GoldBagTabComplete implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (sender instanceof Player) {
            ArrayList<String> tabComplete = new ArrayList<String>();
            if(command.getName().equalsIgnoreCase("purse")){
                if(sender.hasPermission("goldpurse.admin")){
                    if (args.length == 1){
                        tabComplete.add("give");
                        tabComplete.add("take");
                        tabComplete.add("set");
                        return tabComplete;
                    }
                    else if(args.length == 2) {
                        DatabaseHandler db = new DatabaseHandler();
                        return db.getAllUsers();
                    }
                    else if(args.length == 3 ){ return tabComplete; }
                    else { return null; }
                }
                else{
                    return tabComplete;
                }
            }
            else if(command.getName().equalsIgnoreCase("balance") || alias.equalsIgnoreCase("balance")){
                return null;
            }
            else { return null; }
        }
        return null;
    }
}

/*
commands:
  purse:
    usage: /purse
    description: Opens your purse
    permission: goldpurse.use
  balance:
    usage: /balance
    description: Returns current balance of your purse
    permission: goldpurse.use
    aliases: money
  purse give:
    usage: /purse give [ign] [amount]
    description: Adds currency to purse
    permission: goldpurse.admin
  purse take:
    usage: /purse take [ign] [amount]
    description: Removes currency from purse
    permission: goldpurse.admin
  purse set:
    usage: /purse set [ign] [amount]
    description: Sets currency in purse
    permission: goldpurse.admin
  purse create:
    usage: /purse create [id]
    description: Creates a virtual purse
    permission: goldpurse.admin
*/