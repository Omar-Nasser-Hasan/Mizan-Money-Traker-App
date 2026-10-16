# M0 spike verdicts

All figures below must be re-run on the target phone before a provider or model becomes product code. The Dev menu is hidden behind a long press on the `More` tab title.

## SPIKE-G1 — Egyptian dealer gold prices

**What worked.** `https://getdaleelak.com/api/v1/feed.json` is a public JSON feed with timestamped, per-direction dealer prices for `GOLD24K-EGP`, `GOLD21K-EGP`, `GOLD18K-EGP`, and `GOLDPOUND-EGP`. It names individual sellers, including iSagha, and the debug `DaleelakGoldPriceProvider` reads the best buy/sell value for each requested asset.

**Terms and attribution.** The feed says it is free to use and that a link back to Daleelak is appreciated. Its terms say rates are indicative, for general information only, may be delayed or inaccurate, and must be confirmed with the seller before acting; it also prohibits scraping its pages or ignoring rate limits. Attribution is **not yet product-ready**: the debug probe displays the provider name but does not provide a clickable link. Any product integration must display `Daleelak (getdaleelak.com)` with a link and the indicative-rate disclaimer.

**Update cadence.** The API documents that prices change at most about once an hour, refresh through the day, and recommends a cached request about every five minutes with ETag revalidation. The debug spike intentionally has no cache; that is acceptable only for a probe.

**Latency.** Desktop fetch on 2026-10-16 returned HTTP 200 in under one second; handset latency is not yet recorded. The Dev probe displays the source timestamp and has no cache, by design.

**Failure modes.** This is a third-party aggregation rather than iSagha's official API; seller selection may differ by karat, data can become stale, the JSON schema can change, connectivity can fail, rate limiting can return HTTP 429, and it is not a tradeable quote. When the feed is down, the debug provider returns no snapshot and the probe shows a failure state; it neither invents a value nor persists one.

**Recommendation.** Conditional pass for continued evaluation: use as a best-effort dealer aggregation behind `GoldPriceProvider`, with visible attribution, a two-hour cache, stale UI, and manual fallback. A product outage path must retain the last known price with its timestamp/stale badge and offer manual entry. Do not call it a guaranteed iSagha quote.

## SPIKE-S1 — EGX quotes

**What worked.** The public Swagger document at `https://ticker.egidegypt.com/swagger/v1/swagger.json` exposes a delayed market-watch operation. `EgiTickerQuoteProvider` probes COMI, TMGH, and SWDY and returns an empty list on every failure, preserving the manual/last-price path.

**Latency.** The documentation endpoint returned HTTP 200 in under one second on 2026-10-16. The unauthenticated symbol request returned HTTP 401, so no quote latency exists.

**Failure modes.** The advertised delayed operation requires authorization in the observed environment; fields and access rules may change, market hours apply, and an empty response must never block the UI.

**Recommendation.** Fail. Keep manual prices as the M7 default. Re-evaluate only with a documented, durable free tier or an explicit user-provided API key; never silently substitute a scraped quote.

## SPIKE-V1 — on-device speech recognition

**What worked.** The Dev probe uses `SpeechRecognizer.createOnDeviceSpeechRecognizer`, checks recognition support on Android 13+ and requests model download where the Android 14+ API is available, then records the first transcript and elapsed time from start-listening to result/error for `ar-EG` and `en-US`.

**Latency.** Pending the target handset and the 20 user-supplied spoken sentences. The app shows milliseconds for every trial; copy each transcript and time into this section.

**Failure modes.** No recognition service, unavailable on-device recognizer, language pack absent, model download scheduling/error, no-match, or poor Egyptian Arabic transcription. The probe explicitly requests offline recognition and never falls back online.

**Recommendation.** Inconclusive pending 20 real trials. Do not ship voice entry yet. If ar-EG has poor offline accuracy, compare whisper.cpp base as specified, including size and latency.

## SPIKE-L1 — on-device category choice models

**What worked.** No model has been integrated. The Dev probe captures a reproducible protocol: same 40 categories, 10 warmups, then 20 measured picks; capture p50/p95 wall time and PSS with Android Studio Profiler for one small multilingual embedding model and one LiteRT-LM model.

**Latency and RAM.** Pending target handset, model artifacts, and profiling run. Record model name/version, size, device, p50, p95, peak PSS delta, and whether the UI janked.

**Failure modes.** Unsupported GPU/NNAPI path, RAM pressure, model download/storage limits, tokenizer/model mismatch, cold-start outliers, and p95 over the 3-second gate.

**Recommendation.** Inconclusive. Do not integrate either candidate. Recommend only a candidate that meets p95 under three seconds with no UI jank; otherwise retain the deterministic parser alone.
