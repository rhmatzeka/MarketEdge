# MarketEdge

An Android app for following markets and financial news, in the style of Investing.com. It shows live crypto, forex, and gold prices, detailed charts, the latest headlines, your watchlist, and **WarrenAI**, a chat assistant that answers questions using the market data and news the app just loaded.

## Features

- **Markets**: a compact, fast price list (the tab labels are in Indonesian: Pasar, Berita, Ide, Watchlist, Lainnya)
- **Instrument details**: a real price chart with time ranges, plus Overview, Technical, News, Analysis, and Data tabs
- **Key stats**: bid/ask, daily range, 52-week range, previous close, open, 24h volume, market cap, rank, and circulating supply
- **News**: latest headlines with a detail page, image, share button, link to the source, and an "ask the AI about this" option
- **Watchlist** with an edit mode and an asset picker
- **WarrenAI chat** that works even without an API key (see below)
- Dark theme with orange accents and an animated splash screen

## Where the data comes from

All feeds are public and free:

| Data | Source |
| --- | --- |
| Crypto prices, chart history, and market stats | CoinGecko |
| Crypto bid/ask | Coinbase public order book |
| Forex, gold, and silver | A public currency feed |
| News headlines | Spaceflight News API |

If a feed is down, the app shows a loading, error, or retry state and falls back to data it already loaded.

## Tech stack

Kotlin, Android Views built in code, Material Components, a light clean-architecture layout (`data`, `domain`, `presentation`), a custom `SparklineView` for charts, and `HttpURLConnection` + `org.json` for networking (no extra libraries).

## Build and run

Open the project in Android Studio, or build from the command line:

```bash
./gradlew :app:assembleDebug
```

The debug APK is saved to `app/build/outputs/apk/debug/app-debug.apk`.

## Turn on the online AI (optional)

1. Copy `.env.example` to `.env`.
2. Add a free Groq API key from https://console.groq.com/keys:

   ```env
   AI_API_KEY=your_groq_key
   AI_BASE_URL=https://api.groq.com/openai/v1/chat/completions
   AI_MODEL=llama-3.1-8b-instant
   ```

3. Rebuild the app.

Without `AI_API_KEY`, WarrenAI still works: it gives a simple local analysis based on the loaded prices and news.

## Project structure

```text
app/src/main/java/id/rahmat/marketedge/
├── data/            API clients and repositories
├── domain/          Models, repository interfaces, and use cases
├── presentation/    Screens and UI components
├── MainActivity.kt
└── MarketEdgeApplication.kt
```

## Notes

The UI is built in code (no XML layouts) to keep this early version simple and stable. Retrofit, Room, Paging, or a chart library could be added later.
