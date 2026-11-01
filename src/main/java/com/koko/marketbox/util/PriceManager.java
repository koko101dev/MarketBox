package com.koko.marketbox.util;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages item prices loaded from item_prices.txt in the config directory.
 */
public class PriceManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, Double> PRICES = new ConcurrentHashMap<>();
    private static final String FILE_NAME = "item_prices.txt";

    /**
     * Loads prices from the item_prices.txt file in the config directory.
     */
    public static void loadPrices() {
        PRICES.clear();
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("MarketBox");
        File file = configDir.resolve(FILE_NAME).toFile();
        
        if (!file.exists()) {
            LOGGER.warn("Price file {} not found in config directory, attempting to create default...", file.getAbsolutePath());
            createDefaultPriceFile(file);
        }

        if (!file.exists()) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] parts = line.split("\\s+");
                if (parts.length >= 2) {
                    String itemId = parts[0];
                    try {
                        String priceStr = parts[1].replace(",", ""); // Handle potential comma in price
                        double price = Double.parseDouble(priceStr);
                        PRICES.put(itemId, price);
                    } catch (NumberFormatException e) {
                        LOGGER.error("Invalid price format in line: {}", line);
                    }
                }
            }
            LOGGER.info("Successfully loaded {} prices from {}", PRICES.size(), file.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("Failed to read price file: {}", e.getMessage());
        }
    }

    private static void createDefaultPriceFile(File file) {
        try {
            Files.createDirectories(file.getParentFile().toPath());
            
            // Try to load from resources first (Production & Dev)
            try (InputStream is = PriceManager.class.getResourceAsStream("/" + FILE_NAME)) {
                if (is != null) {
                    Files.copy(is, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.info("Created default price file from mod resources.");
                    return;
                }
            }

            // Fallback to project root (Old Dev logic)
            File sourceFile = new File(FILE_NAME);
            if (sourceFile.exists()) {
                Files.copy(sourceFile.toPath(), file.toPath());
                LOGGER.info("Copied default price file from project root to config directory.");
                return;
            }

            // Final fallback: minimal default
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                writer.println("# MarketBox Item Prices");
                writer.println("# Format: itemID price // name");
                writer.println("minecraft:oak_log 10.0");
                writer.println("minecraft:diamond 2500.0");
            }
            LOGGER.info("Created a minimal default price file.");
        } catch (IOException e) {
            LOGGER.error("Failed to create default price file: {}", e.getMessage());
        }
    }

    /**
     * Gets the price of an item stack.
     */
    public static Double getPrice(ItemStack stack) {
        if (stack.isEmpty()) return null;
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return PRICES.get(itemId);
    }

    /**
     * Gets the price of an item by its ID.
     */
    public static Double getPrice(String itemId) {
        return PRICES.get(itemId);
    }

    /**
     * Updates the prices map (used for client-side synchronization).
     */
    public static void updatePrices(Map<String, Double> newPrices) {
        PRICES.clear();
        PRICES.putAll(newPrices);
        LOGGER.info("Updated prices from server synchronization ({} entries)", newPrices.size());
    }

    /**
     * Returns a copy of the current prices map.
     */
    public static Map<String, Double> getPrices() {
        return new HashMap<>(PRICES);
    }
}
