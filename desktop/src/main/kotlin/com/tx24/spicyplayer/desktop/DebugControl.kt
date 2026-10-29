package com.tx24.spicyplayer.desktop

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.awt.Component
import java.awt.Container
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import javax.accessibility.Accessible
import javax.accessibility.AccessibleContext
import javax.swing.SwingUtilities
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skiko.SkiaLayer

/**
 * A local HTTP port for driving the window from scripts while developing, started only when
 * `-Dspicy.debugPort=<port>` is set. It listens on 127.0.0.1 alone.
 *
 * - `/snap`: the window's content as a PNG, drawn by Skia (nothing needs to be on screen).
 * - `/tree`: the accessibility tree, one node per line with its role, name and bounds.
 * - `/click?x=&y=` (`&button=right`), `/move?x=&y=`, `/scroll?x=&y=&n=` (positive is down).
 * - `/key?code=ESCAPE&mods=ctrl,shift`, `/type?text=`: key names are `KeyEvent.VK_*` without the prefix.
 * - `/size?w=&h=`: resizes the window (window points).
 *
 * Coordinates are in the content's points, the same as `/tree` and half a 2× snap.
 */
object DebugControl {
    private var server: HttpServer? = null

    fun startIfAsked(window: Window) {
        val port = System.getProperty("spicy.debugPort")?.toIntOrNull() ?: return
        if (server != null) return
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0).apply {
            createContext("/") { exchange ->
                try {
                    handle(window, exchange)
                } catch (e: Exception) {
                    reply(exchange, 500, "${e.javaClass.simpleName}: ${e.message}\n".toByteArray(), "text/plain")
                }
            }
            start()
        }
        System.err.println("[debug] control on http://127.0.0.1:$port")
    }

    private fun handle(window: Window, exchange: HttpExchange) {
        val q = query(exchange)
        fun int(name: String) = q[name]?.toInt() ?: error("missing $name")
        val body: ByteArray = when (exchange.requestURI.path) {
            "/snap" -> onEdt { snap(window) }
            "/tree" -> onEdt { tree(window) }.toByteArray()
            "/click" -> onEdt { click(window, int("x"), int("y"), q["button"] == "right"); ok }
            "/move" -> onEdt { mouse(window, MouseEvent.MOUSE_MOVED, int("x"), int("y"), 0, 0); ok }
            "/scroll" -> onEdt { scroll(window, int("x"), int("y"), int("n")); ok }
            "/key" -> onEdt { key(window, q["code"] ?: error("missing code"), q["mods"].orEmpty()); ok }
            "/type" -> onEdt { type(window, q["text"].orEmpty()); ok }
            "/size" -> onEdt { window.setSize(int("w"), int("h")); window.validate(); ok }
            else -> error("unknown path ${exchange.requestURI.path}")
        }
        reply(exchange, 200, body, if (exchange.requestURI.path == "/snap") "image/png" else "text/plain")
    }

    private val ok = "ok\n".toByteArray()

    private fun query(exchange: HttpExchange): Map<String, String> =
        exchange.requestURI.rawQuery.orEmpty().split('&').filter { '=' in it }.associate {
            val (k, v) = it.split('=', limit = 2)
            k to URLDecoder.decode(v, Charsets.UTF_8)
        }

    private fun reply(exchange: HttpExchange, code: Int, body: ByteArray, type: String) {
        exchange.responseHeaders.add("Content-Type", type)
        exchange.sendResponseHeaders(code, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    private fun <T> onEdt(block: () -> T): T {
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private fun skiaLayer(window: Window): SkiaLayer =
        find(window) { it is SkiaLayer } as? SkiaLayer ?: error("no Skia layer in the window")

    private fun find(root: Component, test: (Component) -> Boolean): Component? {
        if (test(root)) return root
        if (root is Container) for (child in root.components) find(child, test)?.let { return it }
        return null
    }

    private fun snap(window: Window): ByteArray {
        val bitmap = skiaLayer(window).screenshot() ?: error("Skia gave no picture")
        return Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)?.bytes ?: error("PNG encoding failed")
    }

    /** The component under ([x], [y]) of the Skia layer, which is where Compose listens. */
    private fun target(window: Window, x: Int, y: Int): Component {
        val layer = skiaLayer(window)
        return SwingUtilities.getDeepestComponentAt(layer, x, y) ?: layer
    }

    private fun mouse(window: Window, id: Int, x: Int, y: Int, modifiers: Int, button: Int, clicks: Int = 1) {
        val layer = skiaLayer(window)
        val target = target(window, x, y)
        val p = SwingUtilities.convertPoint(layer, x, y, target)
        target.dispatchEvent(MouseEvent(target, id, System.currentTimeMillis(), modifiers, p.x, p.y, clicks, false, button))
    }

    private fun click(window: Window, x: Int, y: Int, right: Boolean) {
        val button = if (right) MouseEvent.BUTTON3 else MouseEvent.BUTTON1
        val down = if (right) InputEvent.BUTTON3_DOWN_MASK else InputEvent.BUTTON1_DOWN_MASK
        mouse(window, MouseEvent.MOUSE_MOVED, x, y, 0, 0)
        mouse(window, MouseEvent.MOUSE_PRESSED, x, y, down, button)
        mouse(window, MouseEvent.MOUSE_RELEASED, x, y, 0, button)
        mouse(window, MouseEvent.MOUSE_CLICKED, x, y, 0, button)
    }

    private fun scroll(window: Window, x: Int, y: Int, notches: Int) {
        val layer = skiaLayer(window)
        val target = target(window, x, y)
        val p = SwingUtilities.convertPoint(layer, x, y, target)
        target.dispatchEvent(
            MouseWheelEvent(target, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0, p.x, p.y, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, notches),
        )
    }

    private fun keyTarget(window: Window): Component = window.focusOwner ?: skiaLayer(window)

    private fun key(window: Window, code: String, mods: String) {
        val vk = KeyEvent::class.java.getField("VK_${code.uppercase()}").getInt(null)
        val modifiers = mods.split(',').filter { it.isNotBlank() }.fold(0) { acc, m ->
            acc or when (m.trim().lowercase()) {
                "ctrl" -> InputEvent.CTRL_DOWN_MASK
                "shift" -> InputEvent.SHIFT_DOWN_MASK
                "alt" -> InputEvent.ALT_DOWN_MASK
                else -> error("unknown modifier $m")
            }
        }
        val target = keyTarget(window)
        val now = System.currentTimeMillis()
        target.dispatchEvent(KeyEvent(target, KeyEvent.KEY_PRESSED, now, modifiers, vk, KeyEvent.CHAR_UNDEFINED))
        target.dispatchEvent(KeyEvent(target, KeyEvent.KEY_RELEASED, now, modifiers, vk, KeyEvent.CHAR_UNDEFINED))
    }

    private fun type(window: Window, text: String) {
        val target = keyTarget(window)
        for (c in text) {
            target.dispatchEvent(KeyEvent(target, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, c))
        }
    }

    private fun tree(window: Window): String {
        val layer = skiaLayer(window)
        val origin = layer.locationOnScreen
        val out = StringBuilder()
        fun walk(context: AccessibleContext, depth: Int) {
            val name = context.accessibleName.orEmpty()
            val description = context.accessibleDescription.orEmpty()
            val role = context.accessibleRole?.toDisplayString().orEmpty()
            val bounds = runCatching {
                val c = context.accessibleComponent ?: return@runCatching ""
                val at = c.locationOnScreen ?: return@runCatching ""
                val size = c.size ?: return@runCatching ""
                " [${at.x - origin.x},${at.y - origin.y} ${size.width}x${size.height}]"
            }.getOrDefault("")
            val text = listOf(name, description).filter { it.isNotBlank() }.joinToString(" — ")
            if (text.isNotBlank() || role != "panel") out.append("  ".repeat(depth)).append(role).append(bounds).append(' ').append(text).append('\n')
            for (i in 0 until context.accessibleChildrenCount) {
                (context.getAccessibleChild(i) as? Accessible)?.accessibleContext?.let { walk(it, depth + 1) }
            }
        }
        walk(layer.accessibleContext, 0)
        return out.toString()
    }
}
