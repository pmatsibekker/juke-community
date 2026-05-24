package org.juke.framework.proxy;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the name collision detection and disambiguation logic in JukeNameFormatter.
 *
 * Verifies that:
 * - When there are NO collisions, simple names are used (e.g. "MyService")
 * - When there ARE collisions, minimum-distinguishing package prefixes are added
 *   (e.g. "example.MyService" vs "other.MyService")
 * - Type discriminators also get disambiguated when they collide
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class NameCollisionTest {

    @BeforeEach
    void setup() {
        JukeNameFormatter.clearMappings();
    }

    // --- simpleName tests ------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("simpleName extracts last segment from dotted FQN")
    void simpleName_dotted() {
        assertEquals("MyService", JukeNameFormatter.simpleName("com.example.MyService"));
        assertEquals("Inner", JukeNameFormatter.simpleName("com.example.Outer.Inner"));
    }

    @Test
    @Order(2)
    @DisplayName("simpleName extracts last segment from dollar-separated inner class")
    void simpleName_dollar() {
        assertEquals("Inner", JukeNameFormatter.simpleName("com.example.Outer$Inner"));
    }

    @Test
    @Order(3)
    @DisplayName("simpleName returns the input when no separators")
    void simpleName_noPackage() {
        assertEquals("MyService", JukeNameFormatter.simpleName("MyService"));
    }

    // --- splitFqn tests --------------------------------------------------

    @Test
    @Order(10)
    @DisplayName("splitFqn handles dots and dollars")
    void splitFqn_mixed() {
        assertArrayEquals(
                new String[]{"com", "example", "Outer", "Inner"},
                JukeNameFormatter.splitFqn("com.example.Outer$Inner"));
    }

    // --- joinTail tests --------------------------------------------------

    @Test
    @Order(11)
    @DisplayName("joinTail returns last N segments joined by dots")
    void joinTail_basic() {
        String[] parts = {"com", "example", "api", "MyService"};
        assertEquals("MyService", JukeNameFormatter.joinTail(parts, 1));
        assertEquals("api.MyService", JukeNameFormatter.joinTail(parts, 2));
        assertEquals("example.api.MyService", JukeNameFormatter.joinTail(parts, 3));
        assertEquals("com.example.api.MyService", JukeNameFormatter.joinTail(parts, 4));
    }

    // --- Collision detection: no collision --------------------------------

    @Test
    @Order(20)
    @DisplayName("No collision: disambiguatedName returns simple name")
    void noCollision_simpleName() {
        JukeNameFormatter.registerFqn("com.example.MyService");
        assertEquals("MyService", JukeNameFormatter.disambiguatedName("com.example.MyService"));
    }

    @Test
    @Order(21)
    @DisplayName("No collision: two different simple names stay simple")
    void noCollision_differentNames() {
        JukeNameFormatter.registerFqn("com.example.ServiceA");
        JukeNameFormatter.registerFqn("com.example.ServiceB");
        assertEquals("ServiceA", JukeNameFormatter.disambiguatedName("com.example.ServiceA"));
        assertEquals("ServiceB", JukeNameFormatter.disambiguatedName("com.example.ServiceB"));
    }

    // --- Collision detection: same simple name, different packages --------

    @Test
    @Order(30)
    @DisplayName("Collision: same simple name in different packages adds minimum prefix")
    void collision_sameSimpleName_differentPackages() {
        JukeNameFormatter.registerFqn("com.example.MyService");
        JukeNameFormatter.registerFqn("com.other.MyService");

        String name1 = JukeNameFormatter.disambiguatedName("com.example.MyService");
        String name2 = JukeNameFormatter.disambiguatedName("com.other.MyService");

        System.out.println("  com.example.MyService -> " + name1);
        System.out.println("  com.other.MyService   -> " + name2);

        assertNotEquals(name1, name2, "Colliding names must be different");
        assertTrue(name1.contains("MyService"), "Must still contain the class name");
        assertTrue(name2.contains("MyService"), "Must still contain the class name");

        // Should be "example.MyService" and "other.MyService"
        assertEquals("example.MyService", name1);
        assertEquals("other.MyService", name2);
    }

    @Test
    @Order(31)
    @DisplayName("Collision: deeper disambiguation needed when immediate parents match")
    void collision_sameParent() {
        // Both have "api" as immediate parent
        JukeNameFormatter.registerFqn("com.example.api.MyService");
        JukeNameFormatter.registerFqn("com.other.api.MyService");

        String name1 = JukeNameFormatter.disambiguatedName("com.example.api.MyService");
        String name2 = JukeNameFormatter.disambiguatedName("com.other.api.MyService");

        System.out.println("  com.example.api.MyService -> " + name1);
        System.out.println("  com.other.api.MyService   -> " + name2);

        assertNotEquals(name1, name2, "Colliding names must be different");
        // Need depth 3: "example.api.MyService" vs "other.api.MyService"
        assertEquals("example.api.MyService", name1);
        assertEquals("other.api.MyService", name2);
    }

    @Test
    @Order(32)
    @DisplayName("Collision: three-way collision uses minimum unique prefix for each")
    void collision_threeWay() {
        JukeNameFormatter.registerFqn("com.alpha.MyService");
        JukeNameFormatter.registerFqn("com.beta.MyService");
        JukeNameFormatter.registerFqn("com.gamma.MyService");

        String a = JukeNameFormatter.disambiguatedName("com.alpha.MyService");
        String b = JukeNameFormatter.disambiguatedName("com.beta.MyService");
        String c = JukeNameFormatter.disambiguatedName("com.gamma.MyService");

        System.out.println("  com.alpha.MyService -> " + a);
        System.out.println("  com.beta.MyService  -> " + b);
        System.out.println("  com.gamma.MyService -> " + c);

        assertEquals("alpha.MyService", a);
        assertEquals("beta.MyService", b);
        assertEquals("gamma.MyService", c);
    }

    // --- Collision with inner classes (dollar notation) -------------------

    @Test
    @Order(40)
    @DisplayName("Collision: inner classes with same simple name in different outers")
    void collision_innerClasses() {
        JukeNameFormatter.registerFqn("com.example.TestA$IDataService");
        JukeNameFormatter.registerFqn("com.example.TestB$IDataService");

        String name1 = JukeNameFormatter.disambiguatedName("com.example.TestA$IDataService");
        String name2 = JukeNameFormatter.disambiguatedName("com.example.TestB$IDataService");

        System.out.println("  TestA$IDataService -> " + name1);
        System.out.println("  TestB$IDataService -> " + name2);

        assertNotEquals(name1, name2);
        // Should distinguish by outer class: "TestA.IDataService" vs "TestB.IDataService"
        assertEquals("TestA.IDataService", name1);
        assertEquals("TestB.IDataService", name2);
    }

    // --- Type discriminator collision ------------------------------------

    @Test
    @Order(50)
    @DisplayName("Type discriminator: no collision uses simple name")
    void typeDiscriminator_noCollision() {
        JukeNameFormatter.registerFqn("com.example.IService");
        JukeNameFormatter.registerFqn("com.example.WeatherReport");

        // Test the underlying disambiguatedName directly since we can't fake Class.getName()
        String ifaceName = JukeNameFormatter.disambiguatedName("com.example.IService");
        String typeName = JukeNameFormatter.disambiguatedName("com.example.WeatherReport");

        System.out.println("  interface: " + ifaceName + "  type: " + typeName);
        assertEquals("IService", ifaceName);
        assertEquals("WeatherReport", typeName);
        // The full short identifier would be: IService.getData@WeatherReport
    }

    @Test
    @Order(51)
    @DisplayName("Type discriminator: collision on responseType adds prefix")
    void typeDiscriminator_collision() {
        JukeNameFormatter.registerFqn("com.example.Result");
        JukeNameFormatter.registerFqn("com.other.Result");

        String name1 = JukeNameFormatter.disambiguatedName("com.example.Result");
        String name2 = JukeNameFormatter.disambiguatedName("com.other.Result");

        System.out.println("  @com.example.Result -> " + name1);
        System.out.println("  @com.other.Result   -> " + name2);

        assertNotEquals(name1, name2);
        assertEquals("example.Result", name1);
        assertEquals("other.Result", name2);
    }

    // --- cleanMethodName tests -------------------------------------------

    @Test
    @Order(60)
    @DisplayName("cleanMethodName strips overload hash suffix")
    void cleanMethodName_stripsHash() {
        assertEquals("fetchData", JukeNameFormatter.cleanMethodName("fetchData1679564109"));
        assertEquals("getData", JukeNameFormatter.cleanMethodName("getData-1641989625"));
    }

    @Test
    @Order(61)
    @DisplayName("cleanMethodName preserves names without hash")
    void cleanMethodName_noHash() {
        assertEquals("fetchData", JukeNameFormatter.cleanMethodName("fetchData"));
        assertEquals("get123", JukeNameFormatter.cleanMethodName("get123")); // only 3 digits, not a hash
    }

}

