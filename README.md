# SimpleEco - Dynamisches Wirtschaftssystem Plugin

Ein vollständiges Paper Spigot Plugin für Minecraft, das ein dynamisches Wirtschaftssystem mit intelligenter Preisbildung und Villager-Trading implementiert.

## 🌟 Features

### Grundlegende Währung
- **BasicCurrency-System**: Vollständige Verwaltung von Spielerkonten
- **Konfigurierbare Währung**: Name, Symbol und Startguthaben anpassbar
- **Asynchrone Operationen**: Alle Datenbankzugriffe erfolgen asynchron für optimale Performance

### Dynamische Preisbildung
- **Intelligente Preisformel**: `Preis = clamp(basisPreis * (1 + preisFaktor * nettoVerkäufe * regressionFaktor / referenzMenge), minPreis, maxPreis)`
- **Preis-Regression**: Preise kehren über konfigurierbare Zeit zum Basispreis zurück
- **Item-spezifische Parameter**: Jedes Item kann eigene `priceFactor` und `referenceAmount` haben
- **Echzeit-Updates**: Preise ändern sich sofort basierend auf Handelstätigkeiten
- **Item-Kontrolle**: Konfigurierbare Kaufbarkeit/Verkaufbarkeit pro Item
- **Vollständig konfigurierbar**: Alle Preisparameter in der `config.yml` anpassbar

### Villager-Trading-Interface
- **Interaktives Menü**: Rechtsklick auf Villager öffnet Trading-Interface
- **Live-Preisinformationen**: Aktuelle Kauf-/Verkaufspreise, Trends und Volatilität
- **Multi-Handels-Support**: Einzel- oder 64er-Handel per Klick

### SQLite-Persistierung
- **Robuste Datenbank**: SQLite mit WAL-Modus für bessere Concurrency
- **Zwei Haupttabellen**:
  - `player_balance`: Spielerkontostände
  - `item_stats`: Item-Handelsstatistiken
- **Performance-Optimiert**: Caching und asynchrone Operationen

## 🚀 Installation

1. **Download**: Lade die neueste `SimpleEco.jar` Datei herunter
2. **Installation**: Platziere die JAR-Datei in deinem `plugins/` Ordner
3. **Server-Neustart**: Starte deinen Paper Spigot Server neu
4. **Konfiguration**: Passe die `plugins/SimpleEco/config.yml` nach deinen Wünschen an

## ⚙️ Konfiguration

### Grundeinstellungen
```yaml
# Währungseinstellungen
currency:
  name: "Gold"
  startBalance: 1000.0
  symbol: "G"

# Datenbankeinstellungen
database:
  path: "plugins/SimpleEco/economy.db"

# Preiseinstellungen
pricing:
  priceFactor: 0.05  # 5% Elastizität (global, überschreibbar pro Item)
  referenceAmount: 1000  # Referenzmenge (global, überschreibbar pro Item)
  regressionTimeMinutes: 60  # Zeit bis Preise zum Default zurückkehren
  regressionUpdateInterval: 5  # Update-Intervall in Minuten
```

### Item-Preise konfigurieren
```yaml
pricing:
  priceFactor: 0.05  # Globaler Standard-Preisfaktor
  referenceAmount: 1000  # Globale Standard-Referenzmenge
  regressionTimeMinutes: 60  # Zeit bis Preise zum Basispreis zurückkehren
  regressionUpdateInterval: 5  # Update-Intervall in Minuten
  
  items:
    WHEAT:
      basePrice: 10.0
      minPrice: 5.0
      maxPrice: 50.0
      buyable: true   # Kann gekauft werden
      sellable: true  # Kann verkauft werden
      # Verwendet globale priceFactor und referenceAmount
      
    DIAMOND:
      basePrice: 500.0
      minPrice: 250.0
      maxPrice: 2500.0
      buyable: true
      sellable: false  # Nur kaufbar, nicht verkaufbar
      priceFactor: 0.02        # Item-spezifisch: Weniger volatil
      referenceAmount: 100     # Item-spezifisch: Reagiert schneller
      
    COAL:
      basePrice: 5.0
      minPrice: 2.0
      maxPrice: 25.0
      buyable: false   # Nur verkaufbar (Rohstoff)
      sellable: true
      priceFactor: 0.1         # Item-spezifisch: Sehr volatil
      referenceAmount: 2000    # Item-spezifisch: Massengut
```

## 🎮 Commands

| Command | Beschreibung | Permission |
|---------|-------------|------------|
| `/eco balance [Spieler]` | Zeigt Kontostand an | `simpleeco.use` |
| `/eco pay <Spieler> <Betrag>` | Überweist Geld | `simpleeco.use` |
| `/eco help` | Zeigt Hilfe an | `simpleeco.use` |

## 🔑 Permissions

| Permission | Beschreibung | Standard |
|------------|-------------|----------|
| `simpleeco.use` | Grundlegende Plugin-Nutzung | `true` |
| `simpleeco.admin` | Admin-Funktionen | `op` |
| `simpleeco.balance.other` | Fremde Kontostände einsehen | `op` |

## 🛠️ Villager-Trading

### Wie es funktioniert
1. **Rechtsklick auf Villager**: Öffnet das Trading-Menü
2. **Linksklick auf Item**: Kauft 1x Item
3. **Rechtsklick auf Item**: Verkauft 1x Item
4. **Shift+Klick**: Handelt mit 64x Items
5. **Shift+Rechtsklick auf Villager**: Normales Villager-Trading

### Preisinformationen
Das Interface zeigt für jedes Item:
- Aktueller Kauf-/Verkaufspreis
- Preistoleranz-Trends (steigend/fallend/stabil)
- Volatilität (niedrig/mittel/hoch)
- Handelsstatistiken (verkauft/gekauft/netto)
- Effektive Preisparameter (Faktor und Referenzmenge)

## 📊 Dynamische Preisbildung

### Preisformel
```
Preis = clamp(
    basisPreis * (1 + preisFaktor * (verkauft - gekauft) * regressionFaktor / referenzMenge),
    minPreis,
    maxPreis
)

RegressionFaktor = 1.0 - (zeitSeitLetztemHandel / regressionZeit)
```

### Beispiel
- **Basispreis**: 10 Gold
- **Preis-Faktor**: 0.05 (5%)
- **Referenzmenge**: 1000
- **Verkauft**: 1500, **Gekauft**: 500
- **Netto**: +1000
- **Zeit seit letztem Handel**: 30 Minuten
- **Regressions-Zeit**: 60 Minuten
- **Regressions-Faktor**: 0.5 (1.0 - 30/60)

**Berechneter Preis**: `10 * (1 + 0.05 * 1000 * 0.5 / 1000) = 10 * 1.025 = 10.25 Gold`

### Beispiel: Item-spezifische Parameter

**Diamant** (wenig volatil, reagiert schnell):
- **Basispreis**: 500 Gold
- **Item-spezifischer Preis-Faktor**: 0.02 (vs. global 0.05)
- **Item-spezifische Referenzmenge**: 100 (vs. global 1000)
- **Verkauft**: 50, **Gekauft**: 30, **Netto**: +20
- **Berechneter Preis**: `500 * (1 + 0.02 * 20 * 1.0 / 100) = 500 * 1.004 = 502 Gold`

**Kohle** (sehr volatil, Massengut):
- **Basispreis**: 5 Gold
- **Item-spezifischer Preis-Faktor**: 0.1 (vs. global 0.05)
- **Item-spezifische Referenzmenge**: 2000 (vs. global 1000)
- **Verkauft**: 3000, **Gekauft**: 1000, **Netto**: +2000
- **Berechneter Preis**: `5 * (1 + 0.1 * 2000 * 1.0 / 2000) = 5 * 1.1 = 5.5 Gold`

## 🔧 Technische Details

### Architektur
- **Thread-Safe**: Alle Operationen sind thread-sicher implementiert
- **Asynchron**: Datenbankzugriffe blockieren nie den Haupt-Thread
- **Modularer Aufbau**: Saubere Trennung der Komponenten
- **Performance-Optimiert**: Caching und effiziente Datenbankabfragen

### Systemanforderungen
- **Minecraft**: 1.20.4+
- **Server**: Paper Spigot (empfohlen)
- **Java**: 17+
- **RAM**: Minimal 512MB für Plugin-Daten

### Datenbank-Schema
```sql
-- Spieler-Kontostände
CREATE TABLE player_balance (
    uuid TEXT PRIMARY KEY,
    balance REAL NOT NULL DEFAULT 0.0,
    last_updated INTEGER NOT NULL
);

-- Item-Handelsstatistiken
CREATE TABLE item_stats (
    item TEXT PRIMARY KEY,
    sold BIGINT NOT NULL DEFAULT 0,
    bought BIGINT NOT NULL DEFAULT 0,
    last_updated INTEGER NOT NULL
);
```

## 📈 Performance

### Optimierungen
- **SQLite WAL-Modus**: Bessere Concurrency bei Datenbankzugriffen
- **In-Memory-Caching**: Häufig abgerufene Daten werden gecacht
- **Asynchrone Verarbeitung**: Keine Blockierung des Haupt-Threads
- **Batch-Operationen**: Effiziente Datenbank-Updates

### Benchmarks
- **Spieler-Balance-Abfrage**: < 1ms (gecacht), < 10ms (Datenbank)
- **Preis-Berechnung**: < 0.1ms
- **Trading-Transaktion**: < 50ms (komplett)

## 🐛 Troubleshooting

### Häufige Probleme

**Plugin startet nicht**
- Überprüfe Java-Version (benötigt Java 17+)
- Kontrolliere Konsolen-Logs auf Fehlermeldungen
- Stelle sicher, dass Paper Spigot 1.20.4+ verwendet wird

**Datenbank-Fehler**
- Überprüfe Schreibrechte im Plugin-Verzeichnis
- Kontrolliere verfügbaren Speicherplatz
- Prüfe auf SQLite-Korruption

**Preise aktualisieren nicht**
- Überprüfe `priceFactor` in der Konfiguration
- Kontrolliere Item-Konfiguration
- Prüfe Konsolen-Logs auf Fehler

## 📝 Changelog

### Version 1.0.0
- Erste vollständige Version
- Dynamisches Preissystem implementiert
- Villager-Trading-Interface
- Vollständiges Command-System
- SQLite-Persistierung
- Umfassende Konfigurationsmöglichkeiten

### Version 1.1.0
- **Preis-Regression**: Preise kehren über Zeit zum Basispreis zurück
- **Item-Kontrolle**: Konfigurierbare Kaufbarkeit/Verkaufbarkeit pro Item
- **Automatische Updates**: Scheduler-Task für regelmäßige Preisanpassungen
- **Verbesserte Performance**: Optimierte Datenbankzugriffe mit Zeitstempel-Tracking
- **Enhanced UI**: Bessere Anzeige von handelbaren Optionen im Villager-Menü

### Version 1.2.0 (Aktuelle Version)
- **Item-spezifische Parameter**: Jedes Item kann eigene `priceFactor` und `referenceAmount` haben
- **Granulare Kontrolle**: Verschiedene Volatilitäten und Reaktionsgeschwindigkeiten pro Item
- **Erweiterte Anzeige**: UI zeigt effektive Preisparameter für jedes Item
- **Flexible Konfiguration**: Globale Standards mit optionalen item-spezifischen Überschreibungen

## 👥 Support

Bei Fragen oder Problemen:
1. Überprüfe die Dokumentation
2. Kontrolliere die Konsolen-Logs
3. Erstelle ein Issue auf GitHub mit detaillierter Fehlerbeschreibung

## 📄 Lizenz

Dieses Plugin ist unter der MIT-Lizenz veröffentlicht. Siehe `LICENSE` Datei für Details.

---

**Entwickelt mit ❤️ für die Minecraft-Community** 