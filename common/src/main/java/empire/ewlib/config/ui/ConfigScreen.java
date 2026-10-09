package empire.ewlib.config.ui;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import empire.ewlib.EWLib;
import empire.ewlib.config.ConfigTypes;
import empire.ewlib.config.ConfigWrapper;
import empire.ewlib.config.Option;
import empire.ewlib.config.annotation.ExcludeFromScreen;
import empire.ewlib.config.annotation.RestartRequired;
import empire.ewlib.config.annotation.SectionHeader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A screen which generates a row for every option of a config, laid out like
 * owo-lib's config screen: a dark panel with the options, section headers, a
 * collapsible sections sidebar, a search box and Reload / Done buttons.
 * <p>
 * Changes are applied when the screen closes. If an option marked with
 * {@link RestartRequired} changed, the player is offered to exit the game.
 * <p>
 * Extend this class and override {@link #factoryForOption(Option)} to change
 * which rows are created, or return {@code null} there to hide an option.
 */
public class ConfigScreen extends Screen {

    public static final Identifier SEARCH_ICON = Identifier.fromNamespaceAndPath(EWLib.MOD_ID, "textures/gui/config_search.png");

    protected static final int TITLE_HEIGHT = 29;
    protected static final int BOTTOM_BAR_HEIGHT = 30;
    protected static final int SIDE_PADDING = 50;
    protected static final int SCROLLBAR_WIDTH = 3;
    protected static final int SCROLL_STEP = 15;
    protected static final int SIDEBAR_TOGGLE_WIDTH = 10;
    protected static final long SIDEBAR_ANIMATION_MILLIS = 350;
    protected static final long HIGHLIGHT_MILLIS = 1250;

    private static final Map<Predicate<Option<?>>, OptionComponentFactory<?>> DEFAULT_FACTORIES = new LinkedHashMap<>();

    /**
     * Extra option factories, checked before the default ones. Add to this to
     * support more option types or replace the row of specific options.
     */
    protected final Map<Predicate<Option<?>>, OptionComponentFactory<?>> extraFactories = new LinkedHashMap<>();

    protected final @Nullable Screen parent;
    protected final ConfigWrapper<?> config;

    protected @Nullable List<ConfigEntry> entries = null;
    protected final Map<Option<?>, OptionComponent> optionComponents = new LinkedHashMap<>();
    protected final List<SectionHeaderEntry> sections = new ArrayList<>();

    // layout, recomputed every frame
    protected int panelTop, panelBottom, containerRight;
    protected int viewportLeft, viewportTop, viewportRight, viewportBottom;
    protected int contentHeight;

    // scrolling
    protected double scrollOffset = 0, currentScroll = 0;
    protected long lastScrollbarInteractTime = 0;
    protected boolean draggingScrollbar = false;
    protected long lastFrameTime = System.nanoTime();

    // sections sidebar
    protected boolean sidebarOpen = false;
    protected long sidebarToggleTime = 0;

    // search
    protected @Nullable EditBox searchField;
    protected String searchText = "";
    protected Component matchIndicator = Component.empty();
    protected @Nullable SearchMatches currentMatches = null;
    protected int currentMatchIndex = 0;
    protected @Nullable ConfigEntry highlightedEntry = null;
    protected long highlightStartTime = 0;

    public ConfigScreen(ConfigWrapper<?> config, @Nullable Screen parent) {
        super(Component.translatable("text.config." + config.name() + ".title"));
        this.parent = parent;
        this.config = config;
    }

    /**
     * Create a config screen for the given config
     *
     * @param parent The screen to return to when this one closes
     */
    public static ConfigScreen create(ConfigWrapper<?> config, @Nullable Screen parent) {
        return new ConfigScreen(config, parent);
    }

    /**
     * Register a config screen for a mod, see {@link ConfigScreenProviders#register(String, Function)}
     */
    public static <S extends Screen> void registerProvider(String modId, Function<Screen, S> provider) {
        ConfigScreenProviders.register(modId, provider);
    }

    // -------------
    // Setup
    // -------------

    @Override
    protected void init() {
        if (this.entries == null) this.buildEntries();

        for (var component : this.optionComponents.values()) {
            for (var widget : component.widgets()) this.addWidget(widget);
        }

        int buttonWidth = Math.max((int) ((this.width - SIDE_PADDING * 2) * .1f), 40);
        int buttonY = this.height - BOTTOM_BAR_HEIGHT + 5;

        var doneButton = this.addRenderableWidget(Button.builder(Component.translatable("text.ewlib.config.button.done"), button -> this.onClose())
                .bounds(this.width - SIDE_PADDING - buttonWidth, buttonY, buttonWidth, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable("text.ewlib.config.button.reload"), button -> this.reload())
                .bounds(doneButton.getX() - 5 - buttonWidth, buttonY, buttonWidth, 20)
                .build());

        int searchWidth = Mth.clamp((this.width - SIDE_PADDING * 2) / 4, 60, 200);
        this.searchField = this.addRenderableWidget(new EditBox(this.font, SIDE_PADDING + 20 + 3, this.height - BOTTOM_BAR_HEIGHT / 2 - 4, searchWidth, 9, Component.translatable("text.ewlib.config.search")));
        this.searchField.setBordered(false);
        this.searchField.setMaxLength(128);
        this.searchField.setValue(this.searchText);
        this.searchField.setSuggestion(this.searchText.isEmpty() ? I18n.get("text.ewlib.config.search") : "");
        this.searchField.setResponder(text -> {
            this.searchField.setSuggestion(text.isEmpty() ? I18n.get("text.ewlib.config.search") : "");
            if (!text.equals(this.searchText)) {
                this.searchField.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
                this.matchIndicator = Component.empty();
            }
            this.searchText = text;
        });

        this.layoutEntries();
    }

    protected void buildEntries() {
        this.entries = new ArrayList<>();
        this.optionComponents.clear();
        this.sections.clear();

        this.config.forEachOption(option -> {
            if (option.backingField().hasAnnotation(ExcludeFromScreen.class)) return;

            var factory = this.factoryForOption(option);
            if (factory == null) {
                EWLib.LOGGER.debug("No config screen row for option {}", option);
                return;
            }

            if (option.backingField().hasAnnotation(SectionHeader.class)) {
                var header = new SectionHeaderEntry(Component.translatable("text.config." + this.config.name() + ".section."
                        + option.backingField().field().getAnnotation(SectionHeader.class).value()));
                this.sections.add(header);
                this.entries.add(header);
            }

            @SuppressWarnings({"unchecked", "rawtypes"})
            var component = ((OptionComponentFactory) factory).make(option);
            this.optionComponents.put(option, component);
            this.entries.add(component);
        });
    }

    /**
     * @return The factory for the given option's row, or {@code null} to leave the option out of the screen
     */
    protected @Nullable OptionComponentFactory<?> factoryForOption(Option<?> option) {
        for (var entry : this.extraFactories.entrySet()) {
            if (entry.getKey().test(option)) return entry.getValue();
        }

        for (var entry : DEFAULT_FACTORIES.entrySet()) {
            if (entry.getKey().test(option)) return entry.getValue();
        }

        return null;
    }

    protected void reload() {
        this.config.load();
        this.entries = null;
        this.currentMatches = null;
        this.highlightedEntry = null;
        this.rebuildWidgets();
    }

    // -------------
    // Layout
    // -------------

    protected int sidebarWidth() {
        if (this.sections.isEmpty()) return 0;

        int contentWidth = this.font.width(Component.translatable("text.ewlib.config.sections"));
        for (var section : this.sections) {
            contentWidth = Math.max(contentWidth, this.font.width(section.title()));
        }

        float progress = Mth.clamp((System.currentTimeMillis() - this.sidebarToggleTime) / (float) SIDEBAR_ANIMATION_MILLIS, 0, 1);
        float openness = ConfigRendering.easeCubic(this.sidebarOpen ? progress : 1 - progress);
        return Math.round((contentWidth + 4 + 4) * openness);
    }

    protected void layoutEntries() {
        if (this.entries == null) return;

        this.panelTop = TITLE_HEIGHT + 1;
        this.panelBottom = this.height - BOTTOM_BAR_HEIGHT - 1;
        this.containerRight = this.width - SIDE_PADDING - this.sidebarWidth();

        this.viewportLeft = SIDE_PADDING + 1;
        this.viewportTop = this.panelTop + 1;
        this.viewportRight = this.containerRight - 1 - (this.sections.isEmpty() ? 0 : SIDEBAR_TOGGLE_WIDTH);
        this.viewportBottom = this.panelBottom - 1;

        this.contentHeight = 0;
        for (var entry : this.entries) this.contentHeight += entry.height();

        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, this.maxScroll());
        this.currentScroll = Mth.clamp(this.currentScroll, 0, this.maxScroll());

        int y = this.viewportTop - (int) Math.round(this.currentScroll);
        int entryWidth = this.viewportRight - this.viewportLeft - SCROLLBAR_WIDTH;
        for (var entry : this.entries) {
            entry.layout(this.viewportLeft, y, entryWidth);
            y += entry.height();
        }

        this.updateWidgetVisibility(true);
    }

    /**
     * Show only the row widgets inside the visible part of the panel, so hidden
     * ones can't be clicked. With {@code interactive == false}, all are hidden.
     */
    protected void updateWidgetVisibility(boolean interactive) {
        for (var component : this.optionComponents.values()) {
            for (var widget : component.widgets()) {
                widget.visible = interactive
                        && component.isWidgetShown(widget)
                        && widget.getBottom() > this.viewportTop
                        && widget.getY() < this.viewportBottom;
            }
        }
    }

    protected int maxScroll() {
        return Math.max(0, this.contentHeight - (this.viewportBottom - this.viewportTop));
    }

    protected void scrollBy(double amount) {
        this.scrollOffset = Mth.clamp(this.scrollOffset + amount, 0, this.maxScroll());
        this.lastScrollbarInteractTime = System.currentTimeMillis() + 1250;
    }

    /**
     * Scroll so that {@code entry} is at the top of the panel
     */
    protected void scrollTo(ConfigEntry entry) {
        this.scrollBy(entry.y() - this.viewportTop);
    }

    protected boolean isInViewport(double mouseX, double mouseY) {
        return mouseX >= this.viewportLeft && mouseX < this.viewportRight && mouseY >= this.viewportTop && mouseY < this.viewportBottom;
    }

    protected boolean isInScrollbar(double mouseX, double mouseY) {
        return this.maxScroll() > 0 && this.isInViewport(mouseX, mouseY) && mouseX >= this.viewportRight - SCROLLBAR_WIDTH;
    }

    protected boolean isOnSidebarToggle(double mouseX, double mouseY) {
        return !this.sections.isEmpty()
                && mouseX >= this.containerRight - SIDEBAR_TOGGLE_WIDTH && mouseX < this.containerRight
                && mouseY >= this.panelTop && mouseY < this.panelBottom;
    }

    // -------------
    // Rendering
    // -------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        long now = System.nanoTime();
        float ticks = (now - this.lastFrameTime) / 50_000_000f;
        this.lastFrameTime = now;

        this.currentScroll += (this.scrollOffset - this.currentScroll) * Math.min(ticks * .5f, 1);
        if (Math.abs(this.scrollOffset - this.currentScroll) < .5) this.currentScroll = this.scrollOffset;

        this.layoutEntries();

        graphics.centeredText(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);

        // the panel: a faint line above and below, a dark translucent body
        graphics.horizontalLine(0, this.width - 1, this.panelTop - 1, 0x33FFFFFF);
        graphics.horizontalLine(0, this.width - 1, this.panelBottom, 0x33FFFFFF);
        graphics.fill(0, this.panelTop, this.width, this.panelBottom, 0x77000000);
        graphics.outline(0, this.panelTop, this.width, this.panelBottom - this.panelTop, 0x99121212);

        this.extractEntries(graphics, mouseX, mouseY, a);
        this.extractScrollbar(graphics, mouseX, mouseY);
        this.extractSidebar(graphics, mouseX, mouseY);
        this.extractSearchBar(graphics);

        super.extractRenderState(graphics, mouseX, mouseY, a);

        this.extractOptionTooltip(graphics, mouseX, mouseY);
    }

    protected void extractEntries(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (this.entries == null) return;

        // widgets outside the panel must not look hovered
        int entryMouseX = this.isInViewport(mouseX, mouseY) ? mouseX : -1;
        int entryMouseY = this.isInViewport(mouseX, mouseY) ? mouseY : -1;

        graphics.enableScissor(this.viewportLeft, this.viewportTop, this.viewportRight, this.viewportBottom);
        for (var entry : this.entries) {
            if (entry.y() + entry.height() < this.viewportTop || entry.y() > this.viewportBottom) continue;
            entry.extract(graphics, entryMouseX, entryMouseY, a);
        }

        if (this.highlightedEntry != null) this.extractSearchHighlight(graphics, this.highlightedEntry);
        graphics.disableScissor();
    }

    protected void extractSearchHighlight(GuiGraphicsExtractor graphics, ConfigEntry entry) {
        float age = (System.currentTimeMillis() - this.highlightStartTime) / (float) HIGHLIGHT_MILLIS;
        if (age > 1) {
            this.highlightedEntry = null;
            return;
        }

        int mainColor = ConfigRendering.lerpArgb((float) Math.sin(age * Math.PI), 0x008D9BE0, 0x4C8D9BE0);
        int segmentWidth = (int) (entry.width() * .3f);
        int baseX = (int) (entry.x() - segmentWidth + ConfigRendering.easeCubic(age) * (entry.width() + segmentWidth * 2));

        ConfigRendering.horizontalGradient(graphics, baseX - segmentWidth, entry.y(), baseX, entry.y() + entry.height(), mainColor & 0x00FFFFFF, mainColor);
        ConfigRendering.horizontalGradient(graphics, baseX, entry.y(), baseX + segmentWidth, entry.y() + entry.height(), mainColor, mainColor & 0x00FFFFFF);
    }

    protected void extractScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int maxScroll = this.maxScroll();
        if (maxScroll <= 0) return;

        if (this.isInScrollbar(mouseX, mouseY) || this.draggingScrollbar) {
            this.lastScrollbarInteractTime = System.currentTimeMillis() + 1500;
        }

        // fade out 750ms after the last interaction
        float progress = Mth.clamp((this.lastScrollbarInteractTime - System.currentTimeMillis()) / 750f, 0, 1);
        int alpha = (int) ((float) Math.sin(progress * Math.PI / 2) * 0xA0);
        if (alpha <= 0) return;

        int viewportHeight = this.viewportBottom - this.viewportTop;
        int barHeight = Math.max(10, (int) (viewportHeight * (viewportHeight / (float) this.contentHeight)));
        int barY = this.viewportTop + (int) ((viewportHeight - barHeight) * (this.currentScroll / maxScroll));

        graphics.fill(this.viewportRight - SCROLLBAR_WIDTH, barY, this.viewportRight, barY + barHeight, alpha << 24);
    }

    protected void extractSidebar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.sections.isEmpty()) return;

        var toggle = Component.literal(this.sidebarOpen ? ">" : "<").withStyle(ChatFormatting.BOLD);
        graphics.text(this.font, toggle, this.containerRight - 2 - this.font.width(toggle), (this.panelTop + this.panelBottom) / 2 - 4, 0xFFFFFFFF, true);
        if (this.isOnSidebarToggle(mouseX, mouseY)) {
            graphics.requestCursor(CursorTypes.POINTING_HAND);
            graphics.setTooltipForNextFrame(this.font, Component.translatable("text.ewlib.config.sections_tooltip"), mouseX, mouseY);
        }

        int sidebarWidth = this.sidebarWidth();
        if (sidebarWidth <= 0) return;

        int left = this.containerRight;
        int center = left + (this.width - SIDE_PADDING - left) / 2;
        graphics.enableScissor(left, this.panelTop, left + sidebarWidth, this.panelBottom);

        var heading = Component.translatable("text.ewlib.config.sections").withStyle(ChatFormatting.UNDERLINE);
        graphics.text(this.font, heading, center - this.font.width(heading) / 2, this.panelTop + 15, 0xFFFFFFFF, false);

        int labelY = this.sectionLabelsTop();
        for (var section : this.sections) {
            int labelWidth = this.font.width(section.title());
            boolean hovered = mouseX >= center - labelWidth / 2 && mouseX < center + labelWidth / 2 + 1
                    && mouseY >= labelY && mouseY < labelY + 13 && mouseX >= left && mouseX < left + sidebarWidth;

            var text = hovered ? section.title().copy().withStyle(ChatFormatting.YELLOW) : section.title();
            graphics.text(this.font, text, center - labelWidth / 2, labelY + 2, 0xFFFFFFFF, false);
            if (hovered) graphics.requestCursor(CursorTypes.POINTING_HAND);

            labelY += 13;
        }

        graphics.disableScissor();
    }

    protected int sectionLabelsTop() {
        return (this.panelTop + this.panelBottom) / 2 - this.sections.size() * 13 / 2;
    }

    protected @Nullable SectionHeaderEntry sectionLabelAt(double mouseX, double mouseY) {
        int sidebarWidth = this.sidebarWidth();
        if (sidebarWidth <= 0 || mouseX < this.containerRight || mouseX >= this.containerRight + sidebarWidth) return null;

        int center = this.containerRight + (this.width - SIDE_PADDING - this.containerRight) / 2;
        int labelY = this.sectionLabelsTop();
        for (var section : this.sections) {
            int labelWidth = this.font.width(section.title());
            if (mouseX >= center - labelWidth / 2 && mouseX < center + labelWidth / 2 + 1 && mouseY >= labelY && mouseY < labelY + 13) {
                return section;
            }
            labelY += 13;
        }

        return null;
    }

    protected void extractSearchBar(GuiGraphicsExtractor graphics) {
        if (this.searchField == null) return;

        int centerY = this.height - BOTTOM_BAR_HEIGHT / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, SEARCH_ICON, SIDE_PADDING + 2, centerY - 8, 0, 0, 16, 16, 16, 16);

        int boxLeft = SIDE_PADDING + 20;
        int indicatorWidth = this.matchIndicator.getString().isEmpty() ? 0 : this.font.width(this.matchIndicator) + 10;
        int boxRight = this.searchField.getRight() + 3 + indicatorWidth;
        graphics.fillGradient(boxLeft, centerY - 7, boxRight, centerY + 8, 0xC0101010, 0xD0101010);

        if (indicatorWidth > 0) {
            graphics.text(this.font, this.matchIndicator, this.searchField.getRight() + 5, centerY - 4, 0xFFFFFFFF, false);
        }
    }

    protected void extractOptionTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!this.isInViewport(mouseX, mouseY) || this.isInScrollbar(mouseX, mouseY)) return;

        for (var component : this.optionComponents.values()) {
            if (mouseX < component.x() || mouseX >= component.x() + component.width()) continue;
            if (mouseY < component.y() || mouseY >= component.y() + component.height()) continue;

            var lines = this.tooltipFor(component.option());
            if (!lines.isEmpty()) graphics.setTooltipForNextFrame(this.font, lines, mouseX, mouseY);
            return;
        }
    }

    protected List<FormattedCharSequence> tooltipFor(Option<?> option) {
        var lines = new ArrayList<FormattedCharSequence>();

        var tooltipKey = option.translationKey() + ".tooltip";
        if (Language.getInstance().has(tooltipKey)) {
            lines.addAll(this.font.split(Component.translatable(tooltipKey), Integer.MAX_VALUE));
        }

        if (option.backingField().hasAnnotation(RestartRequired.class)) {
            lines.add(Component.literal("⏻ ").withColor(0xFAEA48)
                    .append(Component.translatable("text.ewlib.config.applies_after_restart").withStyle(ChatFormatting.GRAY))
                    .getVisualOrderText());
        }

        return lines;
    }

    // -------------
    // Input
    // -------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        this.layoutEntries();

        if (this.isOnSidebarToggle(event.x(), event.y())) {
            long elapsed = Math.min(System.currentTimeMillis() - this.sidebarToggleTime, SIDEBAR_ANIMATION_MILLIS);
            // reverse a running animation from where it currently is
            this.sidebarToggleTime = System.currentTimeMillis() - (SIDEBAR_ANIMATION_MILLIS - elapsed);
            this.sidebarOpen = !this.sidebarOpen;
            this.playClickSound();
            return true;
        }

        var section = this.sectionLabelAt(event.x(), event.y());
        if (section != null) {
            this.scrollTo(section);
            this.playClickSound();
            return true;
        }

        if (this.isInScrollbar(event.x(), event.y())) {
            this.draggingScrollbar = true;
            this.scrollToMouse(event.y());
            return true;
        }

        if (this.isInViewport(event.x(), event.y())) return super.mouseClicked(event, doubleClick);

        // clicks outside the panel must not reach rows that are scrolled out of view
        this.updateWidgetVisibility(false);
        try {
            return super.mouseClicked(event, doubleClick);
        } finally {
            this.updateWidgetVisibility(true);
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.draggingScrollbar) {
            this.scrollToMouse(event.y());
            return true;
        }

        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.draggingScrollbar = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.isInViewport(mouseX, mouseY)) {
            this.scrollBy(-scrollY * SCROLL_STEP);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    protected void scrollToMouse(double mouseY) {
        int viewportHeight = this.viewportBottom - this.viewportTop;
        double progress = Mth.clamp((mouseY - this.viewportTop) / viewportHeight, 0, 1);
        this.scrollOffset = progress * this.maxScroll();
        this.currentScroll = this.scrollOffset;
        this.lastScrollbarInteractTime = System.currentTimeMillis() + 1250;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_F && event.hasControlDown() && this.searchField != null) {
            this.setFocused(this.searchField);
            return true;
        }

        if (this.searchField != null && this.getFocused() == this.searchField && event.isConfirmation()) {
            this.search();
            return true;
        }

        return super.keyPressed(event);
    }

    protected void search() {
        if (this.searchField == null || this.entries == null) return;

        var query = this.searchField.getValue().toLowerCase(Locale.ROOT);
        if (query.isBlank()) return;

        if (this.currentMatches != null && this.currentMatches.query().equals(query)) {
            this.currentMatchIndex = this.currentMatches.matches().isEmpty() ? -1 : (this.currentMatchIndex + 1) % this.currentMatches.matches().size();
        } else {
            var splitQuery = query.split(" ");
            this.currentMatchIndex = 0;
            this.currentMatches = new SearchMatches(query, this.entries.stream()
                    .filter(entry -> Arrays.stream(splitQuery).allMatch(entry.searchText()::contains))
                    .toList());
        }

        if (this.currentMatches.matches().isEmpty()) {
            this.matchIndicator = Component.translatable("text.ewlib.config.search.no_matches");
            this.searchField.setTextColor(0xFFEB1D36);
            return;
        }

        this.matchIndicator = Component.translatable("text.ewlib.config.search.matches", this.currentMatchIndex + 1, this.currentMatches.matches().size());
        this.searchField.setTextColor(0xFF28FFBF);

        var match = this.currentMatches.matches().get(this.currentMatchIndex);
        this.highlightedEntry = match;
        this.highlightStartTime = System.currentTimeMillis();

        if (match.y() < this.viewportTop || match.y() + match.height() > this.viewportBottom) {
            this.scrollTo(match);
        }
    }

    protected void playClickSound() {
        AbstractWidget.playButtonClickSound(this.minecraft.getSoundManager());
    }

    // -------------
    // Closing
    // -------------

    @Override
    public void onClose() {
        boolean shouldRestart = false;
        for (var entry : this.optionComponents.entrySet()) {
            if (!entry.getKey().backingField().hasAnnotation(RestartRequired.class)) continue;
            if (!entry.getValue().isValid() || !entry.getValue().hasChanged()) continue;

            shouldRestart = true;
        }

        this.minecraft.gui.setScreen(shouldRestart ? new RestartRequiredScreen(this.parent) : this.parent);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void removed() {
        for (var entry : this.optionComponents.entrySet()) {
            if (!entry.getValue().isValid()) continue;
            ((Option) entry.getKey()).set(entry.getValue().parsedValue());
        }

        super.removed();
    }

    protected record SearchMatches(String query, List<ConfigEntry> matches) {}

    static {
        DEFAULT_FACTORIES.put(option -> ConfigTypes.isNumberType(option.clazz()), OptionComponentFactory.NUMBER);
        DEFAULT_FACTORIES.put(option -> option.clazz() == String.class, OptionComponentFactory.STRING);
        DEFAULT_FACTORIES.put(option -> option.clazz() == Boolean.class || option.clazz() == boolean.class, OptionComponentFactory.BOOLEAN);
        DEFAULT_FACTORIES.put(option -> option.clazz() == Identifier.class, OptionComponentFactory.IDENTIFIER);
        DEFAULT_FACTORIES.put(option -> option.clazz().isEnum(), OptionComponentFactory.ENUM);
    }
}
