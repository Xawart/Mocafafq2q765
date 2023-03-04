import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredListener;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class MCShopPlugin extends JavaPlugin {
    private static final String COMMAND_NAME = "mcshop";
    private static final String SET_PRICE_COMMAND = "setprice";
    private static final String SET_MIN_AMOUNT_COMMAND = "setminamount";
    private static final String SELL_HAND_COMMAND = "sellhand";
    private static final String PLUGIN_NAME = "MCShopPlugin";
    private static final String PLUGIN_VERSION = "1.0";
    private Map<Integer, Double> prices = new HashMap<>();
    private Map<Integer, Integer> minAmounts = new HashMap<>();
    private FileConfiguration config;

    @Override
    public void onEnable() {
        // Load the config file
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        // Load the prices and minimum amounts from the config
        loadPrices();
        loadMinAmounts();

        // Register the commands and event listeners
        PluginCommand command = getCommand(COMMAND_NAME);
        command.setExecutor(this);
        command.setTabCompleter(new MCShopTabCompleter(this));
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new MCShopListener(this), this);
    }

    @Override
    public void onDisable() {
        // Save the prices and minimum amounts to the config
        savePrices();
        saveMinAmounts();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be executed by a player.");
            return true;
        }

        Player player = (Player) sender;

        if (!cmd.getName().equalsIgnoreCase(COMMAND_NAME)) {
            return false;
        }

        if (args.length == 0) {
            // Show all items with prices and minimum amounts
            showAllItems(player);
        } else if (args.length == 2 && args[0].equalsIgnoreCase(SET_PRICE_COMMAND)) {
            // Set the price of an item
            setItemPrice(player, args[1]);
        } else if (args.length == 3 && args[0].equalsIgnoreCase(SET_MIN_AMOUNT_COMMAND)) {
            // Set the minimum amount of an item
            setItemMinAmount(player, args[1], args[2]);
        } else if (args.length == 1 && args[0].equalsIgnoreCase(SELL_HAND_COMMAND)) {
            // Sell the item in the player's hand
            sellHandItem(player);
        } else {
            return false;
        }

        return true;
    }

    private void showAllItems(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, "MCShop");
        for (Material material : Material.values()) {
            if (material.isItem() && !material.isAir()) {
                ItemStack itemStack = new ItemStack (material);
int itemId = material.getId();
double price = prices.getOrDefault(itemId, 10.0);
int minAmount = minAmounts.getOrDefault(itemId, 1);
            ItemMeta itemMeta = itemStack.getItemMeta();
            itemMeta.setDisplayName(material.toString());
            itemMeta.setLore(List.of("Price: " + price, "Min. Amount: " + minAmount));
            itemStack.setItemMeta(itemMeta);

            inventory.addItem(itemStack);
        }
    }
    player.openInventory(inventory);
}

private void setItemPrice(Player player, String itemIdString) {
    int itemId;
    try {
        itemId = Integer.parseInt(itemIdString);
    } catch (NumberFormatException e) {
        player.sendMessage("Invalid item ID.");
        return;
    }

    if (!prices.containsKey(itemId)) {
        player.sendMessage("Item not found.");
        return;
    }

    if (!player.hasPermission("mcshop.setprice")) {
        player.sendMessage("You don't have permission to use this command.");
        return;
    }

    double newPrice;
    try {
        newPrice = Double.parseDouble(args[2]);
    } catch (NumberFormatException e) {
        player.sendMessage("Invalid price.");
        return;
    }

    prices.put(itemId, newPrice);
    player.sendMessage("Price for item " + itemId + " set to " + newPrice + "$.");
}

private void setItemMinAmount(Player player, String itemIdString, String minAmountString) {
    int itemId;
    try {
        itemId = Integer.parseInt(itemIdString);
    } catch (NumberFormatException e) {
        player.sendMessage("Invalid item ID.");
        return;
    }

    if (!minAmounts.containsKey(itemId)) {
        player.sendMessage("Item not found.");
        return;
    }

    if (!player.hasPermission("mcshop.setminamount")) {
        player.sendMessage("You don't have permission to use this command.");
        return;
    }

    int newMinAmount;
    try {
        newMinAmount = Integer.parseInt(minAmountString);
    } catch (NumberFormatException e) {
        player.sendMessage("Invalid minimum amount.");
        return;
    }

    minAmounts.put(itemId, newMinAmount);
    player.sendMessage("Minimum amount for item " + itemId + " set to " + newMinAmount + ".");
}

private void sellHandItem(Player player) {
    ItemStack itemStack = player.getInventory().getItemInMainHand();
    int itemId = itemStack.getType().getId();
    int minAmount = minAmounts.getOrDefault(itemId, 1);
    double price = prices.getOrDefault(itemId, 10.0);
    int amount = itemStack.getAmount();

    if (amount < minAmount) {
        player.sendMessage("You need at least " + minAmount + " of this item to sell it.");
        return;
    }

    double total = price * amount;
    Economy economy = getServer().getServicesManager().getRegistration(Economy.class).getProvider();

    if (economy != null) {
        economy.depositPlayer(player, total);
        player.sendMessage("Sold " + amount + " of " + itemStack.getType().toString() + " for " + total + "$.");
        player.getInventory().setItemInMainHand(null);
    } else {
        player.sendMessage("Economy plugin not found.");
    }
}

private void loadPrices() {
    for (String key : config.getConfigurationSection("prices").getKeys(false)) {
        int itemId;
        try {
            itemId = Integer.parseInt(key);
        } catch (NumberFormatException e) {
            getLogger().log(Level.WARNING, "Invalid item ID in config: " + key);
            continue;
        }
        double price = config.getDouble("prices." + key);
        prices.put(itemId, price);
// Set the item's metadata to show the price and minimum amount
itemStack.setItemMeta(itemMeta);
// Add the item to the inventory
inventory.addItem(itemStack);
}

// Open the inventory for the player
player.openInventory(inventory);
}

private void setItemPrice(Player player, String itemIdString) {
// Parse the item ID from the command argument
int itemId;
try {
itemId = Integer.parseInt(itemIdString);
} catch (NumberFormatException e) {
player.sendMessage("Invalid item ID.");
return;
}

// Check if the player has the item in their inventory
ItemStack itemStack = new ItemStack(itemId);
if (!player.getInventory().contains(itemStack)) {
player.sendMessage("You don't have this item in your inventory.");
return;
}

// Parse the price from the command argument
double price;
try {
price = Double.parseDouble(args[2]);
} catch (NumberFormatException e) {
player.sendMessage("Invalid price.");
return;
}

// Set the price in the prices map
prices.put(itemId, price);

// Update the item's metadata in the player's inventory
updateItemMetadata(player, itemId);
player.sendMessage("Item price set to " + price + ".");
}

private void setItemMinAmount(Player player, String itemIdString, String minAmountString) {
// Parse the item ID from the command argument
int itemId;
try {
itemId = Integer.parseInt(itemIdString);
} catch (NumberFormatException e) {
player.sendMessage("Invalid item ID.");
return;
}

// Check if the player has the item in their inventory
ItemStack itemStack = new ItemStack(itemId);
if (!player.getInventory().contains(itemStack)) {
player.sendMessage("You don't have this item in your inventory.");
return;
}

// Parse the minimum amount from the command argument
int minAmount;
try {
minAmount = Integer.parseInt(minAmountString);
} catch (NumberFormatException e) {
player.sendMessage("Invalid minimum amount.");
return;
}

// Set the minimum amount in the minAmounts map
minAmounts.put(itemId, minAmount);

// Update the item's metadata in the player's inventory
updateItemMetadata(player, itemId);
player.sendMessage("Minimum amount set to " + minAmount + ".");
}

private void sellHandItem(Player player) {
// Get the item in the player's hand
ItemStack handItem = player.getInventory().getItemInMainHand();
if (handItem.getType() == Material.AIR) {
player.sendMessage("You don't have an item in your hand.");
return;
}

// Get the item's ID and check if it has a minimum amount set
int itemId = handItem.getType().getId();
int minAmount = minAmounts.getOrDefault(itemId, 1);
if (handItem.getAmount() < minAmount) {
player.sendMessage("You need at least " + minAmount + " of this item to sell it.");
return;
}

// Get the item's price and calculate the total sale value
double price = prices.getOrDefault(itemId, 10.0);
double saleValue = price * handItem.getAmount();

// Remove the item from the player's inventory
player.getInventory().remove(handItem);

// Add the sale value to the player's balance
// (Note: You will need to implement your own balance system)
player.sendMessage("Sold " + handItem.getAmount() + " " + handItem.getType().toString() + " for $" + saleValue + ".");
}

private void updateItemMetadata(Player player, int itemId) {
// Find the item in the player's inventory
Inventory inventory = player.getInventory();
for (int i = 0; i < inventory.getSize(); i++) {
ItemStack itemStack = inventory.getItem(i);
if (itemStack != null && itemStack.getType().getId() == itemId) {
// Update the item's metadata
double price = prices.getOrDefault(itemId,



// Add the item ID to the item's lore
itemMeta.setLore(List.of("Price: " + price, "Min. Amount: " + minAmount, "ID: " + itemId));

// Add the item to the inventory
itemStack.setItemMeta(itemMeta);
inventory.addItem(itemStack);
}
}
player.openInventory(inventory);
}

private void setItemPrice(Player player, String arg) {
int itemId;
double price;
try {
itemId = Integer.parseInt(arg);
} catch (NumberFormatException e) {
player.sendMessage("Invalid item ID.");
return;
}
if (!prices.containsKey(itemId)) {
player.sendMessage("This item is not available in the shop.");
return;
}
try {
price = Double.parseDouble(arg);
} catch (NumberFormatException e) {
player.sendMessage("Invalid price.");
return;
}
prices.put(itemId, price);
player.sendMessage("Price updated.");
}

private void setItemMinAmount(Player player, String arg1, String arg2) {
int itemId;
int minAmount;
try {
itemId = Integer.parseInt(arg1);
} catch (NumberFormatException e) {
player.sendMessage("Invalid item ID.");
return;
}
if (!minAmounts.containsKey(itemId)) {
player.sendMessage("This item is not available in the shop.");
return;
}
try {
minAmount = Integer.parseInt(arg2);
} catch (NumberFormatException e) {
player.sendMessage("Invalid minimum amount.");
return;
}
minAmounts.put(itemId, minAmount);
player.sendMessage("Minimum amount updated.");
}

private void sellHandItem(Player player) {
ItemStack itemStack = player.getInventory().getItemInMainHand();
int itemId = itemStack.getType().getId();
double price = prices.getOrDefault(itemId, 10.0);
int minAmount = minAmounts.getOrDefault(itemId, 1);
if (itemStack.getAmount() < minAmount) {
player.sendMessage("You must have at least " + minAmount + " of this item to sell it.");
return;
}
double earnings = itemStack.getAmount() * price;
player.getInventory().removeItem(new ItemStack(itemStack.getType(), itemStack.getAmount()));
player.sendMessage("You sold " + itemStack.getAmount() + " of " + itemStack.getType().toString() + " for $" + earnings + ".");
}

private void loadPrices() {
ConfigurationSection section = config.getConfigurationSection("prices");
if (section == null) {
return;
}
for (String key : section.getKeys(false)) {
int itemId = Integer.parseInt(key);
double price = section.getDouble(key);
prices.put(itemId, price);
}
}

private void savePrices() {
ConfigurationSection section = config.createSection("prices");
for (Map.Entry<Integer, Double> entry : prices.entrySet()) {
section.set(entry.getKey().toString(), entry.getValue());
}
try {
config.save(new File(getDataFolder(), "config.yml"));
} catch (IOException e) {
getLogger().log(Level.WARNING, "Failed to save prices to the config file.", e);
}
}

private void loadMinAmounts() {
ConfigurationSection section = config.getConfigurationSection("minAmounts");
if (section == null) {
return;
}
for (String key : section.getKeys(false)) {
int itemId = Integer.parseInt(key);
int minAmount = section.getInt(key);
minAmounts.put(itemId, minAmount);
}
}

private void saveMinAmounts() {
ConfigurationSection section = config.createSection("minAmounts");
for (Map.Entry<Integer, Integer> entry : minAmounts.entrySet())

        // Add item's ID to item's lore
        itemMeta.getLore().add("ID: " + itemId);
        itemStack.setItemMeta(itemMeta);
        inventory.addItem(itemStack);
        }
    }
    player.openInventory(inventory);
}

private void setItemPrice(Player player, String arg) {
    try {
        int itemId = Integer.parseInt(arg);
        if (!prices.containsKey(itemId)) {
            player.sendMessage("Invalid item ID.");
            return;
        }
        double price = Double.parseDouble(args[2]);
        prices.put(itemId, price);
        player.sendMessage("Price for item " + itemId + " set to " + price + "$");
    } catch (NumberFormatException e) {
        player.sendMessage("Invalid price value.");
    }
}

private void setItemMinAmount(Player player, String arg1, String arg2) {
    try {
        int itemId = Integer.parseInt(arg1);
        if (!minAmounts.containsKey(itemId)) {
            player.sendMessage("Invalid item ID.");
            return;
        }
        int minAmount = Integer.parseInt(arg2);
        minAmounts.put(itemId, minAmount);
        player.sendMessage("Minimum amount for item " + itemId + " set to " + minAmount);
    } catch (NumberFormatException e) {
        player.sendMessage("Invalid minimum amount value.");
    }
}

private void sellHandItem(Player player) {
    ItemStack itemStack = player.getInventory().getItemInMainHand();
    if (itemStack == null || itemStack.getType() == Material.AIR) {
        player.sendMessage("You need to hold an item to sell it.");
        return;
    }
    int itemId = itemStack.getType().getId();
    int minAmount = minAmounts.getOrDefault(itemId, 1);
    double price = prices.getOrDefault(itemId, 10.0);
    int amount = itemStack.getAmount();
    if (amount < minAmount) {
        player.sendMessage("You need to have at least " + minAmount + " " + itemStack.getType().toString() + " to sell it.");
        return;
    }
    double totalPrice = price * amount;
    player.getInventory().removeItem(itemStack);
    player.sendMessage("You sold " + amount + " " + itemStack.getType().toString() + " for " + totalPrice + "$.");
    player.getInventory().addItem(new ItemStack(Material.GOLD_INGOT, (int) totalPrice));
}

private void loadPrices() {
    for (String key : config.getConfigurationSection("prices").getKeys(false)) {
        try {
            int itemId = Integer.parseInt(key);
            double price = config.getDouble("prices." + key);
            prices.put(itemId, price);
        } catch (NumberFormatException e) {
            getLogger().log(Level.WARNING, "Invalid item ID: " + key);
        }
    }
}

private void loadMinAmounts() {
    for (String key : config.getConfigurationSection("min-amounts").getKeys(false)) {
        try {
            int itemId = Integer.parseInt(key);
            int minAmount = config.getInt("min-amounts." + key);
            minAmounts.put(itemId, minAmount);
        } catch (NumberFormatException e) {
            getLogger().log(Level.WARNING, "Invalid item ID: " + key);
        }
    }
}

private void savePrices() {
    for (int itemId : prices.keySet()) {
        config.set("prices." + itemId, prices.get(itemId));
    }
    try {
        config.save(new File(getDataFolder(), "config.yml"));
    } catch (IOException e) {
        getLogger().log(Level.SEVERE, "Failed to save prices to config file.", e);
    }
}

private void saveMinAmounts() {
    for (int itemId : minAmounts
