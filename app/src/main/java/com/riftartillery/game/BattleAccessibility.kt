package com.riftartillery.game

import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import android.view.accessibility.AccessibilityManager
import android.content.Context

internal data class AccessibleControl(val name:String,val bounds:Rect,val click:()->Unit)

/** Gives the custom Canvas real, individually focusable controls for TalkBack and switches. */
@Suppress("DEPRECATION") // Factory methods and parent bounds are retained for Android 8 compatibility.
internal class BattleAccessibility(private val view:View,private val controls:()->List<AccessibleControl>) : AccessibilityNodeProvider() {
    private var focused= -1
    override fun createAccessibilityNodeInfo(id:Int):AccessibilityNodeInfo? {
        if(id==View.NO_ID) return AccessibilityNodeInfo.obtain(view).also {node->
            view.onInitializeAccessibilityNodeInfo(node)
            controls().indices.forEach {node.addChild(view,it+1)}
        }
        val item=controls().getOrNull(id-1) ?: return null
        return AccessibilityNodeInfo.obtain().also {node->
            node.setSource(view,id);node.setParent(view);node.packageName=view.context.packageName
            node.className="android.widget.Button";node.text=item.name;node.contentDescription=item.name
            node.isEnabled=true;node.isFocusable=true;node.isClickable=true;node.isVisibleToUser=view.visibility==View.VISIBLE
            node.isAccessibilityFocused=focused==id
            node.setBoundsInParent(item.bounds)
            val location=IntArray(2);view.getLocationOnScreen(location)
            node.setBoundsInScreen(Rect(item.bounds).apply {offset(location[0],location[1])})
            node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
            node.addAction(if(focused==id) AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS else AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS)
        }
    }
    override fun performAction(id:Int,action:Int,args:Bundle?):Boolean {
        val item=controls().getOrNull(id-1) ?: return false
        when(action) {
            AccessibilityNodeInfo.ACTION_CLICK->{item.click();view.invalidate();event(id,AccessibilityEvent.TYPE_VIEW_CLICKED);view.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)}
            AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS->{focused=id;event(id,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)}
            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS->{focused= -1;event(id,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)}
            else->return false
        }
        return true
    }
    private fun event(id:Int,type:Int) {
        val manager=view.context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        if(!manager.isEnabled) return
        val e=AccessibilityEvent.obtain(type);e.packageName=view.context.packageName;e.className="android.widget.Button";e.setSource(view,id)
        view.parent?.requestSendAccessibilityEvent(view,e)
    }
}
