package empire.ewlib.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A value container that notifies its observers whenever the value changes
 * to something not {@link Objects#equals(Object, Object) equal} to the old value
 */
public class Observable<T> {

    private final List<Consumer<T>> observers = new ArrayList<>();
    private T value;

    protected Observable(T initial) {
        this.value = initial;
    }

    public static <T> Observable<T> of(T initial) {
        return new Observable<>(initial);
    }

    public T get() {
        return this.value;
    }

    public void set(T newValue) {
        var oldValue = this.value;
        this.value = newValue;

        if (!Objects.equals(oldValue, newValue)) {
            for (var observer : List.copyOf(this.observers)) {
                observer.accept(newValue);
            }
        }
    }

    public void observe(Consumer<T> observer) {
        this.observers.add(observer);
    }
}
