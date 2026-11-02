package com.felipe.nonograma

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*

/** Editor manual: tamaño, modo color, paleta y pistas (con color por pista). */
class EditorActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var palBar: LinearLayout
    private lateinit var sizeR: TextView
    private lateinit var sizeC: TextView
    private lateinit var sum: TextView
    private val lineViews = mutableListOf<Pair<EditText, LinearLayout>>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        Store.load(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(24), dp(10), dp(8))
            // el contenedor toma el foco para que ningún campo lo reciba al abrir
            isFocusable = true; isFocusableInTouchMode = true
        }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        fun stepper(label: String, get: () -> Int, set: (Int) -> Unit): TextView {
            top.addView(TextView(this).apply { text = label; setPadding(0, 0, dp(4), 0) })
            val v = TextView(this).apply { text = get().toString(); textSize = 16f; setPadding(dp(4), 0, dp(4), 0) }
            top.addView(Button(this).apply { text = "−"; minWidth = 0; minimumWidth = dp(40); setOnClickListener { set(get() - 1); v.text = get().toString() } })
            top.addView(v)
            top.addView(Button(this).apply { text = "+"; minWidth = 0; minimumWidth = dp(40); setOnClickListener { set(get() + 1); v.text = get().toString() } })
            return v
        }
        sizeR = stepper("Filas", { Store.rows }) { Store.resize(it, Store.cols); rebuild() }
        sizeC = stepper("Col.", { Store.cols }) { Store.resize(Store.rows, it); rebuild() }
        root.addView(top)
        val opts = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        opts.addView(CheckBox(this).apply { text = "Auto"; isChecked = Store.autoSize; setOnCheckedChangeListener { _, c -> Store.autoSize = c } })
        opts.addView(CheckBox(this).apply { text = "Color"; isChecked = Store.colorMode; setOnCheckedChangeListener { _, c -> Store.colorMode = c; rebuild() } })
        sum = TextView(this).apply { setPadding(dp(12), 0, 0, 0) }
        opts.addView(sum)
        root.addView(opts)
        palBar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        root.addView(HorizontalScrollView(this).apply { addView(palBar) })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val bottom = LinearLayout(this)
        bottom.addView(Button(this).apply { text = "Limpiar"; setOnClickListener {
            Store.rowClues.forEach { it.clear() }; Store.colClues.forEach { it.clear() }; Store.solution = null; rebuild() } })
        bottom.addView(Button(this).apply { text = "Resolver"; setOnClickListener { solve(this) } },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(bottom)
        setContentView(root)
        root.requestFocus()
        rebuild()
    }

    override fun onPause() { super.onPause(); Store.save(this) }

    private fun swatch(color: Int, sizeDp: Int = 30) = View(this).apply {
        background = GradientDrawable().apply { setColor(color); setStroke(dp(1), 0xFF888888.toInt()); cornerRadius = dp(4).toFloat() }
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
    }

    private fun rebuildPalette() {
        palBar.removeAllViews()
        palBar.visibility = if (Store.colorMode) View.VISIBLE else View.GONE
        Store.palette.forEachIndexed { i, c -> palBar.addView(swatch(c).apply { setOnClickListener { pickRgb(c) { n -> Store.palette[i] = n; rebuild() } } }) }
        palBar.addView(Button(this).apply { text = "+"; minWidth = 0; minimumWidth = dp(40); setOnClickListener {
            pickRgb(0xFFE53935.toInt()) { n -> Store.palette.add(n); rebuild() } } })
    }

    private fun rebuild() {
        sizeR.text = Store.rows.toString(); sizeC.text = Store.cols.toString()
        rebuildPalette()
        list.removeAllViews(); lineViews.clear()
        fun section(name: String, clues: MutableList<MutableList<Block>>, len: Int) {
            list.addView(TextView(this).apply { text = name; textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(0, dp(8), 0, dp(2)) })
            clues.forEachIndexed { i, clue -> list.addView(lineRow(i + 1, clue, len)) }
        }
        section("Filas", Store.rowClues, Store.cols)
        section("Columnas", Store.colClues, Store.rows)
        updateSum()
    }

    private fun lineRow(n: Int, clue: MutableList<Block>, len: Int): View {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(this).apply { text = n.toString(); minWidth = dp(26) })
        val et = EditText(this).apply {
            setText(clue.joinToString(" ") { it.len.toString() })
            inputType = InputType.TYPE_CLASS_PHONE // teclado numérico con espacio
            isSingleLine = true; textSize = 15f
            showSoftInputOnFocus = true
        }
        val chips = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        fun drawChips() {
            chips.removeAllViews()
            if (!Store.colorMode) return
            clue.forEachIndexed { k, bl ->
                chips.addView(TextView(this).apply {
                    text = bl.len.toString(); gravity = Gravity.CENTER
                    val c = Store.displayColor(bl.color)
                    setTextColor(if (Color.luminance(c) > 0.5f) Color.BLACK else Color.WHITE)
                    background = GradientDrawable().apply { setColor(c); cornerRadius = dp(4).toFloat() }
                    layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply { setMargins(dp(2), 0, dp(2), 0) }
                    setOnClickListener { pickPalette { idx -> clue[k] = Block(bl.len, idx); drawChips(); updateSum() } }
                })
            }
        }
        fun mark() { et.setBackgroundColor(if (Solver.minLen(if (Store.colorMode) clue else clue.map { Block(it.len, 1) }) > len) 0x40FF0000 else 0) }
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val nums = s.toString().split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }.filter { it > 0 }
                val old = clue.toList()
                clue.clear(); nums.forEachIndexed { k, v -> clue.add(Block(v, old.getOrNull(k)?.color ?: 1)) }
                Store.solution = null
                drawChips(); mark(); updateSum()
            }
        })
        row.addView(et, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(HorizontalScrollView(this).apply { addView(chips) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { weight = if (Store.colorMode) 1f else 0f })
        drawChips(); mark()
        lineViews.add(et to chips)
        return row
    }

    private fun updateSum() {
        val k = if (Store.colorMode) Store.palette.size else 1
        val bad = (1..k).any { c ->
            fun s(l: List<List<Block>>) = l.flatten().filter { !Store.colorMode || it.color == c }.sumOf { it.len }
            s(Store.rowClues) != s(Store.colClues)
        }
        val a = Store.rowClues.flatten().sumOf { it.len }; val b = Store.colClues.flatten().sumOf { it.len }
        sum.text = "Σ $a / $b"
        sum.setTextColor(if (bad) 0xFFD32F2F.toInt() else 0xFF388E3C.toInt())
    }

    private fun pickPalette(onPick: (Int) -> Unit) {
        val bar = LinearLayout(this).apply { setPadding(dp(12), dp(12), dp(12), dp(12)) }
        val d = AlertDialog.Builder(this).setView(HorizontalScrollView(this).apply { addView(bar) }).create()
        Store.palette.forEachIndexed { i, c -> bar.addView(swatch(c, 40).apply { setOnClickListener { onPick(i + 1); d.dismiss() } }) }
        d.show()
    }

    private fun pickRgb(start: Int, onPick: (Int) -> Unit) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(4)) }
        val prev = View(this)
        box.addView(prev, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)))
        val ch = intArrayOf(Color.red(start), Color.green(start), Color.blue(start))
        fun upd() = prev.setBackgroundColor(Color.rgb(ch[0], ch[1], ch[2]))
        for (i in 0..2) box.addView(SeekBar(this).apply {
            max = 255; progress = ch[i]
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { ch[i] = p; upd() }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
        upd()
        AlertDialog.Builder(this).setView(box).setPositiveButton("OK") { _, _ -> onPick(Color.rgb(ch[0], ch[1], ch[2])) }.show()
    }

    private fun solve(btn: Button) {
        btn.isEnabled = false
        Thread {
            val st = Store.solveNow(10_000)
            runOnUiThread {
                btn.isEnabled = true
                Store.save(this)
                when (st) {
                    Solver.Status.SOLVED -> { OverlayService.instance?.refresh(); finish() }
                    Solver.Status.TIMEOUT -> Toast.makeText(this, "Tiempo agotado", Toast.LENGTH_SHORT).show()
                    Solver.Status.INVALID -> Toast.makeText(this, "Pistas inválidas", Toast.LENGTH_SHORT).show()
                    Solver.Status.NO_SOLUTION -> Toast.makeText(this, "Sin solución", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
