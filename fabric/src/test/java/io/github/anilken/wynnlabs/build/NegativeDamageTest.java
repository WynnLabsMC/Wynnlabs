package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.Klass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Damage never goes below nothing. Silent Ballet, a pacifist relik, carries -40000 % Spell Damage
 * and -40000 raw Spell Damage: in game its spells deal nothing. The model multiplied each spell
 * by (1 - 400) instead, so a Discord Ritualist healer with it (Last Stand, Chelation, Statue,
 * Healing Efficiency 53) came out at about -442k DPS. An element's damage stops at 0, as
 * WynnBuilder's does.
 */
class NegativeDamageTest {

    /** A Discord Lv 120 Ritualist healer: Last Stand, Chelation, Second Wind, Statue, Silent Ballet. */
    static final String RITUALIST = "https://wynnbuilder.github.io/builder/#CY0j2HaJuv27nGjHuCI0L20YpB6l+PZzkfEGu5";

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    @Test
    void aHugeNegativeSpellDamageSilencesTheSpell() {
        double[] c = {0, 0.5, 0, 0, 0, 0.5};
        double[] bonus = new double[6];
        assertEquals(0, Damage.hit(Map.of("spellDamage", -40000.0, "rawSpellDamage", -40000.0), c, 500, bonus, true, null), 1e-9);
        // the main attack is not touched by the spell IDs
        assertTrue(Damage.hit(Map.of("spellDamage", -40000.0), c, 500, bonus, false, null) > 0);
        // one element under zero leaves the other's damage as it is
        double[] el = new double[6];
        double air = Damage.hit(Map.of("airDamage", 20.0), new double[] {0, 0, 0, 0, 0, 0.5}, 500, bonus, true, null);
        assertEquals(air, Damage.hit(Map.of("airDamage", 20.0, "earthDamage", -500.0), c, 500, bonus, true, el), 1e-9);
        assertEquals(0, el[1], 1e-9);
    }

    @Test
    void aRitualistWithSilentBalletHasNoNegativeDps() throws Exception {
        WynnBuilderLink.Build b = WynnBuilderLink.decode(RITUALIST);
        assertNotNull(b);
        ReferenceBuilds.installPlain();
        Klass k = ReferenceBuilds.classOf(b);
        assertEquals(Klass.SHAMAN, k);
        ReferenceBuilds.install(k, b.level(), "Ritualist", "HEALER");
        ReferenceBuilds.LinkSet ls = ReferenceBuilds.set(b);
        assertEquals("Silent Ballet", ls.set().get("WEAPON").name());
        TreeModel.Overlay own = LinkImport.overlay(LinkImport.of(GameState.current(), b));
        int budget = Math.max(ReferenceBuilds.earned(b.level()), ReferenceBuilds.need(ls.set().values()));
        Build.Eval e = TreeModel.with(own, () -> Build.allocateBest(ls.set(), b.level(), budget));
        assertNotNull(e);
        List<Damage.Piece> armour = new ArrayList<>();
        ls.set().forEach((slot, p) -> {
            if (!slot.equals("WEAPON") && p != null) {
                armour.add(p);
            }
        });
        Damage.Detail d = TreeModel.with(own, () -> Damage.detail(armour, ls.set().get("WEAPON"), e.allocation(), b.level()));
        Damage.Result r = d.result();
        Fixtures.note("Silent Ballet Ritualist: spell " + r.spellDps() + " main " + r.mainDps() + " objective " + r.objective()
                + " heal " + r.healPerSecond());
        assertEquals(0, r.spellDps(), 1e-9, "its spells deal nothing");
        assertTrue(r.mainDps() > 0, "its main attack still hits");
        assertTrue(r.objective() > 0);
        for (Damage.SpellPart sp : d.breakdown().spells()) {
            assertTrue(sp.perCast() >= 0, sp.toString());
        }
        for (Sources.Source s : d.breakdown().sources()) {
            assertTrue(s.dps() >= 0, s.name() + " " + s.dps());
        }
        assertTrue(d.breakdown().total() >= 0);
    }
}
