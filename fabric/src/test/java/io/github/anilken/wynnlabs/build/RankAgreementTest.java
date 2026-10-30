package io.github.anilken.wynnlabs.build;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The reference benchmarks' reading of the comparisons players write next to their links. */
class RankAgreementTest {

    private static List<String> read(String text) {
        return RankAgreement.claims(1, 0, text).stream()
                .map(c -> (c.sign() > 0 ? "+" : "-") + c.dim().name().toLowerCase(java.util.Locale.ROOT)).toList();
    }

    @Test
    void damageAndEhpClaims() {
        assertEquals(List.of("+dps", "-ehp"), read("Halcyon mage, less EHP more DPS; has tomes"));
        assertEquals(List.of("+dps", "-ehp"), read("Alternative build (more dps oriented loosing ehp) :"));
        assertEquals(List.of("+dps", "-ehp"), read("a build with a bit more dps but even more glassy (lol)."));
        assertEquals(List.of("+ehp"), read("Idol variant: same damage, better EHP if tomes cancel the -hpr"));
        assertEquals(List.of("+ehp"), read("Masterwork Archangel, 'even better sustain'"));
        assertEquals(List.of("+dps"), read("i have this if u need more damag"));
    }

    @Test
    void betterOverall() {
        assertEquals(List.of("+overall"), read("Also heres a slightly better uncrafted warp rift build"));
    }

    @Test
    void questionsAndRepliesToOthersClaimNothing() {
        assertTrue(read("what's better in general").isEmpty());
        assertTrue(read("is this one better?").isEmpty());
        assertTrue(read("@someone my build is better").isEmpty());
        assertTrue(read("Masterwork Cataclysm, 2 crafted, 'more QOL'").isEmpty());
    }

    @Test
    void agreementFollowsTheClaimedDimension() {
        RankAgreement.SameTree glass = new RankAgreement.SameTree(1.0, 1.0, 900, 40000, 5, 1, 1.0);
        RankAgreement.SameTree tank = new RankAgreement.SameTree(1.0, 1.0, 600, 90000, 5, 1, 1.0);
        RankAgreement.Claim moreDps = new RankAgreement.Claim(1, 0, RankAgreement.Dim.DPS, 1);
        RankAgreement.Claim lessEhp = new RankAgreement.Claim(1, 0, RankAgreement.Dim.EHP, -1);
        assertEquals(1.0, RankAgreement.agree(moreDps, glass, tank));
        assertEquals(1.0, RankAgreement.agree(lessEhp, glass, tank));
        assertEquals(0.0, RankAgreement.agree(moreDps, tank, glass));
        assertEquals(0.5, RankAgreement.agree(new RankAgreement.Claim(1, 0, RankAgreement.Dim.OVERALL, 1), glass, tank));
    }
}
