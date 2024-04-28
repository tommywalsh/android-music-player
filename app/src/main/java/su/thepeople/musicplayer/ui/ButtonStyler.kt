package su.thepeople.musicplayer.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.widget.TextView
import su.thepeople.musicplayer.R

/**
 * Android has a fairly flexible and nice way to define styles inside of XML files, which lets you define color/font/etc. choices all in one
 * place. This lets you easily make tweaks to a style, and also to swap styles in and out.
 *
 * That's true so long as you're working solely in XML.  In code, I'd love to be able to say 'myButton.setStyle(R.style.selected)'.  But
 * amazingly, Android has no support for that!  Instead, there is an asinine procedure, where you have to look up "styled attributes", then apply
 * each of them one-by-one to whatever widgets you care about. Then, you have to manually "recycle" the memory used by the list like it's 1994.
 *
 * This class hides all of this complexity from the rest of the code.
 */

class ButtonStyle(private val textColor: Int, private val bgColor: Int) {
    fun applyTo(button: TextView) {
        button.setTextColor(textColor)
        button.setBackgroundColor(bgColor)
    }
}

@SuppressLint("ResourceType")
fun makeStyle(context: Context, resourceId: Int): ButtonStyle {
    val attributeNames = intArrayOf(android.R.attr.textColor, android.R.attr.background)
    val attributeValues = context.obtainStyledAttributes(resourceId, attributeNames)
    val textColor = attributeValues.getColor(0, Color.WHITE)
    val bgColor = attributeValues.getColor(1, Color.BLACK)
    attributeValues.recycle()
    return ButtonStyle(textColor, bgColor)
}

class ButtonStyler(context: Context) {

    private val lockedStyle = makeStyle(context, R.style.LockedButton)
    private val unlockedStyle = makeStyle(context, R.style.UnlockedButton)

    fun styleLocked(button: TextView) {
        lockedStyle.applyTo(button)
    }

    fun styleUnlocked(button: TextView) {
        unlockedStyle.applyTo(button)
    }
}
