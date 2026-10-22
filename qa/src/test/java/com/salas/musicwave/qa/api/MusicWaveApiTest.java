package com.salas.musicwave.qa.api;

import com.intuit.karate.junit5.Karate;

class MusicWaveApiTest {

    @Karate.Test
    Karate blackBoxApiRegression() {
        return Karate.run(
                "classpath:features/system-status.feature",
                "classpath:features/downloads-black-box.feature");
    }
}
