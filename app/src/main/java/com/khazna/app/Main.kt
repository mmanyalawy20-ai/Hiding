package com.khazna.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import java.math.BigDecimal
import java.math.MathContext
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** تخزين محلي: قائمة التطبيقات المخفية + الرموز (مُجزّأة بـ SHA-256 مع salt). */
class Store(context: Context) {
    private val p = context.applicationContext
        .getSharedPreferences("khazna", Context.MODE_PRIVATE)

    var hidden: Set<String>
        get() = (p.getStringSet("hidden", emptySet()) ?: emptySet()).toSet()
        set(v) { p.edit().putStringSet("hidden", v.toSet()).apply() }

    var fails: Int
        get() = p.getInt("fails", 0)
        set(v) { p.edit().putInt("fails", v).apply() }

    private fun salt(): String {
        var s = p.getString("salt", null)
        if (s == null) {
            s = UUID.randomUUID().toString()
            p.edit().putString("salt", s).apply()
        }
        return s
    }

    private fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest((salt() + ":" + pin).toByteArray())
            .joinToString("") { "%02x".format(it) }

    val hasPin: Boolean get() = p.contains("pin")
    val hasDuress: Boolean get() = p.contains("duress")

    fun setPin(pin: String) { p.edit().putString("pin", hash(pin)).apply() }
    fun setDuress(pin: String) { p.edit().putString("duress", hash(pin)).apply() }
    fun clearDuress() { p.edit().remove("duress").apply() }

    fun checkPin(pin: String): Boolean = hasPin && p.getString("pin", null) == hash(pin)
    fun checkDuress(pin: String): Boolean = hasDuress && p.getString("duress", null) == hash(pin)
}

data class AppItem(val label: String, val pkg: String, val icon: ImageBitmap)

object Apps {
    /** كل التطبيقات التي لها أيقونة تشغيل، مرتبة بالاسم. */
    fun launchable(ctx: Context): List<AppItem> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map {
                AppItem(
                    label = it.loadLabel(pm).toString(),
                    pkg = it.activityInfo.packageName,
                    icon = it.loadIcon(pm).toBitmap(96, 96).asImageBitmap()
                )
            }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    fun launch(ctx: Context, pkg: String) {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }
}

object Pal {
    val Ink = Color(0xFF0E1A22)
    val Slate2 = Color(0xFF2A3E4F)
    val Slate3 = Color(0xFF3A5164)
    val Mist = Color(0xFFE3EBEE)
    val Paper = Color(0xFFF2F6F7)
    val Saffron = Color(0xFFEFB04A)
    val Muted = Color(0xFF667C89)
    val Danger = Color(0xFFD4574E)
}

@Composable
fun KhaznaTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = Pal.Ink,
                onPrimary = Pal.Saffron,
                background = Pal.Paper,
                surface = Color.White,
                onBackground = Pal.Ink,
                onSurface = Pal.Ink,
                primaryContainer = Pal.Ink,
                onPrimaryContainer = Pal.Saffron
            ),
            content = content
        )
    }
}

/** أيقونة تطبيق مع اسمه، وزر "−" اختياري (يظهر في وضع التحرير). */
@Composable
fun AppCell(
    app: AppItem,
    textColor: Color,
    onClick: () -> Unit,
    onBadge: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier.clickable(onClick = onClick).padding(6.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Image(bitmap = app.icon, contentDescription = app.label, modifier = Modifier.size(56.dp))
            Text(
                app.label,
                color = textColor,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
        if (onBadge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(24.dp)
                    .background(Pal.Danger, CircleShape)
                    .clickable(onClick = onBadge),
                contentAlignment = Alignment.Center
            ) { Text("−", color = Color.White, fontSize = 16.sp) }
        }
    }
}

/** نافذة إدخال رمز من 4 أرقام. تُرجع onConfirm رسالة خطأ أو null عند النجاح. */
@Composable
fun PinDialog(title: String, hint: String, onConfirm: (String) -> String?, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(hint, fontSize = 13.sp, color = Pal.Muted, modifier = Modifier.padding(bottom = 10.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { v -> if (v.length <= 4 && v.all { it.isDigit() }) value = v },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )
                error?.let { Text(it, color = Pal.Danger, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (value.length != 4) error = "الرمز يجب أن يكون 4 أرقام"
                else error = onConfirm(value)
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private const val OPS = "÷×−+"

/** حساب بسيط مع أولوية الضرب والقسمة. يُرجع null إذا كانت الصيغة غير صالحة. */
fun evaluate(raw: String): String? {
    val s = raw.trimEnd('÷', '×', '−', '+')
    if (s.isEmpty()) return null
    val nums = mutableListOf<Double>()
    val ops = mutableListOf<Char>()
    var i = 0
    while (i < s.length) {
        var neg = false
        if (s[i] == '−') { neg = true; i++ }
        val st = i
        while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
        if (st == i) return null
        var v = s.substring(st, i).toDoubleOrNull() ?: return null
        if (i < s.length && s[i] == '%') { v /= 100.0; i++ }
        nums.add(if (neg) -v else v)
        if (i < s.length) {
            val o = s[i]
            if (OPS.indexOf(o) < 0) return null
            ops.add(o)
            i++
        }
    }
    if (nums.size != ops.size + 1) return null

    val n2 = mutableListOf(nums[0])
    val o2 = mutableListOf<Char>()
    for (k in ops.indices) {
        val x = nums[k + 1]
        when (ops[k]) {
            '×' -> n2[n2.lastIndex] = n2.last() * x
            '÷' -> n2[n2.lastIndex] = n2.last() / x
            else -> { o2.add(ops[k]); n2.add(x) }
        }
    }
    var r = n2[0]
    for (k in o2.indices) r = if (o2[k] == '+') r + n2[k + 1] else r - n2[k + 1]
    if (r.isNaN() || r.isInfinite()) return "خطأ"
    return if (r == r.toLong().toDouble() && abs(r) < 1e15) r.toLong().toString()
    else BigDecimal(r).round(MathContext(10)).stripTrailingZeros().toPlainString()
}

/** أول تشغيل: اختيار الرمز السري. */
@Composable
fun SetupScreen(store: Store, onDone: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().background(Pal.Paper).statusBarsPadding().navigationBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("إعداد الرمز السري", fontSize = 24.sp, color = Pal.Ink)
        Spacer(Modifier.height(8.dp))
        Text(
            "اختر 4 أرقام. بعد ذلك يفتح التطبيق كحاسبة عادية، وتكتب الرمز ثم تضغط = لفتح الخزنة.",
            fontSize = 14.sp, color = Pal.Muted
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { v -> if (v.length <= 4 && v.all { it.isDigit() }) pin = v },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            label = { Text("الرمز") }
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { store.setPin(pin); onDone() },
            enabled = pin.length == 4,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("حفظ ومتابعة") }
    }
}

/** الحاسبة المموّهة. إدخال الرمز ثم = يفتح الخزنة. */
@Composable
fun CalcScreen(store: Store, onVault: () -> Unit, onDecoy: () -> Unit) {
    var expr by remember { mutableStateOf("") }
    var fresh by remember { mutableStateOf(false) }

    fun press(k: String) {
        when {
            k == "C" -> { expr = ""; fresh = false }
            k == "⌫" -> { expr = if (fresh) "" else expr.dropLast(1); fresh = false }
            k == "=" -> {
                if (expr.length == 4 && expr.all { it.isDigit() }) {
                    if (store.checkPin(expr)) { expr = ""; onVault(); return }
                    if (store.checkDuress(expr)) { expr = ""; onDecoy(); return }
                    store.fails = store.fails + 1
                }
                val r = evaluate(expr)
                if (r != null) { expr = r; fresh = true }
            }
            OPS.contains(k) -> {
                if (expr.isEmpty()) {
                    if (k == "−") expr = "−"
                } else if (expr != "خطأ") {
                    fresh = false
                    expr = if (OPS.contains(expr.last())) expr.dropLast(1) + k else expr + k
                }
            }
            k == "%" -> { if (expr.lastOrNull()?.isDigit() == true) expr += "%" }
            k == "." -> {
                if (fresh || expr == "خطأ") { expr = "0."; fresh = false }
                else {
                    val last = expr.split('÷', '×', '−', '+').last()
                    if (!last.contains('.') && !last.endsWith("%")) expr += if (last.isEmpty()) "0." else "."
                }
            }
            else -> {
                if (fresh || expr == "خطأ") { expr = ""; fresh = false }
                if (expr.length < 24) expr += k
            }
        }
    }

    val rows = listOf(
        listOf("C", "⌫", "%", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "−"),
        listOf("1", "2", "3", "+")
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = Modifier.fillMaxSize().background(Pal.Ink).statusBarsPadding().navigationBarsPadding().padding(14.dp)
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp), contentAlignment = Alignment.BottomEnd) {
                val text = expr.ifEmpty { "0" }
                Text(
                    text,
                    color = Pal.Mist,
                    fontSize = when { text.length > 14 -> 32.sp; text.length > 8 -> 44.sp; else -> 64.sp },
                    textAlign = TextAlign.End,
                    maxLines = 2
                )
            }
            for (row in rows) {
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (k in row) {
                        val isOp = OPS.contains(k)
                        val isFn = k == "C" || k == "⌫" || k == "%"
                        Key(k, if (isOp) Pal.Saffron else if (isFn) Pal.Slate3 else Pal.Slate2,
                            if (isOp) Pal.Ink else Color.White, Modifier.weight(1f)) { press(k) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Key("0", Pal.Slate2, Color.White, Modifier.weight(2f).padding(0.dp)) { press("0") }
                Key(".", Pal.Slate2, Color.White, Modifier.weight(1f)) { press(".") }
                Key("=", Pal.Saffron, Pal.Ink, Modifier.weight(1f)) { press("=") }
            }
        }
    }
}

@Composable
private fun Key(label: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.height(66.dp).background(bg, RoundedCornerShape(22.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = fg, fontSize = 26.sp) }
}

private enum class VaultPage { GRID, PICK, SETTINGS }
private enum class PinTarget { NONE, PIN, DURESS }

/**
 * الخزنة. في وضع decoy (رمز الإكراه) تظهر فارغة دائماً ولا تُحفظ فيها أي تغييرات،
 * ولا تظهر الإعدادات حتى لا يصل المُكرِه إلى الرمز الحقيقي.
 */
@Composable
fun VaultScreen(store: Store, decoy: Boolean, onLock: () -> Unit) {
    val ctx = LocalContext.current
    var page by remember { mutableStateOf(VaultPage.GRID) }
    var all by remember { mutableStateOf(emptyList<AppItem>()) }
    var hidden by remember { mutableStateOf(if (decoy) emptySet() else store.hidden) }
    var edit by remember { mutableStateOf(false) }
    var failsShown by remember { mutableIntStateOf(if (decoy) 0 else store.fails) }

    LaunchedEffect(Unit) {
        all = withContext(Dispatchers.IO) { Apps.launchable(ctx).filter { it.pkg != ctx.packageName } }
    }

    fun setHidden(v: Set<String>) {
        if (decoy) return
        hidden = v
        store.hidden = v
    }

    Box(Modifier.fillMaxSize().background(Pal.Paper)) {
        when (page) {
            VaultPage.GRID -> {
                val list = all.filter { it.pkg in hidden }
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("الخزنة", fontSize = 24.sp, color = Pal.Ink)
                            Text(
                                if (list.isEmpty()) "لا شيء هنا بعد" else "${list.size} تطبيقات مخفية",
                                fontSize = 13.sp, color = Pal.Muted
                            )
                        }
                        if (list.isNotEmpty()) {
                            TextButton(onClick = { edit = !edit }) { Text(if (edit) "تم" else "تحرير") }
                        }
                        if (!decoy) TextButton(onClick = { page = VaultPage.SETTINGS }) { Text("الإعدادات") }
                        TextButton(onClick = onLock) { Text("قفل") }
                    }

                    if (failsShown > 0) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 10.dp)
                                .background(Color(0xFFFBEDEB), RoundedCornerShape(14.dp)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("سُجّلت $failsShown محاولات رمز خاطئة", fontSize = 13.sp, color = Color(0xFF7C2A23), modifier = Modifier.weight(1f))
                            TextButton(onClick = { store.fails = 0; failsShown = 0 }) { Text("إخفاء") }
                        }
                    }

                    if (list.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("الخزنة فارغة.\nاضغط + لاختيار التطبيقات التي تريد إخفاءها.", color = Pal.Muted, fontSize = 15.sp)
                        }
                    } else {
                        LazyVerticalGrid(columns = GridCells.Fixed(4), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(list, key = { it.pkg }) { app ->
                                AppCell(
                                    app = app,
                                    textColor = Pal.Ink,
                                    onClick = { if (!edit) Apps.launch(ctx, app.pkg) },
                                    onBadge = if (edit) ({
                                        setHidden(hidden - app.pkg)
                                        if (hidden.none { p -> all.any { it.pkg == p } }) edit = false
                                        Toast.makeText(ctx, "${app.label} عاد إلى الشاشة الرئيسية", Toast.LENGTH_SHORT).show()
                                    }) else null
                                )
                            }
                        }
                    }
                }
                FloatingActionButton(
                    onClick = { page = VaultPage.PICK },
                    modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(20.dp),
                    containerColor = Pal.Ink,
                    contentColor = Pal.Saffron
                ) { Text("+", fontSize = 28.sp) }
            }

            VaultPage.PICK -> PickPage(
                candidates = all.filter { it.pkg !in hidden },
                onClose = { page = VaultPage.GRID },
                onHide = { chosen ->
                    setHidden(hidden + chosen)
                    Toast.makeText(ctx, "تم إخفاء ${chosen.size} تطبيقات", Toast.LENGTH_SHORT).show()
                    page = VaultPage.GRID
                }
            )

            VaultPage.SETTINGS -> SettingsPage(store, onBack = { page = VaultPage.GRID })
        }
    }
}

@Composable
private fun PickPage(candidates: List<AppItem>, onClose: () -> Unit, onHide: (Set<String>) -> Unit) {
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf(setOf<String>()) }
    val shown = candidates.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("اختر التطبيقات", fontSize = 22.sp, color = Pal.Ink)
                Text("ستختفي من الشاشة الرئيسية", fontSize = 13.sp, color = Pal.Muted)
            }
            TextButton(onClick = onClose) { Text("إغلاق") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("ابحث عن تطبيق") }
        )
        LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
            items(shown, key = { it.pkg }) { app ->
                val on = app.pkg in chosen
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { chosen = if (on) chosen - app.pkg else chosen + app.pkg }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(42.dp))
                    Text(app.label, fontSize = 15.sp, color = Pal.Ink, modifier = Modifier.weight(1f))
                    Checkbox(checked = on, onCheckedChange = { chosen = if (on) chosen - app.pkg else chosen + app.pkg })
                }
            }
        }
        Button(
            onClick = { onHide(chosen) },
            enabled = chosen.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(vertical = 0.dp)
        ) { Text(if (chosen.isEmpty()) "اختر تطبيقاً للإخفاء" else "إخفاء ${chosen.size}") }
    }
}

@Composable
private fun SettingsPage(store: Store, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var target by remember { mutableStateOf(PinTarget.NONE) }
    var hasDuress by remember { mutableStateOf(store.hasDuress) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("الإعدادات", fontSize = 24.sp, color = Pal.Ink, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("رجوع") }
        }
        SettingRow("تغيير الرمز السري", "الرمز الذي تكتبه في الحاسبة ثم =") { target = PinTarget.PIN }
        SettingRow(
            "رمز الإكراه",
            if (hasDuress) "مفعّل — يفتح خزنة فارغة" else "غير مفعّل — يفتح خزنة وهمية فارغة"
        ) { target = PinTarget.DURESS }
        if (hasDuress) {
            SettingRow("تعطيل رمز الإكراه", "") {
                store.clearDuress(); hasDuress = false
                Toast.makeText(ctx, "تم تعطيل رمز الإكراه", Toast.LENGTH_SHORT).show()
            }
        }
    }

    when (target) {
        PinTarget.PIN -> PinDialog(
            title = "رمز سري جديد",
            hint = "أدخل 4 أرقام. يجب أن يختلف عن رمز الإكراه.",
            onConfirm = { v ->
                if (store.checkDuress(v)) "اختر رمزاً مختلفاً عن رمز الإكراه"
                else { store.setPin(v); target = PinTarget.NONE; Toast.makeText(ctx, "تم حفظ الرمز", Toast.LENGTH_SHORT).show(); null }
            },
            onDismiss = { target = PinTarget.NONE }
        )
        PinTarget.DURESS -> PinDialog(
            title = "رمز إكراه",
            hint = "أدخل 4 أرقام مختلفة عن رمزك السري. عند كتابته تفتح خزنة فارغة.",
            onConfirm = { v ->
                if (store.checkPin(v)) "اختر رمزاً مختلفاً عن الرمز السري"
                else {
                    store.setDuress(v); hasDuress = true; target = PinTarget.NONE
                    Toast.makeText(ctx, "تم حفظ رمز الإكراه", Toast.LENGTH_SHORT).show(); null
                }
            },
            onDismiss = { target = PinTarget.NONE }
        )
        PinTarget.NONE -> {}
    }
}

@Composable
private fun SettingRow(title: String, sub: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 10.dp)
            .background(Color.White, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Text(title, fontSize = 15.sp, color = Pal.Ink)
        if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = Pal.Muted, modifier = Modifier.padding(top = 2.dp))
    }
}

private enum class Screen { SETUP, CALC, VAULT, DECOY }

class MainActivity : ComponentActivity() {
    private var lockTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // يمنع لقطات الشاشة ويُخفي معاينة التطبيق في قائمة التطبيقات الأخيرة
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent { KhaznaTheme { Root(lockTick) } }
    }

    override fun onStop() {
        super.onStop()
        lockTick++ // أي خروج من التطبيق يقفل الخزنة
    }
}

@Composable
private fun Root(lockTick: Int) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    var screen by remember { mutableStateOf(if (store.hasPin) Screen.CALC else Screen.SETUP) }

    LaunchedEffect(lockTick) {
        if (screen == Screen.VAULT || screen == Screen.DECOY) screen = Screen.CALC
    }

    when (screen) {
        Screen.SETUP -> SetupScreen(store) { screen = Screen.CALC }
        Screen.CALC -> CalcScreen(store, onVault = { screen = Screen.VAULT }, onDecoy = { screen = Screen.DECOY })
        Screen.VAULT -> VaultScreen(store, decoy = false) { screen = Screen.CALC }
        Screen.DECOY -> VaultScreen(store, decoy = true) { screen = Screen.CALC }
    }
}

/** الشاشة الرئيسية: تعرض كل التطبيقات ما عدا المخفية. */
class LauncherActivity : ComponentActivity() {
    private var tick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* المشغّل لا يُغلق بزر الرجوع */ }
        })
        setContent { KhaznaTheme { HomeScreen(tick) } }
    }

    override fun onResume() {
        super.onResume()
        tick++ // تحديث القائمة عند العودة (تثبيت/حذف/إخفاء)
    }
}

@Composable
private fun HomeScreen(tick: Int) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    var apps by remember { mutableStateOf(emptyList<AppItem>()) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(tick) {
        val hidden = store.hidden
        apps = withContext(Dispatchers.IO) { Apps.launchable(ctx).filter { it.pkg !in hidden } }
    }

    val shown = apps.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }

    Column(
        Modifier.fillMaxSize().background(Color(0x66000000)).statusBarsPadding().navigationBarsPadding().padding(12.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            placeholder = { Text("ابحث") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color(0x88FFFFFF),
                cursorColor = Color.White,
                focusedPlaceholderColor = Color(0xBBFFFFFF),
                unfocusedPlaceholderColor = Color(0xBBFFFFFF)
            )
        )
        LazyVerticalGrid(columns = GridCells.Fixed(4)) {
            items(shown, key = { it.pkg }) { app ->
                AppCell(app = app, textColor = Color.White, onClick = { Apps.launch(ctx, app.pkg) })
            }
        }
    }
}
