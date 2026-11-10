package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.Build;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.ItemJson;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.market.Shopping;
import io.github.anilken.wynnlabs.market.Vault;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.model.SkillKind;
import io.github.anilken.wynnlabs.model.Slot;
import io.github.anilken.wynnlabs.model.Tier;
import io.github.anilken.wynnlabs.quests.QuestBook;
import io.github.anilken.wynnlabs.search.BoxMarket;
import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.search.ItemIndex;
import io.github.anilken.wynnlabs.search.ItemWiki;
import io.github.anilken.wynnlabs.search.Kind;
import io.github.anilken.wynnlabs.search.Source;
import io.github.anilken.wynnlabs.search.SourceText;
import io.github.anilken.wynnlabs.search.Sources;
import io.github.anilken.wynnlabs.search.TrackedMobs;
import io.github.anilken.wynnlabs.search.UseList;
import io.github.anilken.wynnlabs.search.Uses;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The search card of an item ({@link CardView}): its icon in an HD slot, its kind and level, what
 * it asks of the player (✔/✘) and the build gain; "How do I get it" from where the player stands
 * with what they hold; Track, "Open on map", the Trade Market and the wiki; and the sections under
 * the rows, always open: "Yours" (bag and bank pages with counts), "Stats" (gear's roll ranges, an
 * ingredient's modifiers and professions) and "Related" (the set's other pieces, items from the
 * same place, the quest that gives it, crafting for an ingredient). One per choice.
 */
final class ItemCard extends MenuPaint implements CardView {

    private final SearchCardPanel card;
    private final SearchTab tab;
    private final Entry e;
    /** Names of related items (set pieces first), and the quest the item comes from (or null). */
    private List<String> related = List.of();
    private String quest;
    private Sources.Owned have = Sources.Owned.NONE;
    /** The least stars the loot chest row's chests have (1 to 4): the item's own by default, cycled by its chip. */
    private int chestStars = 1;
    /** What wearing it adds to the build, when it is a piece the road map would buy; else null. */
    private Double gain;
    /** What the item is used for (crafts, an ingredient's professions, mount feed), worked out when it is chosen. */
    private Uses.Result uses;

    ItemCard(SearchCardPanel card, Entry e) {
        super(card.tab.menu);
        this.card = card;
        this.tab = card.tab;
        this.e = e;
    }

    @Override
    public String key() {
        return e.key();
    }

    @Override
    public String name() {
        return e.name();
    }

    @Override
    public String title() {
        return SearchResultsPanel.kindText(e);
    }

    @Override
    public void select() {
        have = owned(e.name());
        uses = Uses.of(e);
        chestStars = Sources.chestStars(e);
        Shopping.Pick pick = e.kind() == Kind.GEAR ? Shopping.find(e.name()) : null;
        gain = pick == null || pick.gain() <= 0 ? null : pick.gain();
        ItemIndex index = ItemIndex.get();
        List<String> out = new ArrayList<>();
        for (String n : index.setPieces(e)) {
            if (!n.equals(e.name())) {
                out.add(n);
            }
        }
        // items from the same named place (the first one the data gives)
        String place = null;
        for (Entry.Origin o : e.origins()) {
            if (o.name() != null && !o.name().isBlank()) {
                place = o.name();
                break;
            }
        }
        quest = e.quest();
        for (Entry.Origin o : e.origins()) {
            if (Source.Type.of(o.type()) == Source.Type.QUEST && o.name() != null) {
                quest = o.name();
            }
        }
        if (place != null) {
            for (Entry other : index.entries()) {
                if (out.size() >= 8) {
                    break;
                }
                if (other != e && !out.contains(other.name())) {
                    for (Entry.Origin o : other.origins()) {
                        if (place.equals(o.name())) {
                            out.add(other.name());
                            break;
                        }
                    }
                }
            }
        }
        related = out;
    }

    /** What the player holds of this item: the bag's count and each bank page's (by page label). */
    static Sources.Owned owned(String name) {
        int bag = 0;
        try {
            for (ItemStack s : GameState.current().inventory()) {
                if (!s.isEmpty() && ItemJson.clean(s.getHoverName().getString()).equalsIgnoreCase(name)) {
                    bag += s.getCount();
                }
            }
        } catch (RuntimeException ignored) {
            // no bag yet
        }
        Map<String, Integer> bank = new LinkedHashMap<>();
        Vault.Group gr = Vault.find(name);
        if (gr != null) {
            for (Vault.Stored st : gr.places()) {
                bank.merge(Vault.label(st.page()), st.count(), Integer::sum);
            }
        }
        return new Sources.Owned(bag, bank);
    }

    // ---- the head ----

    @Override
    public void picture(GuiGraphics g, int x, int y, int size, boolean hover) {
        Kit.slot(g, x, y, size, 0);
        ItemStack stack = tab.icons.of(e);
        g.renderItem(stack, x + (size - 16) / 2, y + (size - 16) / 2);
        if (hover) {
            menu.hits.add(new MenuHit(x, y, size, size, stack, null, null));
        }
    }

    @Override
    public int ink() {
        return SearchResultsPanel.colour(e);
    }

    @Override
    public Head head() {
        return new Head(CardView.kindLine(e, SearchResultsPanel.kindText(e)), needs(),
                gain == null ? null : L.t("wynnlabs.search.gain", dec(gain, 1)));
    }

    // ---- the rows ----

    @Override
    public String rowsTitle() {
        return L.t("wynnlabs.search.how");
    }

    /** From where the player stands, with what they hold and the chest stars asked for. */
    @Override
    public List<Source> rows(double[] pos) {
        Prices.Price price = e.tradable() ? Prices.of(e.name()) : null;
        return Sources.of(e, ItemIndex.get().places(), have, price, pos, chestStars);
    }

    /** The source's text: the wiki's words once it arrived. */
    @Override
    public String rowText(Source s, ItemWiki.Info wiki) {
        return SourceText.row(s, wiki);
    }

    @Override
    public Entry link(Source s) {
        return ThingCard.linked(s);
    }

    /** The loot chest row's chip: the least stars a chest needs, ★1+ … ★4; a click goes to the next (1 → 2 → 3 → 4 → 1). */
    @Override
    public RowChip rowChip(Source s) {
        if (s.type() != Source.Type.LOOT_CHEST) {
            return null;
        }
        return new RowChip(chestStars >= 4 ? "★4" : "★" + chestStars + "+", L.t("wynnlabs.search.stars_tip"), () -> {
            chestStars = chestStars % 4 + 1;
            card.rowsChanged();
        });
    }

    @Override
    public String wikiPage() {
        return e.name();
    }

    // ---- the actions ----

    /** Track (its drops on the ground), "Open on map", the Trade Market's listings for a tradable item, the wiki. */
    @Override
    public List<SearchCardPanel.Action> actions() {
        return List.of(card.trackChip(TrackedMobs.items(), e.name()), card.mapChip(),
                new SearchCardPanel.Action("shopping-cart", L.t("wynnlabs.search.market"), e.tradable(), false, this::market),
                card.wikiChip(ItemWiki.url(e.name())));
    }

    /** The Trade Market window the T key opens over a container, for this item: its listings and rolls. */
    private void market() {
        Tier rarity;
        try {
            rarity = Tier.valueOf(e.tier().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            rarity = Tier.NORMAL;
        }
        var item = new BoxMarket.Item(e.name(), Slot.UNKNOWN, rarity, e.level(), e.klass(), 0, true);
        Minecraft.getInstance().setScreen(new BoxScreen(new BoxScreen.Single(item, null, false), tab.icons.of(e), menu));
    }

    /** "Lv 73 ✔ · Str 40 ✘ · Shaman ✔ · quest ✔": what the item asks against what the player has. */
    private Component needs() {
        MutableComponent out = Component.empty();
        GameState gs = GameState.current();
        List<Object[]> parts = new ArrayList<>();               // {text, Boolean met}
        if (e.level() > 0) {
            parts.add(new Object[] {"Lv " + e.level(), gs.level() <= 0 ? null : gs.level() >= e.level()});
        }
        if (e.klass() != Klass.NONE) {
            Klass mine = gs.classType();
            parts.add(new Object[] {nice(e.klass().name()), mine == null || mine == Klass.NONE ? null : mine == e.klass()});
        }
        e.skills().forEach((k, v) -> {
            SkillKind s = skill(k);
            if (s != null) {
                parts.add(new Object[] {s.displayName().substring(0, 3) + " " + v, Build.totalPoints(s) >= v});
            }
        });
        if (e.quest() != null) {
            Boolean done = null;
            for (QuestBook.Entry q : gs.quests()) {
                if (q.name().equalsIgnoreCase(e.quest())) {
                    done = "COMPLETED".equals(q.status());
                }
            }
            parts.add(new Object[] {L.t("wynnlabs.search.need_quest"), done});
        }
        for (int i = 0; i < parts.size(); i++) {
            Object[] p = parts.get(i);
            Boolean met = (Boolean) p[1];
            if (i > 0) {
                out.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
            }
            out.append(Component.literal((String) p[0]).withStyle(ChatFormatting.GRAY));
            if (met != null) {
                out.append(Component.literal(met ? " ✔" : " ✘").withStyle(met ? ChatFormatting.DARK_GREEN : ChatFormatting.RED));
            }
        }
        return out;
    }

    private static SkillKind skill(String key) {
        try {
            return SkillKind.valueOf(key.toUpperCase(Locale.ROOT).replace("DEFENSE", "DEFENCE"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---- drawing ----

    /** "Yours", "Stats" and "Related", always open (the card scrolls); returns the y under them. */
    @Override
    public int sections(GuiGraphics g, int x, int y, int w, int mx, int my) {
        int count = have.bag() + have.bank().values().stream().mapToInt(Integer::intValue).sum();
        int cy = card.sources.title(g, L.t("wynnlabs.search.sec_owned"), count > 0 ? String.valueOf(count) : null, x, y, w);
        if (count == 0) {
            cy = card.sources.line(g, L.t("wynnlabs.search.owned_none"), x, cy, w, Theme.INK_3);
        }
        if (have.bag() > 0) {
            cy = card.sources.line(g, L.t("wynnlabs.search.src.bag", have.bag()), x, cy, w, Theme.INK_2);
        }
        for (Map.Entry<String, Integer> p : have.bank().entrySet()) {
            cy = card.sources.line(g, L.t("wynnlabs.search.src.bank", p.getKey(), p.getValue()), x, cy, w, Theme.INK_2);
        }
        cy = card.sources.title(g, L.t("wynnlabs.search.sec_stats"), null, x, cy + Theme.SP_5, w);
        cy = stats(g, e, x, cy, w);
        if (uses != null && !uses.empty()) {
            cy = uses(g, x, cy + Theme.SP_5, w, mx, my);
        }
        cy = card.sources.title(g, L.t("wynnlabs.search.sec_related"), null, x, cy + Theme.SP_5, w);
        return related(g, e, x, cy, w, mx, my);
    }

    private int stats(GuiGraphics g, Entry e, int x, int y, int w) {
        int cy = y;
        if (e.kind() == Kind.INGREDIENT && !e.professions().isEmpty()) {
            List<String> prof = new ArrayList<>();
            e.professions().forEach(p -> prof.add(SearchResultsPanel.words(p)));
            cy = card.sources.line(g, L.t("wynnlabs.search.professions", String.join(", ", prof)), x, cy, w, Theme.INK_2);
        }
        String wanted = tab.stat;
        for (Entry.Stat s : e.stats()) {
            String range = s.min() == s.max() ? signed(s.min()) : signed(s.min()) + " … " + signed(s.max());
            cy = pair(g, SearchResultsPanel.statName(s.key()), range, x, cy, w, s.max() < 0 && s.min() < 0, s.key().equals(wanted));
        }
        for (Map.Entry<String, Integer> m : e.modifiers().entrySet()) {
            cy = pair(g, SearchResultsPanel.statName(m.getKey()), signed(m.getValue()), x, cy, w, m.getValue() < 0, false);
        }
        if (cy == y) {
            cy = card.sources.line(g, L.t("wynnlabs.search.stats_none"), x, cy, w, Theme.INK_3);
        }
        return cy;
    }

    /**
     * "Uses" with how many there are and "All uses ›" at the title's end (the list of them, in
     * place of the search), then their names in a line or two: "Ring · Bracelet · Mount feed".
     */
    private int uses(GuiGraphics g, int x, int y, int w, int mx, int my) {
        List<UseList.Line> lines = UseList.of(uses, n -> 0, p -> 0);
        String all = L.t("wynnlabs.search.uses_all");
        int lw = Type.BODY.width(font(), all);
        int cy = card.sources.title(g, L.t("wynnlabs.search.sec_uses"), String.valueOf(lines.size()), x, y, w);
        if (!e.name().equals(tab.usesOf)) {
            // (the list beside the card is that view already while it shows this item's uses)
            card.sources.link(g, all, x + w - lw, y, lw, mx, my, () -> tab.showUses(e.name()));
        }
        StringBuilder row = new StringBuilder();
        for (UseList.Line l : lines) {
            String name = UseCard.title(l);
            if (row.length() > 0 && Type.BODY.width(font(), row + " · " + name) > w) {
                cy = card.sources.line(g, row.toString(), x, cy, w, Theme.INK_2);
                row.setLength(0);
            }
            row.append(row.length() > 0 ? " · " : "").append(name);
        }
        return card.sources.line(g, row.toString(), x, cy, w, Theme.INK_2);
    }

    /** The quest it comes from, crafting for an ingredient, the set's pieces and items of the same place, as chips. */
    private int related(GuiGraphics g, Entry e, int x, int y, int w, int mx, int my) {
        List<SearchCardPanel.Action> chips = new ArrayList<>();
        if (quest != null) {
            String q = quest;
            chips.add(new SearchCardPanel.Action("script", L.t("wynnlabs.search.quest_link", q), true, false, () -> {
                menu.questsTab.questSelected = q;
                menu.openTab(2);
            }));
        }
        if (e.kind() == Kind.INGREDIENT) {
            chips.add(new SearchCardPanel.Action("hammer", L.t("wynnlabs.search.craft_link"), true, false,
                    () -> menu.openTab(RoadmapScreen.TAB_CRAFT)));
        }
        for (String n : related) {
            // searched for, as the search key does: an item outside the current results would
            // otherwise give way to the first result again on the next frame
            chips.add(new SearchCardPanel.Action("box", n, true, false, () -> tab.show(n)));
        }
        if (chips.isEmpty()) {
            return card.sources.line(g, L.t("wynnlabs.search.related_none"), x, y, w, Theme.INK_3);
        }
        int cx = x;
        int cy = y;
        for (SearchCardPanel.Action c : chips) {
            String label = Type.BODY.fit(font(), c.label(), w - 2 * Theme.SP_4 - 10);
            int cw = Math.min(w, Ui.iconChipWidth(font(), c.icon(), label, false));
            if (cx + cw > x + w && cx > x) {
                cx = x;
                cy += Ui.CHIP_H + Theme.SP_2;
            }
            Ui.iconChip(g, font(), c.icon(), label, false, cx, cy, cw, c.on(), card.over(mx, my) ? mx : -1, my);
            card.click(cx, cy, cw, Ui.CHIP_H, c.run());
            cx += cw + Theme.SP_2;
        }
        return cy + Ui.CHIP_H;
    }

    private int pair(GuiGraphics g, String name, String value, int x, int y, int w, boolean bad, boolean wanted) {
        int vw = Type.BODY.width(font(), value);
        if (wanted) {
            Kit.rowLit(g, x - Theme.SP_2, y - Theme.SP_2, w + 2 * Theme.SP_2, Type.BODY.lineHeight + 2 * Theme.SP_2);
        }
        Type role = wanted ? Type.LABEL : Type.BODY;
        role.draw(g, font(), Type.BODY.fit(font(), name, w - vw - Theme.SP_4), x, y, wanted ? Theme.INK : Theme.INK_2);
        role.drawRight(g, font(), value, x + w, y, bad ? Theme.BAD : Theme.GOOD);
        return y + Type.BODY.lineHeight + Theme.SP_1 + (wanted ? Theme.SP_2 * 2 : 0);
    }

    private static String signed(int v) {
        return v > 0 ? "+" + v : String.valueOf(v);
    }
}
