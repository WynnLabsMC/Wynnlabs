package io.github.anilken.wynnlabs.advice;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** "Advice line only": the verdict line and a red warning stay, everything else goes. */
class AdviceBriefTest {

    @Test
    void onlyTheVerdictAndRedWarningsStay() {
        Component head = Component.literal("✦ Advice: ").append(Component.literal("KEEP").withStyle(ChatFormatting.AQUA));
        Component reason = Component.literal("Keep it: nothing better in the bag").withStyle(ChatFormatting.GRAY);
        Component cannot = Component.literal("⚠ Can't equip: Lv 90 (you: 84)").withStyle(ChatFormatting.RED);
        Component next = Component.literal("Next upgrade: Insomniac (Lv 111)").withStyle(ChatFormatting.GRAY);
        Component price = Component.literal("◆ Price: ~25 EB").withStyle(ChatFormatting.GOLD);
        List<Component> out = List.of(Component.empty(), head, reason, cannot, next, price);
        assertEquals(List.of(head, cannot), Advice.brief(out, "✦ Advice: "));
    }
}
