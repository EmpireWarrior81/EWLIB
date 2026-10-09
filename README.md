# EWLIB

Shared library for EmpireWarrior81's mods, for Fabric and NeoForge.

Modelled after owo-libs design

**This branch targets Minecraft 26.1.2.**

## Project layout

| Folder     | Contents                                                        |
|------------|-----------------------------------------------------------------|
| `common`   | Everything that matters: config core, annotation processor, screen |
| `fabric`   | Fabric glue: config directory, Mod Menu integration               |
| `neoforge` | NeoForge glue: config directory, "Config" button in the Mods screen |

## Building

```
gradlew build                 # jars end up in fabric/build/libs and neoforge/build/libs
gradlew publishToMavenLocal   # makes EWLIB available to your other mods via mavenLocal()
```

## Using it in a mod

```groovy
repositories {
    mavenLocal()
}

dependencies {
    // Fabric (Loom)
    implementation "empire.empirewarrior:ewlib-fabric-26.1.2:1.0.0"
    annotationProcessor "empire.empirewarrior:ewlib-fabric-26.1.2:1.0.0"
    include "empire.empirewarrior:ewlib-fabric-26.1.2:1.0.0"

    // NeoForge (ModDevGradle)
    implementation "empire.empirewarrior:ewlib-neoforge-26.1.2:1.0.0"
    annotationProcessor "empire.empirewarrior:ewlib-neoforge-26.1.2:1.0.0"
    jarJar "empire.empirewarrior:ewlib-neoforge-26.1.2:1.0.0"
}
```

### Config

```java
@Config(name = "mymod", wrapperName = "MyModConfig")
public class MyModConfigModel {

    @SectionHeader("general")
    @Comment("Shown above the option in the config file")
    public boolean coolFeature = true;

    @Hook
    @RestartRequired
    public boolean needsRestart = false;

    @RangeConstraint(min = 0, max = 10)
    public int range = 5;
}
```

The annotation processor generates `MyModConfig` with a getter and setter per option, plus
`subscribeToX(...)` for `@Hook` options:

```java
public static final MyModConfig CONFIG = MyModConfig.createAndLoad();

CONFIG.coolFeature();          // read
CONFIG.coolFeature(false);     // write (saved to disk immediately)
CONFIG.subscribeToNeedsRestart(value -> ...);
```

Register the config screen on the client:

```java
ConfigScreen.registerProvider("mymod", parent -> ConfigScreen.create(CONFIG, parent));
```

Translation keys are the same as owo-lib's: `text.config.<name>.title`,
`text.config.<name>.section.<section>`, `text.config.<name>.option.<option>` and an optional
`text.config.<name>.option.<option>.tooltip`.

| Annotation           | Effect                                                          |
|----------------------|-----------------------------------------------------------------|
| `@Config`            | Marks the model class; sets file name and wrapper class name     |
| `@Hook`              | Generates `subscribeToX` for the option                          |
| `@SectionHeader`     | Starts a section in the screen (and the Sections sidebar)       |
| `@Comment`           | `// comment` above the option in the config file                |
| `@RestartRequired`   | Tooltip note, and offers to exit the game after changing it      |
| `@RangeConstraint`   | Limits a number; shown as a slider                               |
| `@ExcludeFromScreen` | Only editable in the file                                        |

Supported option types: `boolean`, all number types, `String`, enums, `Identifier`, and
`List`/`Set` of those (lists are file-only for now; the screen does not show them yet).

## Credits

The config system's design and the search icon come from owo-lib (MIT).
