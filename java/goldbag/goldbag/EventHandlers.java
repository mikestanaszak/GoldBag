package goldbag.goldbag;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.io.FileReader;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.bukkit.Bukkit.*;

public class EventHandlers implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event){

        //ON JOIN CHECKING TO SEE IF THERE IS A PLAYER IN SQL TABLE WITH GIVEN UUID, CREATES IF DOESN'T EXIST ALSO CHECKS FOR DISPLAYNAMECHANGE
        Player p = event.getPlayer();
        Plugin pl = Bukkit.getPluginManager().getPlugin("GoldBag");
        FileConfiguration config = ((GoldBag) pl).getConfigConfig();
        if(config.getString("savetype").equalsIgnoreCase("mysql")){
            Connection con = ((GoldBag) pl).getConnection();
            try {
                PreparedStatement sql = con
                        .prepareStatement("SELECT * FROM players WHERE USERID = ?");
                sql.setString(1, String.valueOf(p.getUniqueId()));
                ResultSet resultSet = sql.executeQuery();
                if(!resultSet.next()){
                    PreparedStatement insert = con.prepareStatement("INSERT INTO `purses`(`BALANCE`) VALUES (0)");
                    insert.executeUpdate();
                    insert.close();
                    PreparedStatement get = con.prepareStatement("SELECT MAX(id) FROM purses");
                    ResultSet set = get.executeQuery();
                    set.next();
                    insert = con.prepareStatement("INSERT INTO `players` (`IGN`, `USERID`, `PURSEID`) VALUES ( ?, ?, ?)");
                    insert.setString(1, p.getName());
                    insert.setString(2, p.getUniqueId().toString());
                    insert.setInt(3, set.getInt("MAX(id)"));
                    insert.executeUpdate();
                    insert.close();
                    set.close();
                    get.close();
                } else if (!p.getName().equalsIgnoreCase(resultSet.getString("IGN"))) {
                    PreparedStatement update = con.prepareStatement("UPDATE `players` SET `IGN`=? WHERE `USERID`=?");
                    update.setString(1, p.getName());
                    update.setString(2, p.getUniqueId().toString());
                    update.executeUpdate();
                    update.close();
                }
                sql.close();
                resultSet.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private ChatInputMap cis = new ChatInputMap();

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event){
        //MAP FOR INPUT
        if(cis.PlayerInChat(event.getPlayer().getName())){
            cis.doSomething(event.getPlayer().getName(), event.getMessage(), event, getPluginManager().getPlugin("GoldBag"));
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        /*
         *           FOR MENU TAB
         *
         */
        if(event.getView().getTitle() == "§6§lPurse"){
            Plugin pl = Bukkit.getPluginManager().getPlugin("GoldBag");
            FileConfiguration config = ((GoldBag) pl).getConfigConfig();
            event.setCancelled(true);
            if(event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR){
                ArrayList<String> items = new ArrayList<>();
                items.add("§6§lCreate Note");
                items.add("§6§lDeposit");
                items.add("§6§lWithdraw");
                items.add("§6§lPay");
                if(items.contains(event.getCurrentItem().getItemMeta().getDisplayName())) {
                    DatabaseHandler databaseHandler = new DatabaseHandler();
                    Player p = (Player) event.getViewers().get(0);
                    switch(event.getCurrentItem().getItemMeta().getDisplayName()){
                        /*
                         *           CREATE NOTE MENU CLICK
                         *
                         */
                        case "§6§lCreate Note":
                            p.sendMessage("§r§6§l[GoldBag]§6: How much would you like to create a note for?");
                            Player d = getPlayer(p.getDisplayName());
                            p.sendMessage("§r§6§l[GoldBag]§6: Current balance: " + databaseHandler.getBalance(p.getUniqueId()));
                            cis.addToMap(p, new ChatParser(2));
                            p.closeInventory();
                            return;
                        /*
                         *           DEPOSIT MENU CLICK
                         *
                         *
                         *
                         *
                         *
                         *
                         *
                         */
                        case "§6§lDeposit":
                            ItemStack pane = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
                            ItemMeta meta = pane.getItemMeta();
                            meta.setDisplayName(" ");
                            pane.setItemMeta(meta);
                            Inventory dep = Bukkit.createInventory(null, 54, "§6§lDeposit");
                            for(int i = 1; i <= 6; i++){
                                for(int j = 0; j <= 8; j++){
                                    if(i == 1 || i == 6){
                                        dep.setItem(((i - 1) * 9) + j, pane);
                                    }
                                    else if (j == 0 || j == 8) {
                                        dep.setItem(((i - 1) * 9) + j, pane);
                                    }
                                }
                            }
                            ItemStack confirm = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
                            meta = confirm.getItemMeta();
                            meta.setDisplayName("§r§a§lClick to confirm");
                            confirm.setItemMeta(meta);
                            dep.setItem(4, confirm);
                            ItemStack info = new ItemStack(Material.MAP);
                            meta = info.getItemMeta();
                            meta.setDisplayName("§r§6§lCurrency Exchange");
                            ArrayList<String> lore = new ArrayList<>();
                            try {
                                FileReader fileReader = new FileReader(pl.getDataFolder() + "/values.json");
                                JsonParser jsonParser = new JsonParser();
                                JsonElement element = jsonParser.parse(fileReader);
                                JsonObject obj = element.getAsJsonObject();
                                JsonArray array = obj.getAsJsonArray("currency");
                                Consumer<JsonElement> consumer = (x) ->  lore.add("§r§6Material: " + x.getAsJsonObject().get("material") + " Value: " + x.getAsJsonObject().get("value"));
                                array.forEach(consumer);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            meta.setLore(lore);
                            info.setItemMeta(meta);
                            dep.setItem(49, info);
                            p.closeInventory();
                            p.openInventory(dep);
                            return;
                        /*
                         *           WITHDRAW MENU CLICK
                         *
                         *
                         *
                         *
                         *
                         *
                         *
                         */
                        case "§6§lWithdraw":
                            TreeMap<String, Integer> map = new TreeMap<>();
                            try {
                                FileReader fileReader = new FileReader(pl.getDataFolder() + "/values.json");
                                JsonParser jsonParser = new JsonParser();
                                JsonElement element = jsonParser.parse(fileReader);
                                JsonObject obj = element.getAsJsonObject();
                                JsonArray array = obj.getAsJsonArray("currency");
                                Consumer<JsonElement> consumer = (x) ->  map.put(x.getAsJsonObject().get("material").toString().replace("\"", ""), Integer.parseInt(String.valueOf(x.getAsJsonObject().get("value"))));
                                array.forEach(consumer);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            pane = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
                            meta = pane.getItemMeta();
                            meta.setDisplayName(" ");
                            pane.setItemMeta(meta);
                            Inventory withdrawGUI = Bukkit.createInventory(null, 54, "§6§lWithdraw");
                            for(int i = 1; i <= 6; i++){
                                for(int j = 0; j <= 8; j++){
                                    if(i == 1 || i == 6){
                                        withdrawGUI.setItem(((i - 1) * 9) + j, pane);
                                    }
                                    else if (j == 0 || j == 8) {
                                        withdrawGUI.setItem(((i - 1) * 9) + j, pane);
                                    }
                                }
                            }
                            AtomicInteger row = new AtomicInteger(1);
                            AtomicInteger col = new AtomicInteger(1);
                            BiConsumer<String, Integer> biConsumer = (x, y) -> {
                              ItemStack itemStack = new ItemStack(Material.valueOf(x.toUpperCase()));
                              ItemMeta itemMeta = itemStack.getItemMeta();
                              itemMeta.setDisplayName("§6§l" + x.toUpperCase());
                              ArrayList<String> loreList = new ArrayList<>();
                              loreList.add(y.toString());
                              itemMeta.setLore(loreList);
                              itemStack.setItemMeta(itemMeta);
                              withdrawGUI.setItem(row.get() * 9 + col.get(), itemStack);
                              col.getAndIncrement();
                              if(col.get() == 8){
                                  col.set(1);
                                  row.incrementAndGet();
                              }
                            };
                            map.forEach(biConsumer);
                            ItemStack back = new ItemStack(Material.RED_STAINED_GLASS_PANE);
                            meta = back.getItemMeta();
                            meta.setDisplayName("§r§c§lBack");
                            back.setItemMeta(meta);
                            withdrawGUI.setItem(4, back);
                            info = new ItemStack(Material.MAP);
                            meta = info.getItemMeta();
                            meta.setDisplayName("§6§lWithdraw");
                            lore = new ArrayList<>();
                            lore.add("Click to remove specific item");
                            lore.add("Current balance: " + databaseHandler.getBalance(p.getUniqueId()));
                            meta.setLore(lore);
                            info.setItemMeta(meta);
                            withdrawGUI.setItem(49, info);
                            p.closeInventory();
                            p.openInventory(withdrawGUI);
                            return;
                        /*
                         *           PAY MENU CLICK
                         *
                         */
                        case "§6§lPay":
                            p.sendMessage("§r§6§l[GoldBag]§6: How much would you like to pay?");
                            p.sendMessage("§r§6§l[GoldBag]§6: Current balance: " + databaseHandler.getBalance(p.getUniqueId()));
                            cis.addToMap(p, new ChatParser(1));
                            p.closeInventory();
                            return;
                    }
                }
                else {
                    return;
                }
            }
            return;
        }
        /*
        *           FOR DEPOSIT TAB
        *
        *
        *
        *
        *
        *
        *
        */
        else if(event.getView().getTitle() == "§6§lDeposit"){
            Plugin pl = Bukkit.getPluginManager().getPlugin("GoldBag");
            DatabaseHandler databaseHandler = new DatabaseHandler();
            if(event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR){
                if(event.getCurrentItem().getType() == Material.YELLOW_STAINED_GLASS_PANE || event.getCurrentItem().getType() == Material.MAP || event.getCurrentItem().getType() == Material.LIME_STAINED_GLASS_PANE){
                    event.setCancelled(true);
                    if(event.getCurrentItem().getType() == Material.LIME_STAINED_GLASS_PANE){
                        ItemStack[] items = event.getClickedInventory().getContents();
                        Map<String, Integer> map = new HashMap<>();
                        try {
                            FileReader fileReader = new FileReader(pl.getDataFolder() + "/values.json");
                            JsonParser jsonParser = new JsonParser();
                            JsonElement element = jsonParser.parse(fileReader);
                            JsonObject obj = element.getAsJsonObject();
                            JsonArray array = obj.getAsJsonArray("currency");
                            Consumer<JsonElement> consumer = (x) ->  map.put(x.getAsJsonObject().get("material").toString().replace("\"", ""), Integer.parseInt(String.valueOf(x.getAsJsonObject().get("value"))));
                            array.forEach(consumer);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        ArrayList<ItemStack> returnItems = new ArrayList<>();
                        int depositAmount = 0;
                        for(int i = 0; i < items.length; i++){
                            if(items[i] != null) {
                                ItemStack item = items[i];
                                if(map.containsKey(item.getType().toString().toLowerCase(Locale.ROOT))){
                                    int value = map.get(item.getType().toString().toLowerCase(Locale.ROOT));
                                    depositAmount += item.getAmount() * value;
                                }
                                else{
                                    if(item.getType() != Material.YELLOW_STAINED_GLASS_PANE && item.getType() != Material.LIME_STAINED_GLASS_PANE && item.getType() != Material.MAP){
                                        returnItems.add(items[i]);
                                    }
                                }
                            }
                        }
                        Player p = (Player) event.getViewers().get(0);
                        p.closeInventory();
                        Consumer<ItemStack> consumer = (x) -> {
                            if(p.getInventory().firstEmpty() != -1){
                                p.getInventory().addItem(x);
                            }
                            else{
                                p.getWorld().dropItem(p.getLocation(), x);
                            }
                        };
                        returnItems.forEach(consumer);
                        if (depositAmount > 0){
                            databaseHandler.addBalance(p.getUniqueId(), depositAmount);
                            p.sendMessage("§r§6§l[GoldBag]§6: Deposited " + depositAmount);
                            p.performCommand("balance");
                        }
                        return;
                    }
                }
            }
        }
        /*
         *           FOR WITHDRAW TAB
         *
         *
         *
         *
         *
         *
         *
         */
        else if(event.getView().getTitle() == "§6§lWithdraw"){
            event.setCancelled(true);
            if(event.getCurrentItem() != null  && event.getCurrentItem().getType() != Material.AIR){
                if(event.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("§c§lBack")){
                    Player p = (Player) event.getViewers().get(0);
                    p.closeInventory();
                    p.performCommand("purse");
                    return;
                }
                else{
                    if(event.getClickedInventory().getSize() == 54 && event.getCurrentItem().getType() != Material.YELLOW_STAINED_GLASS_PANE && event.getCurrentItem().getType() != Material.MAP){
                        ItemStack i = event.getCurrentItem();
                        ItemMeta meta = i.getItemMeta();
                        Player p = (Player) event.getViewers().get(0);
                        DatabaseHandler databaseHandler = new DatabaseHandler();
                        if(Integer.parseInt(meta.getLore().get(0)) > databaseHandler.getBalance(p.getUniqueId())){
                            p.sendMessage("§r§6§l[GoldBag]§6: §4You do not have enough money to buy this!");
                            p.closeInventory();
                        }
                        else{
                            if(p.getInventory().firstEmpty() != -1){
                                p.getInventory().addItem(new ItemStack(i.getType()));
                            }
                            else{
                                p.getWorld().dropItem(p.getLocation(), new ItemStack(i.getType()));
                            }
                            databaseHandler.removeBalance(p.getUniqueId(), Integer.parseInt(meta.getLore().get(0)));
                            ItemStack info = new ItemStack(Material.MAP);
                            meta = info.getItemMeta();
                            meta.setDisplayName("§6§lWithdraw");
                            ArrayList<String> lore = new ArrayList<>();
                            lore.add("Click to remove specific item");
                            lore.add("Current balance: " + databaseHandler.getBalance(p.getUniqueId()));
                            meta.setLore(lore);
                            info.setItemMeta(meta);
                            event.getClickedInventory().setItem(49, info);
                        }
                        return;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerUse(PlayerInteractEvent event){

        //CLAIM NOTES
        Player p = event.getPlayer();
        if(p.getInventory().getItemInMainHand().hasItemMeta()){
            if(p.getInventory().getItemInMainHand().getType() == Material.PAPER && ChatColor.stripColor(p.getInventory().getItemInMainHand().getItemMeta().getDisplayName()).equalsIgnoreCase("** Bank note **")){
                event.setCancelled(true);
                ItemMeta meta = p.getInventory().getItemInMainHand().getItemMeta();
                String amount = meta.getLore().get(0);
                DatabaseHandler databaseHandler = new DatabaseHandler();
                databaseHandler.addBalance(p.getUniqueId(), Integer.parseInt(amount) * p.getInventory().getItemInMainHand().getAmount());
                p.sendMessage("§r§6§l[GoldBag]§6: You have claimed: " + Integer.parseInt(amount) * p.getInventory().getItemInMainHand().getAmount());
                p.getInventory().getItemInMainHand().setAmount(0);
                p.performCommand("balance");
                return;
            }
        }
    }

}
