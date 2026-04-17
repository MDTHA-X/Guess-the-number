package com.iinaya.gtnonline

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

enum class SoundEffect {
    SUCCESS,
    ERROR,
    YOUR_TURN,
    OPPONENT_TURN,
    WIN,
    LOSE,
    DRAW,
}

class GameSoundPlayer {
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 95)
    private val handler = Handler(Looper.getMainLooper())
    private val queued = mutableListOf<Runnable>()

    fun play(effect: SoundEffect) {
        clearQueue()

        when (effect) {
            SoundEffect.SUCCESS -> playTone(ToneGenerator.TONE_CDMA_CONFIRM, 140)
            SoundEffect.ERROR -> playTone(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 220)
            SoundEffect.YOUR_TURN -> playTone(ToneGenerator.TONE_PROP_BEEP2, 150)
            SoundEffect.OPPONENT_TURN -> playTone(ToneGenerator.TONE_PROP_BEEP, 120)
            SoundEffect.WIN -> playSequence(
                listOf(
                    Triple(ToneGenerator.TONE_CDMA_ABBR_ALERT, 260, 0L),
                    Triple(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 230, 280L),
                    Triple(ToneGenerator.TONE_CDMA_HIGH_L, 340, 540L),
                )
            )

            SoundEffect.LOSE -> playSequence(
                listOf(
                    Triple(ToneGenerator.TONE_CDMA_REORDER, 240, 0L),
                    Triple(ToneGenerator.TONE_CDMA_INTERCEPT, 220, 250L),
                    Triple(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 260, 500L),
                )
            )

            SoundEffect.DRAW -> playSequence(
                listOf(
                    Triple(ToneGenerator.TONE_CDMA_NETWORK_CALLWAITING, 180, 0L),
                    Triple(ToneGenerator.TONE_CDMA_NETWORK_CALLWAITING, 180, 220L),
                    Triple(ToneGenerator.TONE_PROP_BEEP2, 190, 440L),
                )
            )
        }
    }

    private fun playTone(tone: Int, duration: Int) {
        toneGenerator.startTone(tone, duration)
    }

    private fun playSequence(steps: List<Triple<Int, Int, Long>>) {
        steps.forEach { (tone, duration, delayMs) ->
            val runnable = Runnable {
                toneGenerator.startTone(tone, duration)
            }
            queued += runnable
            handler.postDelayed(runnable, delayMs)
        }
    }

    private fun clearQueue() {
        queued.forEach(handler::removeCallbacks)
        queued.clear()
    }

    fun release() {
        clearQueue()
        toneGenerator.release()
    }
}
