package com.sheepfromheaven.minenow.inhabitant;

import com.sheepfromheaven.minenow.network.ModNetwork;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InhabitantIssuesNetworkTest {

    /**
     * Reproduces the "issues button always shows red !" bug: {@code INHABITANT_FIELD_SEP} is the
     * empty string, so splitting on it (as the client does to rebuild the issues list) breaks any
     * non-empty issues payload apart into individual characters instead of the original entries -
     * the round trip must preserve the original list, not shred it.
     */
    @Test
    void issuesListSurvivesNetworkRoundTrip() {
        var original = List.of("Needs Axe", "There is no Bed in my house");

        var encoded = String.join(ModNetwork.INHABITANT_FIELD_SEP, original);
        var decoded = encoded.isEmpty() ? List.<String>of()
                : List.of(encoded.split(Pattern.quote(ModNetwork.INHABITANT_FIELD_SEP), -1));

        assertEquals(original, decoded, "Issues list should survive the network round trip unchanged");
    }
}
