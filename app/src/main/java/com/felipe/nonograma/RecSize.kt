package com.felipe.nonograma

/** Tamaños de vídeo a probar (puro, testeable): pantalla completa y reducciones; lados pares y múltiplos de 16. */
object RecSize {
    fun candidates(w: Int, h: Int): List<Pair<Int, Int>> {
        fun fit(maxLong: Int): Pair<Int, Int> {
            val long = maxOf(w, h); val s = if (long > maxLong) maxLong.toFloat() / long else 1f
            fun a(v: Int) = maxOf(16, ((v * s).toInt() / 16) * 16)
            return a(w) to a(h)
        }
        return listOf(fit(Int.MAX_VALUE), fit(1920), fit(1280)).distinct()
    }
    /** Bitrate razonable para pantalla (~6 bits/píxel/seg a 30 fps, entre 2 y 12 Mbps). */
    fun bitrate(w: Int, h: Int): Int = (w.toLong() * h * 30 / 5).coerceIn(2_000_000, 12_000_000).toInt()
}
