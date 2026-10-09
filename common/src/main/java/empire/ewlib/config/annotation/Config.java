package empire.ewlib.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a config model. EWLIB's annotation processor generates a
 * wrapper class named {@link #wrapperName()} next to it, with a getter, a setter
 * and (for {@link Hook} fields) a {@code subscribeToX} method per option.
 * <pre>{@code
 * @Config(name = "mymod", wrapperName = "MyModConfig")
 * public class MyModConfigModel {
 *     public boolean coolFeature = true;
 * }
 *
 * public static final MyModConfig CONFIG = MyModConfig.createAndLoad();
 * }</pre>
 * The config is stored in {@code config/<name>.json5}, and all translation keys
 * start with {@code text.config.<name>}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Config {

    /**
     * The name of this config, used for the file name and translation keys
     */
    String name();

    /**
     * The simple name of the generated wrapper class
     */
    String wrapperName();

    /**
     * Whether the config is saved to disk every time an option changes
     */
    boolean saveOnModification() default true;

    /**
     * Whether every option gets a {@code subscribeToX} method, as if it were annotated with {@link Hook}
     */
    boolean defaultHook() default false;
}
