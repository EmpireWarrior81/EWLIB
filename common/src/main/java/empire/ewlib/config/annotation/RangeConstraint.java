package empire.ewlib.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limits a numeric option to the given range. In the config screen
 * the option is shown as a slider instead of a text box.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface RangeConstraint {
    double min();

    double max();

    /**
     * How many decimal places the slider shows for floating point options
     */
    int decimalPlaces() default 2;
}
