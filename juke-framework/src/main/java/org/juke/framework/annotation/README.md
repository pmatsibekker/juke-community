# Juke Annotation Framework

This module provides Spring-aware annotation processing for the `@Juke` annotation, which automatically wraps Spring-injected dependencies with JukeFactory, eliminating the need for manual `@PostConstruct` initialization.

## Features

- **Automatic Field Wrapping**: Fields annotated with `@Juke` are automatically wrapped after Spring dependency injection
- **Constructor Support**: Works with both field injection and constructor injection patterns  
- **Spring Integration**: Seamless integration with Spring Framework using BeanPostProcessor
- **Multiple JukeStates**: Support for different JukeState values (juke, record, replay, ignore, etc.)
- **Zero Configuration**: Auto-configuration for Spring Boot applications

## Usage

### Field Injection (Recommended)

**Before (Manual Wrapping):**
```java
@RestController
public class GreetingController {
    @Autowired
    private IGreetingsService service;

    @PostConstruct
    public void initialized() {
        this.service = (IGreetingsService) new JukeFactory<IGreetingsService>()
            .newInstance(this.service, IGreetingsService.class, JukeState.JUKE);
        System.out.println("initialized");
    }
}
```

**After (Automatic Wrapping):**
```java
@RestController
public class GreetingController {
    @Autowired
    @Juke  // Automatically wrapped by Spring (default value is "juke")
    private IGreetingsService service;

    @PostConstruct  
    public void initialized() {
        // No manual wrapping needed!
        System.out.println("initialized - service is automatically wrapped");
    }
}
```

### Constructor Injection

**Before (Manual Wrapping):**
```java
@Component
public class Car {
    private Engine engine;
    private Transmission transmission;
    
    @Autowired
    public Car(Engine engine, Transmission transmission) {
        this.engine = (Engine) new JukeFactory<Engine>()
            .newInstance(engine, Engine.class, JukeState.JUKE);
        this.transmission = (Transmission) new JukeFactory<Transmission>()
            .newInstance(transmission, Transmission.class, JukeState.JUKE);
    }
}
```

**After (Automatic Wrapping):**
```java
@Component
public class Car {
    @Juke
    private Engine engine;
    @Juke
    private Transmission transmission;
    
    @Autowired
    public Car(Engine engine, Transmission transmission) {
        this.engine = engine;          // Simple assignment
        this.transmission = transmission;  // Spring handles wrapping
    }
}
```

### Different JukeState Values

```java
@Component
public class ServiceExample {
    @Autowired @Juke              // Default value "juke" -> JukeState.JUKE
    private IGreetingsService greetingService;
    
    @Autowired @Juke("replay")  // Uses JukeState.REPLAY  
    private IAnotherService replayService;
    
    @Autowired @Juke("record")  // Uses JukeState.RECORD
    private IDataService recordService;
}
```

### Manual Initialization (When Needed)

For cases where Spring's BeanPostProcessor isn't available:

```java
// Initialize all @Juke annotated fields in this class
JukeInitializer.initializeAnnotatedFields(this);

// Or manually wrap a specific object
IGreetingsService wrapped = JukeInitializer.wrap(
    originalService, 
    IGreetingsService.class, 
    "juke"
);
```

## Setup

### For Spring Boot Applications

No additional setup required! The `JukeBeanPostProcessor` is automatically registered.

### For Regular Spring Applications

Add the configuration to your Spring context:

```java
@Configuration
@Import(JukeConfiguration.class)
public class AppConfig {
    // Your other configuration
}
```

Or manually register the bean post processor:

```java
@Configuration
public class AppConfig {
    @Bean
    public JukeBeanPostProcessor jukeBeanPostProcessor() {
        return new JukeBeanPostProcessor();
    }
}
```

## Supported JukeState Values

- `"juke"` - Default wrapping mode
- `"record"` - Recording mode
- `"replay"` - Replay mode  
- `"ignore"` - Ignore mode
- `"none"` - No specific state
- `"disable"` - Disabled mode

## Field type requirements (interface vs concrete)

`@Juke` picks the proxy mechanism from the field's declared type:

- **Interface-typed field** → JDK dynamic proxy (and cookie-scoped per-session replay).
- **Concrete-typed field** (e.g. a `RestTemplate`) → CGLIB subclass that delegates to the real bean, so it's assignable back to the field. Use the optional `name` (recording-identity prefix, to disambiguate two beans of the same type) and `excludeMethods` (skip config/builder methods): `@Juke(name = "shipping", excludeMethods = {"setMessageConverters"})`.
- **A `final` concrete class** can't be subclassed → annotate the **interface** it implements instead.
- **Leave the field alone on purpose** → remove `@Juke`, or set `@Juke(autoWrap = false)`.

> The former `@JukeTemplate` annotation has been **removed**. Migrate any `@JukeTemplate(name=…, excludeMethods=…)` field to `@Juke(name=…, excludeMethods=…)` — the concrete-field path above does exactly what it did.

## Requirements

- Java 8 or higher
- Spring Framework 5.0+ (for BeanPostProcessor support)
- The Juke Framework

## How It Works

1. **Dependency Injection**: Spring injects dependencies normally using `@Autowired`
2. **Post Processing**: After injection, `JukeBeanPostProcessor` scans for `@Juke` annotated fields
3. **Automatic Wrapping**: Fields with `@Juke` are automatically wrapped with `JukeFactory`
4. **Ready to Use**: Your fields are now wrapped and ready for Juke functionality

The `@Juke` annotation eliminates boilerplate code and makes Juke integration seamless with Spring's dependency injection system.