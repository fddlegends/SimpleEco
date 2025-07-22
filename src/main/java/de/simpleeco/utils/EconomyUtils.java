package de.simpleeco.utils;

import org.bukkit.Material;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Utility-Klasse für allgemeine Wirtschafts-Hilfsfunktionen
 * 
 * Bietet statische Methoden für häufig verwendete Operationen
 * im Zusammenhang mit dem Wirtschaftssystem.
 */
public class EconomyUtils {
    
    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("#,##0.00");
    private static final DecimalFormat PERCENTAGE_FORMAT = new DecimalFormat("#0.00");
    
    /**
     * Formatiert einen Geldbetrag als String
     * 
     * @param amount Der zu formatierende Betrag
     * @return Formatierter String (z.B. "1,234.56")
     */
    public static String formatCurrency(double amount) {
        return CURRENCY_FORMAT.format(amount);
    }
    
    /**
     * Formatiert eine Prozentzahl als String
     * 
     * @param percentage Die Prozentzahl (0.05 = 5%)
     * @return Formatierter String (z.B. "5.00%")
     */
    public static String formatPercentage(double percentage) {
        return PERCENTAGE_FORMAT.format(percentage * 100) + "%";
    }
    
    /**
     * Rundet einen Betrag auf 2 Dezimalstellen
     * 
     * @param amount Der zu rundende Betrag
     * @return Gerundeter Betrag
     */
    public static double roundCurrency(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }
    
    /**
     * Berechnet den Prozentsatz einer Änderung
     * 
     * @param oldValue Alter Wert
     * @param newValue Neuer Wert
     * @return Prozentuale Änderung (-1.0 bis +∞)
     */
    public static double calculatePercentageChange(double oldValue, double newValue) {
        if (oldValue == 0) {
            return newValue > 0 ? Double.POSITIVE_INFINITY : 0.0;
        }
        return (newValue - oldValue) / oldValue;
    }
    
    /**
     * Begrenzt einen Wert zwischen Minimum und Maximum
     * 
     * @param value Der zu begrenzende Wert
     * @param min Minimaler Wert
     * @param max Maximaler Wert
     * @return Begrenzter Wert
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * Berechnet einen gewichteten Durchschnitt
     * 
     * @param values Array von Werten
     * @param weights Array von Gewichtungen
     * @return Gewichteter Durchschnitt
     */
    public static double weightedAverage(double[] values, double[] weights) {
        if (values.length != weights.length) {
            throw new IllegalArgumentException("Arrays müssen gleiche Länge haben");
        }
        
        double sum = 0.0;
        double weightSum = 0.0;
        
        for (int i = 0; i < values.length; i++) {
            sum += values[i] * weights[i];
            weightSum += weights[i];
        }
        
        return weightSum > 0 ? sum / weightSum : 0.0;
    }
    
    /**
     * Konvertiert einen Material-Namen zu einem lesbaren deutschen Namen
     * 
     * @param material Das Material
     * @return Deutscher Name oder formatierter Enum-Name
     */
    public static String getGermanMaterialName(Material material) {
        // Basis-Übersetzungen für häufige Materialien
        return switch (material) {
            // Nahrung
            case WHEAT -> "Weizen";
            case CARROT -> "Karotte";
            case POTATO -> "Kartoffel";
            case BEETROOT -> "Rote Bete";
            case APPLE -> "Apfel";
            case BREAD -> "Brot";
            case COOKED_BEEF -> "Gebratenes Rindfleisch";
            case COOKED_PORKCHOP -> "Gebratenes Schweinefleisch";
            case COOKED_CHICKEN -> "Gebratenes Hühnchen";
            case COOKED_MUTTON -> "Gebratenes Hammelfleisch";
            case COOKED_RABBIT -> "Gebratenes Kaninchen";
            case COOKED_COD -> "Gebratener Kabeljau";
            case COOKED_SALMON -> "Gebratener Lachs";
            
            // Erze und Barren
            case DIAMOND -> "Diamant";
            case IRON_INGOT -> "Eisenbarren";
            case GOLD_INGOT -> "Goldbarren";
            case COPPER_INGOT -> "Kupferbarren";
            case NETHERITE_INGOT -> "Netheritbarren";
            case EMERALD -> "Smaragd";
            case COAL -> "Kohle";
            case REDSTONE -> "Redstone";
            case LAPIS_LAZULI -> "Lapislazuli";
            case QUARTZ -> "Nether-Quarz";
            
            // Holz
            case OAK_LOG -> "Eichenholz";
            case BIRCH_LOG -> "Birkenholz";
            case SPRUCE_LOG -> "Fichtenholz";
            case JUNGLE_LOG -> "Dschungelholz";
            case ACACIA_LOG -> "Akazienholz";
            case DARK_OAK_LOG -> "Schwarzeichenholz";
            
            // Stein
            case STONE -> "Stein";
            case COBBLESTONE -> "Kopfsteinpflaster";
            case GRANITE -> "Granit";
            case DIORITE -> "Diorit";
            case ANDESITE -> "Andesit";
            case DEEPSLATE -> "Tiefenschiefer";
            
            // Werkzeuge und Waffen
            case DIAMOND_SWORD -> "Diamantschwert";
            case IRON_SWORD -> "Eisenschwert";
            case DIAMOND_PICKAXE -> "Diamantspitzhacke";
            case IRON_PICKAXE -> "Eisenspitzhacke";
            
            // Standard-Formatierung für nicht übersetzte Items
            default -> formatEnumName(material.name());
        };
    }
    
    /**
     * Formatiert einen Enum-Namen zu einem lesbaren String
     * 
     * @param enumName Der Enum-Name (z.B. "COOKED_BEEF")
     * @return Formatierter Name (z.B. "Cooked Beef")
     */
    public static String formatEnumName(String enumName) {
        String[] words = enumName.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                result.append(" ");
            }
            if (!words[i].isEmpty()) {
                result.append(Character.toUpperCase(words[i].charAt(0)))
                      .append(words[i].substring(1));
            }
        }
        
        return result.toString();
    }
    
    /**
     * Berechnet die Standardabweichung einer Zahlenreihe
     * 
     * @param values Array von Werten
     * @return Standardabweichung
     */
    public static double standardDeviation(double[] values) {
        if (values.length == 0) return 0.0;
        
        double mean = average(values);
        double sumSquaredDiffs = 0.0;
        
        for (double value : values) {
            double diff = value - mean;
            sumSquaredDiffs += diff * diff;
        }
        
        return Math.sqrt(sumSquaredDiffs / values.length);
    }
    
    /**
     * Berechnet den Durchschnitt einer Zahlenreihe
     * 
     * @param values Array von Werten
     * @return Durchschnitt
     */
    public static double average(double[] values) {
        if (values.length == 0) return 0.0;
        
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        
        return sum / values.length;
    }
    
    /**
     * Überprüft ob ein String eine gültige Zahl darstellt
     * 
     * @param str Der zu prüfende String
     * @return true wenn gültige Zahl, false andernfalls
     */
    public static boolean isValidNumber(String str) {
        if (str == null || str.trim().isEmpty()) {
            return false;
        }
        
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Konvertiert Millisekunden zu einer lesbaren Zeitangabe
     * 
     * @param milliseconds Die Millisekunden
     * @return Lesbare Zeitangabe (z.B. "2m 30s")
     */
    public static String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return days + "d " + (hours % 24) + "h";
        } else if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m";
        } else if (minutes > 0) {
            return minutes + "m " + (seconds % 60) + "s";
        } else {
            return seconds + "s";
        }
    }
    
    /**
     * Berechnet die Inflation/Deflation basierend auf Preisänderungen
     * 
     * @param oldPrices Array alter Preise
     * @param newPrices Array neuer Preise
     * @return Inflationsrate (-1.0 bis +∞)
     */
    public static double calculateInflation(double[] oldPrices, double[] newPrices) {
        if (oldPrices.length != newPrices.length) {
            throw new IllegalArgumentException("Preis-Arrays müssen gleiche Länge haben");
        }
        
        double totalOld = 0.0;
        double totalNew = 0.0;
        
        for (int i = 0; i < oldPrices.length; i++) {
            totalOld += oldPrices[i];
            totalNew += newPrices[i];
        }
        
        return calculatePercentageChange(totalOld, totalNew);
    }
} 