package com.example.momirprint

import android.graphics.Bitmap
import android.graphics.Color

/**
 * A dither "kernel": where to push the rounding error after a pixel is forced to black or white.
 *
 * Each pixel is rounded to pure black (0) or pure white (255). The difference between what the
 * pixel *was* and what it *became* is the error. Instead of throwing that error away, we hand
 * fractions of it to neighbouring pixels that haven't been processed yet, so a patch of mid-grey
 * turns into a pattern that is half black dots and half white, which the eye averages back to grey.
 *
 * dx/dy are offsets from the current pixel (dy is always >= 0, i.e. only pixels still to come
 * are touched) and weight[i] is the fraction of the error sent to offset i.
 */
enum class DitherKernel(
    internal val dx: IntArray,
    internal val dy: IntArray,
    internal val weight: FloatArray
) {
    /**
     * Classic. Spreads 100% of the error over 4 neighbours (right 7/16, below-left 3/16,
     * below 5/16, below-right 1/16). Smooth gradients, but photos can look a bit washed out.
     */
    FLOYD_STEINBERG(
        dx = intArrayOf(1, -1, 0, 1),
        dy = intArrayOf(0, 1, 1, 1),
        weight = floatArrayOf(7f / 16, 3f / 16, 5f / 16, 1f / 16)
    ),

    /**
     * Spreads only 6/8 of the error (1/8 to each of 6 neighbours) and drops the rest. Losing
     * error pushes light areas to clean white and dark areas to solid black, which gives a
     * punchier, higher-contrast result. That usually suits thermal printers, whose dots bleed.
     */
    ATKINSON(
        dx = intArrayOf(1, 2, -1, 0, 1, 0),
        dy = intArrayOf(0, 0, 1, 1, 1, 2),
        weight = floatArrayOf(1f / 8, 1f / 8, 1f / 8, 1f / 8, 1f / 8, 1f / 8)
    )
}

object Dither {

    /**
     * Turns any bitmap into a pure black-and-white bitmap (every pixel exactly Color.BLACK or
     * Color.WHITE) ready for PrinterService.printImage().
     *
     * Run this on the bitmap at its FINAL size (the width you ask Coil for). Dithering makes a
     * pattern of single-pixel dots, so if the image is scaled afterwards the pattern gets smeared.
     *
     * This is CPU work over every pixel, so call it from Dispatchers.Default, not the main thread.
     *
     * @param contrast   1.0 = unchanged, >1 = punchier. Pivots around mid-grey.
     * @param brightness added to every grey value (0..255 scale); positive = lighter print.
     */
    fun toPrinterBitmap(
        src: Bitmap,
        kernel: DitherKernel = DitherKernel.ATKINSON,
        contrast: Float = 1f,
        brightness: Float = 0f
    ): Bitmap {
        val w = src.width
        val h = src.height

        // getPixels copies the bitmap into a flat IntArray, one packed ARGB Int per pixel,
        // row after row. That's far faster than calling getPixel(x, y) 200,000 times.
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        // Step 1: colour -> a single grey value per pixel, with contrast/brightness applied.
        val gray = FloatArray(w * h) { i ->
            val adjusted = (luminanceOnWhite(pixels[i]) - 128f) * contrast + 128f + brightness
            adjusted.coerceIn(0f, 255f)
        }

        // Step 2: error diffusion. Afterwards every value in `gray` is exactly 0f or 255f.
        diffuse(gray, w, h, kernel)

        // Step 3: back to a Bitmap.
        for (i in pixels.indices) {
            pixels[i] = if (gray[i] < 128f) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * The algorithm itself, working on a plain grey "plane": `gray[y * w + x]`, 0 = black,
     * 255 = white. Modifies the array in place.
     *
     * It deliberately touches no Android classes, so it can be unit-tested on the JVM.
     */
    internal fun diffuse(gray: FloatArray, w: Int, h: Int, kernel: DitherKernel) {
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val old = gray[i]                        // may be <0 or >255 once errors pile up
                val quantized = if (old < 128f) 0f else 255f
                gray[i] = quantized
                val error = old - quantized

                for (t in kernel.dx.indices) {
                    val nx = x + kernel.dx[t]
                    val ny = y + kernel.dy[t]
                    // Skip neighbours that fall off the left/right/bottom edge. (ny is never
                    // negative: kernels only point at the current row or later.)
                    if (nx in 0 until w && ny < h) {
                        gray[ny * w + nx] += error * kernel.weight[t]
                    }
                }
            }
        }
    }

    /**
     * Brightness (0..255) of a packed ARGB pixel, treating transparency as white paper.
     * Weights 0.299/0.587/0.114 reflect that the eye is most sensitive to green.
     */
    private fun luminanceOnWhite(argb: Int): Float {
        val luminance = 0.299f * Color.red(argb) + 0.587f * Color.green(argb) + 0.114f * Color.blue(argb)
        val opacity = Color.alpha(argb) / 255f
        return luminance * opacity + 255f * (1f - opacity)
    }
}