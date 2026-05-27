# Verification Guide - Dynamic Proxy Wrapping & Session-Aware Replay

This guide explains how to replicate the vulnerabilities/limitations of the original codebase and verify the fixes using the benchmark proof-of-vulnerability tests.

---

## 🔍 The Issues

1. **Unwrapped Startup Dependencies**:
   If Juke's startup mode was `IGNORE` or `NONE`, class-level `@Juke` annotated singletons were not wrapped in CGLIB proxies. This prevented them from being dynamically controlled (enabled/disabled/configured) via the REST API at runtime.
   
2. **Session-Agnostic Proxies**:
   Class-level and template-level (e.g., `RestTemplate`) dependencies did not check or respect request-scoped session contexts (playback cookies). During session-based playback, these dependencies fell back to static global files or live database calls, leaking/ignoring session isolated state.

---

## 🧪 Benchmark Proof Tests

Two dedicated proof-of-vulnerability tests have been added to the suite to serve as the verification benchmark:

* **[JukeTypeBeanPostProcessorProofTest.java](file:///mnt/c/Users/JM/git/juke-community/juke-framework/src/test/java/org/juke/framework/spring/JukeTypeBeanPostProcessorProofTest.java)**:
  Asserts that class-level `@Juke` beans are CGLIB-wrapped during startup even in `IGNORE` or `NONE` mode.
  
* **[ConcretePathSessionAwarenessProofTest.java](file:///mnt/c/Users/JM/git/juke-community/juke-framework/src/test/java/org/juke/framework/proxy/ConcretePathSessionAwarenessProofTest.java)**:
  Asserts that concrete/class-level proxies and template-level proxies route to session-specific storage and schedules when a playback session is active.

---

## 🔄 How to Replicate and Verify

### Step 1: Run Benchmarks on the Original Code (The Failure Proof)
To see the issues in action, run the benchmark tests on the original codebase before the fix was applied:

1. **Stash or discard local fixes temporarily**:
   ```bash
   git stash
   ```
2. **Run the benchmark tests**:
   ```bash
   mvn test -pl juke-framework -Dtest=JukeTypeBeanPostProcessorProofTest,ConcretePathSessionAwarenessProofTest
   ```
   **Expected Result**: Both tests fail.
   * `JukeTypeBeanPostProcessorProofTest` fails because the returned bean is not a proxy.
   * `ConcretePathSessionAwarenessProofTest` fails because it receives live values rather than mock data from the session context.

---

### Step 2: Run Benchmarks on the Fixed Code (The Success Proof)
Restore the fixes and run the same tests to verify the resolution:

1. **Apply the fixes**:
   ```bash
   git stash pop
   ```
2. **Run the benchmark tests**:
   ```bash
   mvn test -pl juke-framework -Dtest=JukeTypeBeanPostProcessorProofTest,ConcretePathSessionAwarenessProofTest
   ```
   **Expected Result**: All tests pass successfully.

---

### Step 3: Run the Complete Framework Test Suite
To confirm that the fixes do not introduce any regressions, run the entire `juke-framework` test suite:
```bash
mvn test -pl juke-framework
```
**Expected Result**: All 629 tests pass successfully.
