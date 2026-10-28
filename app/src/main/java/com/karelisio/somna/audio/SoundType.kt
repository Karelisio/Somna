package com.karelisio.somna.audio

/** Built-in procedural sounds. Alarm sounds wake you up gently, the others help you fall asleep. */
enum class SoundType(val label: String, val forAlarm: Boolean) {
    BIRDS("Oiseaux", true),
    CHIMES("Carillon", true),
    WAVES("Vagues", true),
    RAIN("Pluie", false),
    WHITE_NOISE("Bruit blanc", false),
    FIRE("Feu de cheminée", false);

    companion object {
        val alarmSounds: List<SoundType> = entries.filter { it.forAlarm }
        val sleepSounds: List<SoundType> = entries.filterNot { it.forAlarm }
    }
}
