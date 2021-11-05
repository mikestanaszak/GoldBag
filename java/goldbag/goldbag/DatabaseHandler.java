package goldbag.goldbag;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.UUID;

public class DatabaseHandler {

    private Plugin pl = Bukkit.getPluginManager().getPlugin("GoldBag");
    private FileConfiguration config = ((GoldBag) pl).getConfigConfig();
    //TAKES PLAYER USERNAME AND GETS CURRENT BALANCE
    public int getBalance(UUID user){
        if(config.getString("savetype").equalsIgnoreCase("mysql")){
            Connection con = ((GoldBag) pl).getConnection();
            try {
                PreparedStatement sql = con
                        .prepareStatement("SELECT BALANCE FROM players JOIN purses ON PURSEID = " +
                                "purses.id WHERE USERID = ?");
                sql.setString(1, user.toString());
                ResultSet resultSet = sql.executeQuery();
                int returnValue = 0;
                if(resultSet.next()){
                    returnValue = resultSet.getInt("BALANCE");
                }
                resultSet.close();
                sql.close();
                return returnValue;
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return 0;
    }
    //TAKES PLAYER USERNAME AND SETS BALANCE TO CURRENT PURSE BALANCE
    public boolean setBalance(UUID user, int balance){
        if(config.getString("savetype").equalsIgnoreCase("mysql")){
            Connection con = ((GoldBag) pl).getConnection();
            try {
                PreparedStatement sql = con
                        .prepareStatement("UPDATE `players` JOIN purses ON PURSEID = " +
                                "purses.id SET purses.BALANCE = ? WHERE USERID = ?");
                sql.setInt(1, balance);
                sql.setString(2, user.toString());
                sql.executeUpdate();
                sql.close();
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }
    //TAKES PLAYER USERNAME AND ADDS BALANCE TO CURRENT PURSE BALANCE
    public boolean addBalance(UUID user, int balance){
        if(config.getString("savetype").equalsIgnoreCase("mysql")){
            Connection con = ((GoldBag) pl).getConnection();
            try {
                int temp = getBalance(user) + balance;
                PreparedStatement sql = con
                        .prepareStatement("UPDATE `players` JOIN purses ON PURSEID = " +
                                "purses.id SET purses.BALANCE = ? WHERE USERID = ?");
                sql.setInt(1, temp);
                sql.setString(2, user.toString());
                sql.executeUpdate();
                sql.close();
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }
    //TAKES PLAYER USERNAME AND REMOVES BALANCE TO CURRENT PURSE BALANCE
    public boolean removeBalance(UUID user, int balance){
        if(config.getString("savetype").equalsIgnoreCase("mysql")){
            Connection con = ((GoldBag) pl).getConnection();
            try {
                int temp = getBalance(user) - balance;
                PreparedStatement sql = con
                        .prepareStatement("UPDATE `players` JOIN purses ON PURSEID =" +
                                " purses.id SET purses.BALANCE = ? WHERE USERID = ?");
                sql.setInt(1, temp);
                sql.setString(2, user.toString());
                sql.executeUpdate();
                sql.close();
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }
    //TAKES PLAYER USERNAME AND RETURNS IF PLAYER IS IN TABLE BASED ON USERNAME
    public boolean userExists(UUID user) {
        if(config.getString("savetype").equalsIgnoreCase("mysql")){
            Connection con = ((GoldBag) pl).getConnection();
            try{
                PreparedStatement sql = con.prepareStatement("SELECT IGN FROM players WHERE USERID = ?");
                sql.setString(1, user.toString());
                ResultSet results = sql.executeQuery();
                if(results.next()){
                    return true;
                }
                else{
                    return false;
                }
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
        return false;
    }

    public ArrayList<String> getAllUsers() {
        if(config.getString("savetype").equalsIgnoreCase("mysql")){
            Connection con = ((GoldBag) pl).getConnection();
            try {
                PreparedStatement sql = con.prepareStatement("SELECT IGN FROM players");
                ResultSet results = sql.executeQuery();
                ArrayList<String> returnList = new ArrayList<String>();
                while(results.next()){
                    returnList.add(results.getString("IGN"));
                }
                return returnList;
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
        ArrayList<String> returnList = new ArrayList<String>();
        return returnList;
    }
}