package empire.ewlib.config;

import empire.ewlib.config.annotation.Config;
import empire.ewlib.config.annotation.Hook;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.Map;

/**
 * Generates the wrapper class for every {@link Config}-annotated config model.
 * <p>
 * For each option {@code foo} of type {@code T} the wrapper gets {@code T foo()},
 * {@code void foo(T value)} and, if the option is a {@link Hook}, {@code subscribeToFoo(Consumer<T>)}.
 */
@SupportedAnnotationTypes("empire.ewlib.config.annotation.Config")
public class ConfigAP extends AbstractProcessor {

    private static final String WRAPPER_TEMPLATE = """
            package {package};

            import empire.ewlib.config.ConfigWrapper;
            import empire.ewlib.config.Option;

            import java.util.function.Consumer;

            public class {wrapper_class_name} extends ConfigWrapper<{config_class_name}> {

                public final Keys keys = new Keys();

            {option_instances}

                private {wrapper_class_name}() {
                    super({config_class_name}.class);
                }

                public static {wrapper_class_name} createAndLoad() {
                    var wrapper = new {wrapper_class_name}();
                    wrapper.load();
                    return wrapper;
                }

            {accessors}
                public static class Keys {
            {key_constants}
                }
            }
            """;

    private static final java.util.Set<String> RESERVED_NAMES = java.util.Set.of(
            "keys", "name", "instance", "loading", "options", "optionsView", "save", "load", "fileLocation",
            "optionForKey", "fieldForKey", "allOptions", "forEachOption", "byName", "createAndLoad"
    );

    private Map<TypeMirror, TypeMirror> primitivesToWrappers;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        var types = processingEnv.getTypeUtils();
        var elements = processingEnv.getElementUtils();

        this.primitivesToWrappers = Map.of(
                types.getPrimitiveType(TypeKind.BYTE), elements.getTypeElement("java.lang.Byte").asType(),
                types.getPrimitiveType(TypeKind.CHAR), elements.getTypeElement("java.lang.Character").asType(),
                types.getPrimitiveType(TypeKind.SHORT), elements.getTypeElement("java.lang.Short").asType(),
                types.getPrimitiveType(TypeKind.INT), elements.getTypeElement("java.lang.Integer").asType(),
                types.getPrimitiveType(TypeKind.LONG), elements.getTypeElement("java.lang.Long").asType(),
                types.getPrimitiveType(TypeKind.FLOAT), elements.getTypeElement("java.lang.Float").asType(),
                types.getPrimitiveType(TypeKind.DOUBLE), elements.getTypeElement("java.lang.Double").asType(),
                types.getPrimitiveType(TypeKind.BOOLEAN), elements.getTypeElement("java.lang.Boolean").asType()
        );
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(java.util.Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (var annotated : roundEnv.getElementsAnnotatedWith(Config.class)) {
            if (annotated.getKind() != ElementKind.CLASS) continue;

            var clazz = (TypeElement) annotated;
            var config = clazz.getAnnotation(Config.class);
            var className = clazz.getQualifiedName().toString();
            var packageName = this.processingEnv.getElementUtils().getPackageOf(clazz).getQualifiedName().toString();
            var wrapperName = config.wrapperName();

            try {
                var file = this.processingEnv.getFiler().createSourceFile(packageName.isEmpty() ? wrapperName : packageName + "." + wrapperName, clazz);
                try (var writer = new PrintWriter(file.openWriter())) {
                    writer.print(this.makeWrapper(packageName, wrapperName, className, clazz, config.defaultHook()));
                }
            } catch (IOException e) {
                this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to generate config wrapper: " + e.getMessage(), clazz);
            }
        }

        return true;
    }

    private String makeWrapper(String packageName, String wrapperName, String configClassName, TypeElement clazz, boolean defaultHook) {
        var optionInstances = new StringBuilder();
        var accessors = new StringBuilder();
        var keyConstants = new StringBuilder();

        for (var field : clazz.getEnclosedElements()) {
            if (field.getKind() != ElementKind.FIELD) continue;
            if (field.getModifiers().contains(Modifier.STATIC) || field.getModifiers().contains(Modifier.TRANSIENT)) continue;

            var name = field.getSimpleName().toString();
            if (RESERVED_NAMES.contains(name)) {
                this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "Config option '" + name + "' clashes with a ConfigWrapper member, please rename it", field);
                continue;
            }

            var type = field.asType();
            if (type.getKind() == TypeKind.TYPEVAR) {
                this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Generic field types are not allowed in config classes", field);
                continue;
            }

            var boxedType = this.primitivesToWrappers.getOrDefault(type, type).toString();

            keyConstants.append("        public final Option.Key ").append(name)
                    .append(" = new Option.Key(\"").append(name).append("\");\n");

            optionInstances.append("    private final Option<").append(boxedType).append("> ").append(name)
                    .append(" = this.optionForKey(this.keys.").append(name).append(");\n");

            accessors.append("    public ").append(type).append(' ').append(name).append("() {\n")
                    .append("        return ").append(name).append(".value();\n")
                    .append("    }\n\n");

            accessors.append("    public void ").append(name).append('(').append(type).append(" value) {\n")
                    .append("        ").append(name).append(".set(value);\n")
                    .append("    }\n\n");

            if (defaultHook || field.getAnnotation(Hook.class) != null) {
                accessors.append("    public void subscribeTo").append(capitalize(name)).append("(Consumer<").append(boxedType).append("> subscriber) {\n")
                        .append("        ").append(name).append(".observe(subscriber);\n")
                        .append("    }\n\n");
            }
        }

        var wrapper = WRAPPER_TEMPLATE
                .replace("{wrapper_class_name}", wrapperName)
                .replace("{config_class_name}", configClassName)
                .replace("{option_instances}", stripTrailingNewline(optionInstances))
                .replace("{accessors}", accessors.toString())
                .replace("{key_constants}", stripTrailingNewline(keyConstants));

        return packageName.isEmpty() ? wrapper.replace("package ;\n\n", "") : wrapper.replace("{package}", packageName);
    }

    private static String stripTrailingNewline(StringBuilder builder) {
        if (!builder.isEmpty() && builder.charAt(builder.length() - 1) == '\n') builder.setLength(builder.length() - 1);
        return builder.toString();
    }

    private static String capitalize(String string) {
        return string.substring(0, 1).toUpperCase(Locale.ROOT) + string.substring(1);
    }
}
