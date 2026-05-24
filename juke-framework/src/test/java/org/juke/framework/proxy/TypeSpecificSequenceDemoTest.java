package org.juke.framework.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.juke.framework.config.ConfigUtil;
import org.juke.framework.storage.JukeHelper;
import org.juke.framework.storage.JukeZipDAOImpl;
import org.juke.framework.storage.ZipUtil;
import org.juke.framework.metadata.JukeClass;
import org.juke.framework.metadata.JukeConfigBuilder;
import org.junit.jupiter.api.*;

import java.io.File;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ==================================================================================
 * SAMPLE CASE: Type-Specific Sequence -- Record and Playback Demo
 * ==================================================================================
 *
 * This test models a real-world scenario inspired by Spring's RestTemplate:
 *
 *     restTemplate.getForEntity(url, String.class)        -> returns a String body
 *     restTemplate.getForEntity(url, WeatherReport.class) -> returns a WeatherReport body
 *     restTemplate.getForEntity(url, StockQuote.class)    -> returns a StockQuote body
 *
 * The test follows a two-phase approach modeled after an actual integration test workflow:
 *
 *   PHASE 1 (RECORD):  Calls the real service through a Juke recording proxy.
 *                       Each call's return value is captured in a ZIP alongside
 *                       a type discriminator derived from the Class argument.
 *
 *   PHASE 2 (PLAYBACK): Replays the same calls through a Juke replay proxy.
 *                        Verifies that each replayed result has the correct
 *                        concrete Java type -- not a raw LinkedHashMap.
 *
 * The calls are intentionally interleaved (String, WeatherReport, StockQuote,
 * String again, WeatherReport again) to prove that type-specific sequencing
 * tracks each responseType independently.
 * ==================================================================================
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TypeSpecificSequenceDemoTest {

    // --- Domain Objects --------------------------------------------------

    /** Simulates a weather API response */
    public static class WeatherReport {
        private String city;
        private double temperatureF;
        private String condition;

        public WeatherReport() {}
        public WeatherReport(String city, double temperatureF, String condition) {
            this.city = city;
            this.temperatureF = temperatureF;
            this.condition = condition;
        }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public double getTemperatureF() { return temperatureF; }
        public void setTemperatureF(double temperatureF) { this.temperatureF = temperatureF; }
        public String getCondition() { return condition; }
        public void setCondition(String condition) { this.condition = condition; }
        @Override public String toString() {
            return "WeatherReport{city='" + city + "', temp=" + temperatureF + "F, condition='" + condition + "'}";
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            WeatherReport that = (WeatherReport) o;
            return Double.compare(that.temperatureF, temperatureF) == 0
                    && Objects.equals(city, that.city) && Objects.equals(condition, that.condition);
        }
    }

    /** Simulates a stock API response */
    public static class StockQuote {
        private String symbol;
        private double price;
        private String exchange;

        public StockQuote() {}
        public StockQuote(String symbol, double price, String exchange) {
            this.symbol = symbol;
            this.price = price;
            this.exchange = exchange;
        }
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
        public String getExchange() { return exchange; }
        public void setExchange(String exchange) { this.exchange = exchange; }
        @Override public String toString() {
            return "StockQuote{symbol='" + symbol + "', price=" + price + ", exchange='" + exchange + "'}";
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StockQuote that = (StockQuote) o;
            return Double.compare(that.price, price) == 0
                    && Objects.equals(symbol, that.symbol) && Objects.equals(exchange, that.exchange);
        }
    }

    // --- Service Interface (mirrors RestTemplate-like API) ----------------

    /**
     * A simplified interface that mirrors RestTemplate.getForEntity:
     * the SAME method name, returning Object, but the actual type depends
     * on the Class argument passed at runtime.
     */
    public interface IDataService {
        /** Fetch data from an endpoint -- the responseType determines the return type */
        Object fetchData(String endpoint, Class<?> responseType);
    }

    // --- Service Implementation (simulates real backend responses) --------

    public static class DataServiceImpl implements IDataService {
        @Override
        public Object fetchData(String endpoint, Class<?> responseType) {
            if (responseType == String.class) {
                return "raw-response-from-" + endpoint;
            } else if (responseType == WeatherReport.class) {
                if (endpoint.contains("nyc"))
                    return new WeatherReport("New York", 72.5, "Partly Cloudy");
                else
                    return new WeatherReport("London", 55.0, "Rainy");
            } else if (responseType == StockQuote.class) {
                if (endpoint.contains("MSFT"))
                    return new StockQuote("MSFT", 425.50, "NASDAQ");
                else
                    return new StockQuote("AAPL", 198.75, "NASDAQ");
            }
            return "unknown-type-response";
        }
    }

    // --- Test Infrastructure ----------------------------------------------

    static ObjectMapper objectMapper = new ObjectMapper();
    static {
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    private static final String ZIP_NAME = "type-specific-sequence-demo";
    private static String jukePath;
    private static String recordedZipPath;

    @BeforeAll
    static void setup() {
        jukePath = ConfigUtil.getDefauljukePath();
    }

    @AfterAll
    static void teardown() {
        if (recordedZipPath != null) {
            new File(recordedZipPath).delete();
            new File(recordedZipPath + ".bak").delete();
        }
    }

    // =====================================================================
    //  THE TEST: Record 5 calls, then Replay 5 calls, verify all types
    // =====================================================================

    @Test
    @Order(1)
    @DisplayName("Record and Playback: 5 interleaved calls with 3 different responseTypes")
    void recordThenPlayback_interleavedTypes() throws Exception {

        IDataService realService = new DataServiceImpl();

        // -----------------------------------------------------------------
        //  PHASE 1: RECORD
        // -----------------------------------------------------------------
        System.out.println("===================================================");
        System.out.println("  PHASE 1: RECORDING");
        System.out.println("===================================================");

        JukeState.setGlobaljuke(JukeState.RECORD);
        System.setProperty("juke", JukeState.RECORD);
        System.setProperty("juke.path", jukePath);
        System.setProperty("juke.zip", ZIP_NAME);
        JukeHelper.setJukeDao(new JukeZipDAOImpl(jukePath, ZIP_NAME));
        JukeNameFormatter.clearMappings();

        JukeClass jukeClass = JukeConfigBuilder.set(IDataService.class).build();
        HashMap<String, JukeClass> jukeMap = new HashMap<>();
        jukeMap.put(IDataService.class.getCanonicalName(), jukeClass);

        IDataService recorder = new JukeFactory<IDataService>()
                .newInstance(realService, IDataService.class, JukeState.RECORD);

        // -- Call 1: String response --
        Object r1 = recorder.fetchData("/api/health", String.class);
        System.out.println("  [1] RECORD fetchData(/api/health, String.class)");
        System.out.println("       -> " + r1.getClass().getSimpleName() + ": " + r1);
        assertTrue(r1 instanceof String);

        // -- Call 2: WeatherReport response (NYC) --
        Object r2 = recorder.fetchData("/api/weather/nyc", WeatherReport.class);
        System.out.println("  [2] RECORD fetchData(/api/weather/nyc, WeatherReport.class)");
        System.out.println("       -> " + r2.getClass().getSimpleName() + ": " + r2);
        assertTrue(r2 instanceof WeatherReport);

        // -- Call 3: StockQuote response (MSFT) --
        Object r3 = recorder.fetchData("/api/stock/MSFT", StockQuote.class);
        System.out.println("  [3] RECORD fetchData(/api/stock/MSFT, StockQuote.class)");
        System.out.println("       -> " + r3.getClass().getSimpleName() + ": " + r3);
        assertTrue(r3 instanceof StockQuote);

        // -- Call 4: String response (different endpoint) --
        Object r4 = recorder.fetchData("/api/version", String.class);
        System.out.println("  [4] RECORD fetchData(/api/version, String.class)");
        System.out.println("       -> " + r4.getClass().getSimpleName() + ": " + r4);
        assertTrue(r4 instanceof String);

        // -- Call 5: WeatherReport response (London) --
        Object r5 = recorder.fetchData("/api/weather/london", WeatherReport.class);
        System.out.println("  [5] RECORD fetchData(/api/weather/london, WeatherReport.class)");
        System.out.println("       -> " + r5.getClass().getSimpleName() + ": " + r5);
        assertTrue(r5 instanceof WeatherReport);

        // Flush recordings to ZIP
        JukeHelper.getJukeDAO().writeToFile("juke", objectMapper.writeValueAsString(jukeMap));
        JukeHelper.getJukeDAO().write();
        recordedZipPath = JukeHelper.getJukeDAO().path();

        System.out.println();
        System.out.println("  ZIP written: " + recordedZipPath);

        // -- Inspect the ZIP to show type-discriminated entries --
        assertTrue(new File(recordedZipPath).exists());
        Set<String> entries = ZipUtil.getFileNamesFromZipFile(recordedZipPath);
        System.out.println("  ZIP contents (" + entries.size() + " entries):");
        TreeSet<String> sorted = new TreeSet<>(entries);
        for (String e : sorted) {
            System.out.println("    " + e);
        }

        // Verify type discriminators are present in short-name format
        long stringEntries = entries.stream()
                .filter(e -> e.contains("@String") && !e.contains(".type.") && !e.contains(".args.") && !e.contains("juke")).count();
        long weatherEntries = entries.stream()
                .filter(e -> e.contains("@WeatherReport") && !e.contains(".type.") && !e.contains(".args.") && !e.contains("juke")).count();
        long stockEntries = entries.stream()
                .filter(e -> e.contains("@StockQuote") && !e.contains(".type.") && !e.contains(".args.") && !e.contains("juke")).count();

        System.out.println();
        System.out.println("  String entries:        " + stringEntries);
        System.out.println("  WeatherReport entries: " + weatherEntries);
        System.out.println("  StockQuote entries:    " + stockEntries);

        assertEquals(2, stringEntries, "Should have 2 String recordings");
        assertEquals(2, weatherEntries, "Should have 2 WeatherReport recordings");
        assertEquals(1, stockEntries, "Should have 1 StockQuote recording");

        // -----------------------------------------------------------------
        //  PHASE 2: PLAYBACK
        // -----------------------------------------------------------------
        System.out.println();
        System.out.println("===================================================");
        System.out.println("  PHASE 2: PLAYBACK");
        System.out.println("===================================================");

        JukeState.setGlobaljuke(JukeState.REPLAY);
        System.setProperty("juke", JukeState.REPLAY);

        File zipFile = new File(recordedZipPath);
        String parentDir = zipFile.getParent();
        String zipFileName = zipFile.getName().replace(".zip", "");
        System.setProperty("juke.path", parentDir);
        System.setProperty("juke.zip", zipFileName);
        JukeHelper.setJukeDao(new JukeZipDAOImpl(parentDir, zipFileName));

        JukeConfigBuilder.set(IDataService.class).build();

        IDataService player = new JukeFactory<IDataService>()
                .newInstance(realService, IDataService.class, JukeState.REPLAY);

        // -- Replay Call 1: String --
        Object p1 = player.fetchData("/api/health", String.class);
        System.out.println("  [1] REPLAY fetchData(String.class)");
        System.out.println("       -> type=" + (p1 != null ? p1.getClass().getSimpleName() : "null") + ": " + p1);
        assertNotNull(p1, "Replay call 1 must not be null");
        assertTrue(p1 instanceof String, "Replay 1: expected String, got " + p1.getClass().getName());
        assertEquals(r1, p1, "Replay 1 value should match recorded value");

        // -- Replay Call 2: WeatherReport (NYC) --
        Object p2 = player.fetchData("/api/weather/nyc", WeatherReport.class);
        System.out.println("  [2] REPLAY fetchData(WeatherReport.class)");
        System.out.println("       -> type=" + (p2 != null ? p2.getClass().getSimpleName() : "null") + ": " + p2);
        assertNotNull(p2, "Replay call 2 must not be null");
        assertTrue(p2 instanceof WeatherReport,
                "Replay 2: expected WeatherReport, got " + p2.getClass().getName());
        WeatherReport wr1 = (WeatherReport) p2;
        assertEquals("New York", wr1.getCity());
        assertEquals(72.5, wr1.getTemperatureF(), 0.01);
        assertEquals("Partly Cloudy", wr1.getCondition());

        // -- Replay Call 3: StockQuote (MSFT) --
        Object p3 = player.fetchData("/api/stock/MSFT", StockQuote.class);
        System.out.println("  [3] REPLAY fetchData(StockQuote.class)");
        System.out.println("       -> type=" + (p3 != null ? p3.getClass().getSimpleName() : "null") + ": " + p3);
        assertNotNull(p3, "Replay call 3 must not be null");
        assertTrue(p3 instanceof StockQuote,
                "Replay 3: expected StockQuote, got " + p3.getClass().getName());
        StockQuote sq1 = (StockQuote) p3;
        assertEquals("MSFT", sq1.getSymbol());
        assertEquals(425.50, sq1.getPrice(), 0.01);
        assertEquals("NASDAQ", sq1.getExchange());

        // -- Replay Call 4: String (2nd String call) --
        Object p4 = player.fetchData("/api/version", String.class);
        System.out.println("  [4] REPLAY fetchData(String.class)");
        System.out.println("       -> type=" + (p4 != null ? p4.getClass().getSimpleName() : "null") + ": " + p4);
        assertNotNull(p4, "Replay call 4 must not be null");
        assertTrue(p4 instanceof String, "Replay 4: expected String, got " + p4.getClass().getName());
        assertEquals(r4, p4, "Replay 4 value should match recorded value");

        // -- Replay Call 5: WeatherReport (London, 2nd WeatherReport call) --
        Object p5 = player.fetchData("/api/weather/london", WeatherReport.class);
        System.out.println("  [5] REPLAY fetchData(WeatherReport.class)");
        System.out.println("       -> type=" + (p5 != null ? p5.getClass().getSimpleName() : "null") + ": " + p5);
        assertNotNull(p5, "Replay call 5 must not be null");
        assertTrue(p5 instanceof WeatherReport,
                "Replay 5: expected WeatherReport, got " + p5.getClass().getName());
        WeatherReport wr2 = (WeatherReport) p5;
        assertEquals("London", wr2.getCity());
        assertEquals(55.0, wr2.getTemperatureF(), 0.01);
        assertEquals("Rainy", wr2.getCondition());

        // -----------------------------------------------------------------
        //  SUMMARY
        // -----------------------------------------------------------------
        System.out.println();
        System.out.println("===================================================");
        System.out.println("  RESULTS SUMMARY");
        System.out.println("===================================================");
        System.out.println("  Call 1: String        -> RECORD=" + r1.getClass().getSimpleName()
                + "  REPLAY=" + p1.getClass().getSimpleName() + "  MATCH=" + r1.equals(p1));
        System.out.println("  Call 2: WeatherReport -> RECORD=" + r2.getClass().getSimpleName()
                + "  REPLAY=" + p2.getClass().getSimpleName() + "  MATCH=" + r2.equals(p2));
        System.out.println("  Call 3: StockQuote    -> RECORD=" + r3.getClass().getSimpleName()
                + "  REPLAY=" + p3.getClass().getSimpleName() + "  MATCH=" + r3.equals(p3));
        System.out.println("  Call 4: String        -> RECORD=" + r4.getClass().getSimpleName()
                + "  REPLAY=" + p4.getClass().getSimpleName() + "  MATCH=" + r4.equals(p4));
        System.out.println("  Call 5: WeatherReport -> RECORD=" + r5.getClass().getSimpleName()
                + "  REPLAY=" + p5.getClass().getSimpleName() + "  MATCH=" + r5.equals(p5));
        System.out.println();
        System.out.println("  All 5 calls: correct types, correct values, correct sequence.");
        System.out.println("===================================================");
    }

    // =====================================================================
    //  TEST 2: Deduplication -- consecutive identical responses get compacted
    // =====================================================================

    /**
     * Service that returns the SAME data for repeated calls -- simulates
     * a currency service where getCurrency("USD") always returns the same quote.
     */
    public interface ICurrencyService {
        Object getCurrency(String code, Class<?> responseType);
    }

    public static class CurrencyResult {
        private String code;
        private double rate;

        public CurrencyResult() {}
        public CurrencyResult(String code, double rate) {
            this.code = code;
            this.rate = rate;
        }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public double getRate() { return rate; }
        public void setRate(double rate) { this.rate = rate; }
        @Override public String toString() {
            return "CurrencyResult{code='" + code + "', rate=" + rate + "}";
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CurrencyResult that = (CurrencyResult) o;
            return Double.compare(that.rate, rate) == 0 && Objects.equals(code, that.code);
        }
    }

    public static class CurrencyServiceImpl implements ICurrencyService {
        @Override
        public Object getCurrency(String code, Class<?> responseType) {
            if (responseType == CurrencyResult.class) {
                if ("USD".equals(code)) return new CurrencyResult("USD", 1.0);
                if ("EUR".equals(code)) return new CurrencyResult("EUR", 0.92);
                if ("GBP".equals(code)) return new CurrencyResult("GBP", 0.79);
            }
            return "rate-for-" + code;
        }
    }

    private static String deduplicatedZipPath;

    @Test
    @Order(2)
    @DisplayName("Deduplication: consecutive identical responses compacted to range notation")
    void deduplication_consecutiveIdenticalResponses() throws Exception {
        ICurrencyService realService = new CurrencyServiceImpl();

        // -----------------------------------------------------------------
        //  PHASE 1: RECORD -- 5 calls where first 3 return identical data
        // -----------------------------------------------------------------
        System.out.println("===================================================");
        System.out.println("  DEDUP TEST - PHASE 1: RECORDING");
        System.out.println("===================================================");

        String dedupZipName = "dedup-test";
        JukeState.setGlobaljuke(JukeState.RECORD);
        System.setProperty("juke", JukeState.RECORD);
        System.setProperty("juke.path", jukePath);
        System.setProperty("juke.zip", dedupZipName);
        JukeHelper.setJukeDao(new JukeZipDAOImpl(jukePath, dedupZipName));
        JukeNameFormatter.clearMappings();

        JukeConfigBuilder.set(ICurrencyService.class).build();

        ICurrencyService recorder = new JukeFactory<ICurrencyService>()
                .newInstance(realService, ICurrencyService.class, JukeState.RECORD);

        // Calls 1-3: identical USD response (should compact to [1-3])
        Object r1 = recorder.getCurrency("USD", CurrencyResult.class);
        Object r2 = recorder.getCurrency("USD", CurrencyResult.class);
        Object r3 = recorder.getCurrency("USD", CurrencyResult.class);
        System.out.println("  [1-3] RECORD getCurrency(USD) x3 -> " + r1);

        // Call 4: different response (EUR)
        Object r4 = recorder.getCurrency("EUR", CurrencyResult.class);
        System.out.println("  [4]   RECORD getCurrency(EUR)    -> " + r4);

        // Call 5: same as call 4 (EUR again -- but NOT consecutive with calls 1-3)
        Object r5 = recorder.getCurrency("EUR", CurrencyResult.class);
        System.out.println("  [5]   RECORD getCurrency(EUR)    -> " + r5);

        // Flush recordings
        JukeHelper.getJukeDAO().write();
        deduplicatedZipPath = JukeHelper.getJukeDAO().path();

        System.out.println();
        System.out.println("  ZIP written: " + deduplicatedZipPath);

        // -- Inspect ZIP entries --
        assertTrue(new File(deduplicatedZipPath).exists());
        Set<String> entries = ZipUtil.getFileNamesFromZipFile(deduplicatedZipPath);
        System.out.println("  ZIP contents (" + entries.size() + " entries):");
        TreeSet<String> sorted = new TreeSet<>(entries);
        for (String e : sorted) {
            System.out.println("    " + e);
        }

        // Verify range notation is present
        boolean hasRange13 = entries.stream().anyMatch(e -> e.contains("[1-3]") && e.contains("CurrencyResult"));
        boolean hasSingle4 = entries.stream().anyMatch(e ->
                e.contains("CurrencyResult") && !e.contains(".type.") && e.contains(".4."));
        // Calls 4 and 5 are also identical (both EUR), so they should compact to [4-5]
        boolean hasRange45 = entries.stream().anyMatch(e -> e.contains("[4-5]") && e.contains("CurrencyResult"));

        System.out.println();
        System.out.println("  Has [1-3] range entry: " + hasRange13);
        System.out.println("  Has [4-5] range entry: " + hasRange45);
        System.out.println("  Has single .4. entry:  " + hasSingle4);

        assertTrue(hasRange13, "Calls 1-3 (identical USD) should be compacted to [1-3]");
        assertTrue(hasRange45 || hasSingle4,
                "Calls 4-5 should either be compacted to [4-5] or exist as individual entries");

        // Count total data entries (excluding .type., .args. and metadata)
        long dataEntries = entries.stream()
                .filter(e -> e.contains("CurrencyResult") && !e.contains(".type.") && !e.contains(".args.")).count();
        System.out.println("  Total CurrencyResult data entries: " + dataEntries);
        // With compaction: [1-3] + [4-5] = 2 entries (down from 5)
        // or [1-3] + .4 + .5 = 3 entries (if 4 and 5 differ)
        assertTrue(dataEntries < 5, "Compaction should reduce entries from 5 to fewer");

        // -----------------------------------------------------------------
        //  PHASE 2: REPLAY -- verify all 5 calls return correct data
        // -----------------------------------------------------------------
        System.out.println();
        System.out.println("===================================================");
        System.out.println("  DEDUP TEST - PHASE 2: PLAYBACK");
        System.out.println("===================================================");

        JukeState.setGlobaljuke(JukeState.REPLAY);
        System.setProperty("juke", JukeState.REPLAY);

        File zipFile = new File(deduplicatedZipPath);
        String parentDir = zipFile.getParent();
        String zipFileName = zipFile.getName().replace(".zip", "");
        System.setProperty("juke.path", parentDir);
        System.setProperty("juke.zip", zipFileName);
        JukeHelper.setJukeDao(new JukeZipDAOImpl(parentDir, zipFileName));

        JukeConfigBuilder.set(ICurrencyService.class).build();

        ICurrencyService player = new JukeFactory<ICurrencyService>()
                .newInstance(realService, ICurrencyService.class, JukeState.REPLAY);

        // Replay all 5 calls
        Object p1 = player.getCurrency("USD", CurrencyResult.class);
        System.out.println("  [1] REPLAY -> " + (p1 != null ? p1.getClass().getSimpleName() : "null") + ": " + p1);
        assertNotNull(p1, "Replay 1 must not be null");
        assertTrue(p1 instanceof CurrencyResult, "Replay 1: expected CurrencyResult, got " + p1.getClass());

        Object p2 = player.getCurrency("USD", CurrencyResult.class);
        System.out.println("  [2] REPLAY -> " + (p2 != null ? p2.getClass().getSimpleName() : "null") + ": " + p2);
        assertNotNull(p2);
        assertTrue(p2 instanceof CurrencyResult);

        Object p3 = player.getCurrency("USD", CurrencyResult.class);
        System.out.println("  [3] REPLAY -> " + (p3 != null ? p3.getClass().getSimpleName() : "null") + ": " + p3);
        assertNotNull(p3);
        assertTrue(p3 instanceof CurrencyResult);

        // All 3 should be identical USD results
        assertEquals(r1, p1);
        assertEquals(r2, p2);
        assertEquals(r3, p3);

        Object p4 = player.getCurrency("EUR", CurrencyResult.class);
        System.out.println("  [4] REPLAY -> " + (p4 != null ? p4.getClass().getSimpleName() : "null") + ": " + p4);
        assertNotNull(p4);
        assertTrue(p4 instanceof CurrencyResult);
        assertEquals(r4, p4);

        Object p5 = player.getCurrency("EUR", CurrencyResult.class);
        System.out.println("  [5] REPLAY -> " + (p5 != null ? p5.getClass().getSimpleName() : "null") + ": " + p5);
        assertNotNull(p5);
        assertTrue(p5 instanceof CurrencyResult);
        assertEquals(r5, p5);

        // Call 6: BEYOND the recorded sequence -- should replay the last available entry (EUR)
        Object p6 = player.getCurrency("EUR", CurrencyResult.class);
        System.out.println("  [6] REPLAY (beyond sequence) -> "
                + (p6 != null ? p6.getClass().getSimpleName() : "null") + ": " + p6);
        assertNotNull(p6, "6th call should return last available entry, not null");
        assertTrue(p6 instanceof CurrencyResult, "6th call should still be CurrencyResult");
        assertEquals(r5, p6, "6th call should return same data as last recorded entry (EUR)");

        // Call 7: still beyond -- should keep returning the last entry
        Object p7 = player.getCurrency("EUR", CurrencyResult.class);
        System.out.println("  [7] REPLAY (beyond sequence) -> "
                + (p7 != null ? p7.getClass().getSimpleName() : "null") + ": " + p7);
        assertNotNull(p7);
        assertEquals(r5, p7, "7th call should also return last recorded entry");

        System.out.println();
        System.out.println("===================================================");
        System.out.println("  DEDUP RESULTS SUMMARY");
        System.out.println("===================================================");
        System.out.println("  Calls 1-3: USD -> All matched: " + (r1.equals(p1) && r2.equals(p2) && r3.equals(p3)));
        System.out.println("  Call 4:    EUR -> Matched: " + r4.equals(p4));
        System.out.println("  Call 5:    EUR -> Matched: " + r5.equals(p5));
        System.out.println("  Call 6:    EUR (beyond) -> Matched last: " + r5.equals(p6));
        System.out.println("  Call 7:    EUR (beyond) -> Matched last: " + r5.equals(p7));
        System.out.println("  ZIP size reduced from 5 entries to " + dataEntries + " data entries");
        System.out.println("===================================================");

        // Cleanup
        new File(deduplicatedZipPath).delete();
        new File(deduplicatedZipPath + ".bak").delete();
    }
}
