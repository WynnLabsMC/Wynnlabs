package io.github.anilken.wynnlabs.market;

import io.github.anilken.wynnlabs.build.Exclusive;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.quests.QuestBook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Ornate Shadow pieces are sold in Lutho for one Corrupted Soul Token each, and the token's one
 * source is the quest A Hunter's Calling: they read as that quest's reward, wait for the quest in
 * the book, and stay one exclusive group. Other merchant gear is unchanged.
 */
class QuestTokenSourcesTest {

    private static final List<String> ORNATE = List.of("Ornate Shadow Cowl", "Ornate Shadow Cloak",
            "Ornate Shadow Cover", "Ornate Shadow Cloud");

    @BeforeEach
    void install() throws Exception {
        FixtureState.load(Fixtures.root().resolve("shaman-44")).install("en");
    }

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    private static ItemData gear(String name) {
        return GameState.current().allGear().filter(i -> i.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void ornateShadowIsTheHuntersCallingToken() {
        for (String name : ORNATE) {
            ItemData info = gear(name);
            assertTrue(ShoppingSources.questToken(info), name);
            assertEquals("QUEST_TOKEN: A Hunter's Calling", ShoppingSources.origin(info), name);
            assertEquals("quest", ShoppingSources.kind(info), name);
            assertFalse(Shopping.tradable(info), name);
            assertTrue(ShoppingSources.effort(info), name);
            assertEquals("Ornate Shadow", Exclusive.group(info), name);
            // the fixture's book has A Hunter's Calling, not done
            QuestBook.Entry q = ShoppingSources.questPending(GameState.current(), info);
            assertNotNull(q, name);
            assertEquals("A Hunter's Calling", q.name());
        }
        String where = ShoppingSources.where(ShoppingSources.origin(gear("Ornate Shadow Cowl")));
        assertTrue(where.contains("Lutho") && where.contains("Corrupted Soul Token") && where.contains("A Hunter's Calling"),
                where);
        assertEquals("Ornate Shadow", Exclusive.group("Ornate Shadow Garb"));
    }

    @Test
    void otherMerchantGearIsNotATokenBuy() {
        ItemData f = gear("Fuunyet");
        assertFalse(ShoppingSources.questToken(f));
        assertEquals("MERCHANT: Entamis Village", ShoppingSources.origin(f));
        // its quest (if any) is its own requirement, not a token's
        QuestBook.Entry q = ShoppingSources.questPending(GameState.current(), f);
        assertTrue(q == null || f.requirements().quest().isPresent());
        assertNull(ShoppingSources.questPending(GameState.current(), gear("Prologue")));
    }
}
