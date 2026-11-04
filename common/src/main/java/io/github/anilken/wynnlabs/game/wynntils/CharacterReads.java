package io.github.anilken.wynnlabs.game.wynntils;

import com.wynntils.core.components.Models;
import com.wynntils.models.abilities.AbilityModel;
import com.wynntils.models.character.type.ClassType;
import com.wynntils.models.elements.type.Skill;
import com.wynntils.models.players.type.PlayerRank;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.TomeMemory;
import io.github.anilken.wynnlabs.game.Wyn.Feature;
import io.github.anilken.wynnlabs.game.WynntilsItems;
import io.github.anilken.wynnlabs.model.Capped;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.model.SkillKind;

import java.util.Optional;

import static io.github.anilken.wynnlabs.game.wynntils.Bridge.guard;

/**
 * Wynntils reads of the character (level, class, health, mana, ...) and its skill points.
 */
public final class CharacterReads {

    private CharacterReads() {}

    // ---- character ----

    public static int level() {
        if (!GameState.current().running()) {
            return GameState.current().level();
        }
        return guard(Feature.CHARACTER, () -> Models.CombatXp.getCombatLevel().current(), 0);
    }

    public static Capped xp() {
        return guard(Feature.CHARACTER, () -> WynntilsItems.capped(Models.CombatXp.getXp()), Capped.EMPTY);
    }

    public static Klass classType() {
        if (!GameState.current().running()) {
            return GameState.current().classType();
        }
        return guard(Feature.CHARACTER, () -> {
            ClassType c = Models.Character.getClassType();
            return c == null ? Klass.NONE : WynntilsItems.klass(c.name());
        }, Klass.NONE);
    }

    public static boolean hasCharacter() {
        return guard(Feature.CHARACTER, () -> Models.Character.hasCharacter(), false);
    }

    public static String characterId() {
        return guard(Feature.CHARACTER, () -> Models.Character.getId(), "");
    }

    /**
     * The account's supporter rank as Wynntils read it from the rank menu ("VIP", "HERO_PLUS" …,
     * "NONE" without one), or null while it is not known: no scan yet this session and no copy
     * kept from an earlier one. Staff ranks do not show here; the supporter rank under them does.
     */
    public static String supporterRank() {
        return guard(Feature.CHARACTER, () -> {
            PlayerRank r = Models.Account.getSupporterRank();
            return r == null || r == PlayerRank.NONE && !Models.Account.hasScannedRankInfo() ? null : r.name();
        }, null);
    }

    public static boolean reskinned() {
        return guard(Feature.CHARACTER, () -> Models.Character.isReskinned(), false);
    }

    public static Optional<Capped> health() {
        return guard(Feature.STATS, () -> Models.CharacterStats.getHealth().map(WynntilsItems::capped), Optional.empty());
    }

    public static Optional<Capped> mana() {
        return guard(Feature.STATS, () -> Models.CharacterStats.getMana().map(WynntilsItems::capped), Optional.empty());
    }

    /**
     * A Shaman's Blood Pool in percent, from its boss bar (Wynntils' blood pool bar), or -1 while
     * the bar is not shown.
     */
    public static int bloodPool() {
        return guard(Feature.STATS, () -> {
            if (!AbilityModel.bloodPoolBar.isActive()) {
                return -1;
            }
            var p = AbilityModel.bloodPoolBar.getBarProgress();
            return p == null || p.value() == null ? -1 : p.value().current();
        }, -1);
    }

    /**
     * The full health of the mob you are hitting (Wynntils' focused mob, from the game's damage
     * bar: its health now over its percent left), 0 when none or nothing was hit in the last 10 s.
     */
    public static long focusedMobMaxHealth() {
        return guard(Feature.STATS, () -> {
            if (System.currentTimeMillis() - Models.Combat.getLastDamageDealtTimestamp() > 10_000) {
                return 0L;
            }
            long hp = Models.Combat.getFocusedMobHealth();
            var pct = Models.Combat.getFocusedMobHealthPercent();
            if (hp <= 0 || pct == null || pct.max() <= 0) {
                return Math.max(0L, hp);
            }
            return Math.round(hp * (double) pct.max() / Math.max(1, pct.current()));
        }, 0L);
    }

    public static int emeralds() {
        return guard(Feature.ITEMS, () -> Models.Emerald.getAmountInInventory(), 0);
    }

    // ---- skill points ----

    public static int assignedPoints(SkillKind s) {
        return guard(Feature.SKILLS, () -> Models.SkillPoint.getAssignedSkillPoints(skill(s)), 0);
    }

    public static int gearPoints(SkillKind s) {
        return guard(Feature.SKILLS, () -> Models.SkillPoint.getGearSkillPoints(skill(s)), 0);
    }

    /**
     * Tome skill points of a skill: Wynntils' number when it read any (only its own menu query
     * fills them), else the tomes remembered from the Mastery Tomes menu the player opened.
     */
    public static int tomePoints(SkillKind s) {
        int own = wynntilsTomePoints(s);
        if (own != 0 || !GameState.current().running()) {
            return own;
        }
        boolean any = false;
        for (SkillKind k : SkillKind.values()) {
            any |= wynntilsTomePoints(k) != 0;
        }
        return TomeMemory.points(s, own, any);
    }

    private static int wynntilsTomePoints(SkillKind s) {
        return guard(Feature.SKILLS, () -> Models.SkillPoint.getTomeSkillPoints(skill(s)), 0);
    }

    public static int setBonusPoints(SkillKind s) {
        return guard(Feature.SKILLS, () -> Models.SkillPoint.getSetBonusSkillPoints(skill(s)), 0);
    }

    public static int totalPoints(SkillKind s) {
        return guard(Feature.SKILLS, () -> Models.SkillPoint.getTotalSkillPoints(skill(s)), 0);
    }

    private static Skill skill(SkillKind s) {
        return Skill.valueOf(s.name());
    }
}
