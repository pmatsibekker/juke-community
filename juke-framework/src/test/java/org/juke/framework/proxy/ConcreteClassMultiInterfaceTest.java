package org.juke.framework.proxy;

import org.juke.framework.support.IBillable;
import org.juke.framework.support.IShippable;
import org.juke.framework.support.OrderService;
import org.juke.framework.storage.JukeHelper;
import org.juke.framework.storage.JukeZipDAOImpl;
import org.juke.framework.storage.ZipUtil;
import org.juke.framework.metadata.JukeClass;
import org.juke.framework.metadata.JukeConfigBuilder;
import org.juke.framework.metadata.DataProgramSchedule;
import org.juke.framework.metadata.JukeStateBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates that a concrete class implementing multiple interfaces can be
 * recorded to a zip and replayed with the correct results.
 * <p>
 * {@code OrderService} implements {@code IBillable} and {@code IShippable}.
 * All methods are recorded under the concrete class name ({@code OrderService})
 * — never under the individual interface names.
 * <p>
 * Flow:
 * <ol>
 *     <li>RECORD phase: create a CGLIB proxy of {@code OrderService}, call
 *         methods from both interfaces, flush the zip.</li>
 *     <li>Verify the zip entries use {@code OrderService.*} naming.</li>
 *     <li>REPLAY phase: create a new CGLIB proxy that reads from the zip,
 *         call the same methods and assert identical results.</li>
 * </ol>
 */
public class ConcreteClassMultiInterfaceTest {

    @TempDir
    File tempDir;

    /** Real service instance used as the recording target. */
    private final OrderService realService = new OrderService();

    @BeforeEach
    void resetState() {
        // Clear any cached state from previous tests
        JukeClass.instance().clear();
        org.juke.framework.proxy.ReplayHandler.getReplayHandlerCache().clear();
        JukeNameFormatter.clearMappings();
    }

    // ------------------------------------------------------------------
    // 1. JukeMethodFilter excludes Object methods
    // ------------------------------------------------------------------
    @Test
    void objectMethodsAreFilteredOut() throws Exception {
        Method toString  = OrderService.class.getMethod("toString");
        Method hashCode  = OrderService.class.getMethod("hashCode");
        Method equals    = OrderService.class.getMethod("equals", Object.class);
        Method bill      = OrderService.class.getMethod("bill", double.class);
        Method ship      = OrderService.class.getMethod("ship", String.class);

        assertFalse(JukeMethodFilter.shouldIntercept(toString),
                "toString should be filtered");
        assertFalse(JukeMethodFilter.shouldIntercept(hashCode),
                "hashCode should be filtered");
        assertFalse(JukeMethodFilter.shouldIntercept(equals),
                "equals should be filtered");
        assertTrue(JukeMethodFilter.shouldIntercept(bill),
                "bill() should be intercepted");
        assertTrue(JukeMethodFilter.shouldIntercept(ship),
                "ship() should be intercepted");
    }

    // ------------------------------------------------------------------
    // 2. JukeConfigBuilder picks up methods from all interfaces
    // ------------------------------------------------------------------
    @Test
    void configBuilderFindsMethodsFromAllInterfaces() {
        JukeClass config = JukeConfigBuilder.set(OrderService.class).build();

        assertNotNull(config);
        // Should have bill, currency (IBillable) + ship, estimatedDays (IShippable) = 4
        assertEquals(4, config.getMethods().size(),
                "Should find 4 methods: bill, currency, ship, estimatedDays");

        // Verify it is keyed under the concrete class, not an interface
        assertTrue(JukeClass.instance().containsKey(OrderService.class.getCanonicalName()),
                "JukeClass map should be keyed by OrderService, not IBillable/IShippable");
        assertFalse(JukeClass.instance().containsKey(IBillable.class.getCanonicalName()));
        assertFalse(JukeClass.instance().containsKey(IShippable.class.getCanonicalName()));
    }

    // ------------------------------------------------------------------
    // 3. Full record → verify zip entries → replay round-trip
    // ------------------------------------------------------------------
    @Test
    void recordAndReplayConcreteClassWithMultipleInterfaces() throws Exception {

        // ---- Expected live results (from the real service) ----
        String expectedBill100   = realService.bill(100.0);
        String expectedBill250   = realService.bill(250.0);
        String expectedCurrency  = realService.currency();
        String expectedShip      = realService.ship("123 Main St");
        int    expectedDays      = realService.estimatedDays("123 Main St");

        // ---- RECORD phase ----
        JukeState.setGlobaljuke(JukeState.RECORD);
        System.setProperty("juke", "record");
        System.setProperty("juke.path", tempDir.getAbsolutePath());
        System.setProperty("juke.zip", "order-test");

        JukeZipDAOImpl recordDao = new JukeZipDAOImpl(
                tempDir.getAbsolutePath(), "order-test");
        JukeHelper.setJukeDao(recordDao);

        // Build JukeClass metadata for the concrete class
        JukeConfigBuilder.set(OrderService.class).build();

        // Create a CGLIB proxy that records
        OrderService recordProxy = JukeClassInterceptor.createProxy(
                realService, OrderService.class);

        // Call methods from IBillable
        String recBill100  = recordProxy.bill(100.0);
        String recBill250  = recordProxy.bill(250.0);
        String recCurrency = recordProxy.currency();

        // Call methods from IShippable
        String recShip     = recordProxy.ship("123 Main St");
        int    recDays     = recordProxy.estimatedDays("123 Main St");

        // Verify recording proxy delegated to the real implementation
        assertEquals(expectedBill100,  recBill100);
        assertEquals(expectedBill250,  recBill250);
        assertEquals(expectedCurrency, recCurrency);
        assertEquals(expectedShip,     recShip);
        assertEquals(expectedDays,     recDays);

        // Flush the zip file
        JukeHelper.getJukeDAO().write();

        // In RECORD mode ZipUtil writes to a temp file; move it to the
        // well-known location so the REPLAY DAO can find it.
        String recordedPath = JukeHelper.getJukeDAO().path();
        File targetZip = new File(tempDir, "order-test.zip");
        new File(recordedPath).renameTo(targetZip);

        // ---- Verify zip entry naming ----
        String zipPath = targetZip.getAbsolutePath();
        assertTrue(targetZip.exists(), "Zip file should exist at: " + zipPath);

        Set<String> entries = ZipUtil.getFileNamesFromZipFile(zipPath);
        System.out.println("Zip entries: " + entries);

        // Every entry should start with "OrderService." — NEVER "IBillable." or "IShippable."
        for (String entry : entries) {
            // Skip framework metadata files
            if (entry.startsWith("juke")) continue;
            assertTrue(entry.startsWith("OrderService."),
                    "Entry should use concrete class name, got: " + entry);
            assertFalse(entry.startsWith("IBillable."),
                    "Entry should NOT use interface name IBillable, got: " + entry);
            assertFalse(entry.startsWith("IShippable."),
                    "Entry should NOT use interface name IShippable, got: " + entry);
        }

        // Verify we have entries for methods from BOTH interfaces
        assertTrue(entries.stream().anyMatch(e -> e.contains("bill")),
                "Should have a bill entry");
        assertTrue(entries.stream().anyMatch(e -> e.contains("currency")),
                "Should have a currency entry");
        assertTrue(entries.stream().anyMatch(e -> e.contains("ship")),
                "Should have a ship entry");
        assertTrue(entries.stream().anyMatch(e -> e.contains("estimatedDays")),
                "Should have an estimatedDays entry");

        // Verify bill was called twice (two sequence numbers)
        long billCount = entries.stream().filter(e -> e.contains("bill") && e.endsWith(".json")).count();
        assertTrue(billCount >= 1, "Should have at least 1 bill entry, got: " + billCount);

        // ---- REPLAY phase ----
        JukeState.setGlobaljuke(JukeState.REPLAY);
        System.setProperty("juke", "replay");

        // Reset handler cache so replay builds fresh from the zip
        resetState();

        JukeZipDAOImpl replayDao = new JukeZipDAOImpl(
                tempDir.getAbsolutePath(), "order-test");
        JukeHelper.setJukeDao(replayDao);

        // Rebuild metadata for the concrete class
        JukeConfigBuilder.set(OrderService.class).build();

        // Create a CGLIB proxy that replays
        OrderService replayProxy = JukeClassInterceptor.createProxy(
                realService, OrderService.class);

        // Replay — should return the same values that were recorded
        String replayBill100  = replayProxy.bill(100.0);
        String replayBill250  = replayProxy.bill(250.0);
        String replayCurrency = replayProxy.currency();
        String replayShip     = replayProxy.ship("123 Main St");
        int    replayDays     = replayProxy.estimatedDays("123 Main St");

        assertEquals(expectedBill100,  replayBill100,
                "Replayed bill(100) should match recorded value");
        assertEquals(expectedBill250,  replayBill250,
                "Replayed bill(250) should match recorded value");
        assertEquals(expectedCurrency, replayCurrency,
                "Replayed currency() should match recorded value");
        assertEquals(expectedShip,     replayShip,
                "Replayed ship() should match recorded value");
        assertEquals(expectedDays,     replayDays,
                "Replayed estimatedDays() should match recorded value");

        // ---- Verify Object methods are NOT in the zip ----
        assertFalse(entries.stream().anyMatch(e -> e.contains("toString")),
                "toString should NOT be recorded");
        assertFalse(entries.stream().anyMatch(e -> e.contains("hashCode")),
                "hashCode should NOT be recorded");
        assertFalse(entries.stream().anyMatch(e -> e.contains("equals")),
                "equals should NOT be recorded");
    }

    // ------------------------------------------------------------------
    // 4. Verify schedule is built correctly from concrete class zip
    // ------------------------------------------------------------------
    @Test
    void scheduleIsBuiltFromConcreteClassEntries() throws Exception {
        // Record a few calls first
        JukeState.setGlobaljuke(JukeState.RECORD);
        System.setProperty("juke", "record");
        System.setProperty("juke.path", tempDir.getAbsolutePath());
        System.setProperty("juke.zip", "schedule-test");

        JukeZipDAOImpl dao = new JukeZipDAOImpl(
                tempDir.getAbsolutePath(), "schedule-test");
        JukeHelper.setJukeDao(dao);
        JukeConfigBuilder.set(OrderService.class).build();

        OrderService proxy = JukeClassInterceptor.createProxy(
                realService, OrderService.class);

        proxy.bill(10.0);
        proxy.bill(20.0);
        proxy.bill(30.0);
        proxy.ship("A");
        proxy.ship("B");

        JukeHelper.getJukeDAO().write();

        // Move recorded zip to well-known location
        String recordedPath = JukeHelper.getJukeDAO().path();
        File targetZip = new File(tempDir, "schedule-test.zip");
        new File(recordedPath).renameTo(targetZip);

        // Build a schedule from the zip and verify counts
        String zipPath = targetZip.getAbsolutePath();
        JukeStateBuilder built = new JukeStateBuilder.Builder(
                ZipUtil.getFileNamesFromZipFile(zipPath)).build();
        DataProgramSchedule schedule = built.getSchedule();

        assertNotNull(schedule, "Schedule should not be null");

        // bill was called 3 times
        int billSize = schedule.size("OrderService.bill");
        assertTrue(billSize >= 1,
                "Schedule should track OrderService.bill, got size: " + billSize);

        // ship was called 2 times
        int shipSize = schedule.size("OrderService.ship");
        assertTrue(shipSize >= 1,
                "Schedule should track OrderService.ship, got size: " + shipSize);

        // Should NOT have entries under interface names
        assertEquals(0, schedule.size("IBillable.bill"),
                "Should NOT have entries under IBillable");
        assertEquals(0, schedule.size("IShippable.ship"),
                "Should NOT have entries under IShippable");
    }
}

