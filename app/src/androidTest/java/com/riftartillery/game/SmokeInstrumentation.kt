package com.riftartillery.game

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.content.Context
import com.riftartillery.core.*
import kotlin.math.min

class SmokeInstrumentation : Instrumentation() {
    private lateinit var activity:Activity
    private lateinit var view:BattleView
    @Volatile private var launched:Activity?=null
    private var checks=0
    override fun onCreate(arguments:Bundle?) {super.onCreate(arguments);start()}
    override fun callActivityOnResume(activity:Activity) {super.callActivityOnResume(activity);launched=activity}
    private fun field(name:String):Any?=view.javaClass.getDeclaredField(name).also {it.isAccessible=true}.get(view)
    private fun game()=field("game") as Game
    private fun music()=field("music") as ArenaMusic
    private fun verify(message:String,predicate:()->Boolean) {runOnMainSync {
        if(!predicate()) throw AssertionError(message)
        checks++;android.util.Log.i("RiftSmoke","PASS $checks: $message")
    }}
    private fun click(label:String,prefix:Boolean=false) {
        runOnMainSync {
            val provider=view.accessibilityNodeProvider ?: error("Accessibility provider missing")
            val count=provider.createAccessibilityNodeInfo(View.NO_ID)!!.childCount
            var found=false
            for(id in 1..count) {
                val node=provider.createAccessibilityNodeInfo(id) ?: continue
                val text=node.text?.toString() ?: ""
                if(if(prefix) text.startsWith(label) else text==label) {
                    check(provider.performAction(id,AccessibilityNodeInfo.ACTION_CLICK,null));found=true;break
                }
            }
            check(found) {"Control missing: $label"}
        }
        SystemClock.sleep(170)
    }
    private fun event(action:Int,x:Float,y:Float,down:Long) {runOnMainSync {
        val s=min(view.width/1440f,view.height/810f)
        val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,(view.width-1440*s)/2+x*s,(view.height-810*s)/2+y*s,0)
        view.dispatchTouchEvent(e);e.recycle()
    }}
    private fun tap(x:Float,y:Float) {val t=SystemClock.uptimeMillis();event(MotionEvent.ACTION_DOWN,x,y,t);event(MotionEvent.ACTION_UP,x,y,t);SystemClock.sleep(120)}
    private fun hold(x:Float,y:Float,ms:Long) {val t=SystemClock.uptimeMillis();event(MotionEvent.ACTION_DOWN,x,y,t);SystemClock.sleep(ms);event(MotionEvent.ACTION_UP,x,y,t);SystemClock.sleep(120)}
    private fun pinch() {
        val down=SystemClock.uptimeMillis()
        event(MotionEvent.ACTION_DOWN,600f,400f,down)
        fun two(action:Int,left:Float,right:Float) {runOnMainSync {
            val s=min(view.width/1440f,view.height/810f)
            val properties=Array(2) {i->MotionEvent.PointerProperties().apply {id=i;toolType=MotionEvent.TOOL_TYPE_FINGER}}
            val coordinates=Array(2) {i->MotionEvent.PointerCoords().apply {
                x=(view.width-1440*s)/2+(if(i==0) left else right)*s
                y=(view.height-810*s)/2+400*s;pressure=1f;size=1f
            }}
            val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,2,properties,coordinates,0,0,1f,1f,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0)
            view.dispatchTouchEvent(e);e.recycle()
        }}
        two(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),600f,800f)
        two(MotionEvent.ACTION_MOVE,500f,900f)
        two(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),500f,900f)
        event(MotionEvent.ACTION_UP,500f,400f,down)
        SystemClock.sleep(120)
    }
    private fun waitFor(message:String,timeout:Long=10000,predicate:()->Boolean) {
        val until=SystemClock.uptimeMillis()+timeout
        var done=false
        while(!done && SystemClock.uptimeMillis()<until) {runOnMainSync {done=predicate()};if(!done) SystemClock.sleep(80)}
        verify(message) {done}
    }
    private fun newBattle() {click(if(field("savedGame")==null) "НАЧАТЬ БОЙ  →" else "НОВЫЙ БОЙ  →");SystemClock.sleep(600)}
    private fun menu() {click("II  Пауза");click("Сохранить и в меню")}
    private fun weapon(w:Weapon) {
        runOnMainSync {game().select(w)}
        // Category selection and pagination are exercised separately below.
    }
    override fun onStart() {
        val result=Bundle()
        val began=SystemClock.uptimeMillis()
        try {
            targetContext.startActivity(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val until=SystemClock.uptimeMillis()+15000
            while(launched==null && SystemClock.uptimeMillis()<until) SystemClock.sleep(50)
            activity=launched ?: error("Activity launch timed out")
            SystemClock.sleep(900)
            runOnMainSync {view=activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as BattleView}
            verify("Accessible menu opens") {field("mode")=="menu" && view.accessibilityNodeProvider!=null}
            if(field("musicEnabled")==false) click("Музыка: выкл")
            waitFor("Menu music prepares and plays") {music().isPlaying && music().lastError==null}
            val position=music().position
            waitFor("Decoded music playback advances",5000) {music().position>position}
            click("Музыка: вкл");verify("Music mute pauses player") {!music().isPlaying}
            click("Музыка: выкл");waitFor("Music unmute resumes") {music().isPlaying}
            val audio=targetContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val interrupt=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                .setOnAudioFocusChangeListener { }.build()
            runOnMainSync {check(audio.requestAudioFocus(interrupt)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED)}
            waitFor("Another audio owner pauses game music") {!music().isPlaying}
            runOnMainSync {audio.abandonAudioFocusRequest(interrupt)}
            waitFor("Audio focus return resumes music") {music().isPlaying}
            click("?");verify("Help opens") {field("help")==true};click("Понятно. В бой!")
            verify("Help closes") {field("help")==false}
            val sound=field("sound") as Boolean
            click(if(sound) "Звук: вкл" else "Звук: выкл");verify("Sound changes") {field("sound")!=sound}
            click(if(sound) "Звук: выкл" else "Звук: вкл")
            for(a in Arena.entries) {
                click(a.title);verify("Arena selected: $a") {field("arena")==a}
                waitFor("Distinct looping music for $a") {music().currentArena==a && music().isPlaying && music().looping && music().lastError==null}
            }
            click("Отряд / кузница");verify("Loadout opens") {field("loadoutOpen")==true}
            click("Фантом");click("Сохранить отряд");verify("Race selection persists") {(field("squad") as Loadout).race==Race.PHANTOM}
            click(Arena.ORBIT.title)
            if(field("bot")==true) click("Дуэль / ИИ")
            newBattle();verify("Large orbit hotseat starts") {game().arena==Arena.ORBIT && !game().againstBot && WORLD_W==2880}
            waitFor("All fighters survive large arena spawn") {game().fighters.all {it.alive && it.grounded}}
            click("Обзор");verify("Overview camera enabled") {field("overview")==true}
            click("К бойцу");verify("Follow camera restored") {field("overview")==false}
            click("+");verify("Camera zoom responds") {(field("zoom") as Float)> .9f}
            pinch();verify("Two finger pinch zooms and clamps") {field("cameraManual")==true && field("zoom")==1.25f}
            val cameraX=field("cameraX") as Float;val panDown=SystemClock.uptimeMillis()
            event(MotionEvent.ACTION_DOWN,700f,400f,panDown);event(MotionEvent.ACTION_MOVE,800f,400f,panDown)
            verify("Manual camera pans") {(field("cameraX") as Float)<cameraX-50}
            event(MotionEvent.ACTION_CANCEL,800f,400f,panDown)
            verify("Cancelled touch clears camera input") {field("cameraPointer")==-1 && field("pinchDistance")==0f}
            click("Камера: ручная")
            val time=game().timeLeft
            click("Сменить бойца ↔");verify("Ally switch preserves time") {game().active.id==1 && game().timeLeft<=time && game().timeLeft>time-2}
            click("II  Пауза");val frozen=game().timeLeft;SystemClock.sleep(300)
            verify("Pause freezes battle") {game().timeLeft==frozen}
            verify("Battle pause stops music") {!music().isPlaying}
            click("Продолжить бой");waitFor("Battle resume restores music") {music().isPlaying}
            tap(995f,760f);verify("Touch opens arsenal") {field("arsenal")==true}
            click("Огнестрел");click("Следующая страница");click("АК-47",true)
            verify("Paged arsenal selects AK47") {game().weapon==Weapon.AK47 && field("arsenal")==false}
            tap(850f,770f);verify("Touch power slider") {game().power>.6f}
            tap(800f,350f);verify("Touch aim is finite") {game().angle.isFinite()}
            click("ОГОНЬ  ↗");verify("AK47 uses stock and resolves") {game().active.ammo[Weapon.AK47]==4 && game().phase==Phase.RETREAT}
            click("ОГОНЬ  ↗");verify("Second attack blocked") {game().active.ammo[Weapon.AK47]==4}
            waitFor("Hotseat hands over") {game().turn==2 && game().active.team==1}
            click("Навык");verify("Cyborg shield activates") {game().active.shield==30f}
            runOnMainSync {game().fighters.filter {it.team==1}.forEach {it.hp=0f;it.reviveIn=0};game().update()}
            SystemClock.sleep(250);verify("Victory appears") {game().phase==Phase.ENDED && game().winner==0}
            click("Реванш  →");verify("Rematch resets") {game().turn==1 && game().fighters.all {it.alive}}
            menu();verify("Exit saves match") {targetContext.getFileStreamPath("battle-v1.bin").exists()}
            click("ПРОДОЛЖИТЬ");verify("Continue retains mode") {game().arena==Arena.ORBIT && game().mode==GameMode.DUEL}
            menu()
            for(b in Boss.entries) {
                click("Охота на босса")
                var attempts=0
                while(field("selectedBoss")!=b && attempts++<4) click("Босс:",true)
                newBattle();verify("Boss match starts: $b") {game().mode==GameMode.BOSS && game().boss==b && game().fighters[2].maxHp==b.hp.toFloat()}
                runOnMainSync {game().fighters[2].hp=game().fighters[2].maxHp*.25f;game().skip()}
                waitFor("Boss attacks: $b") {game().bossActionCount>0}
                verify("Boss has phase three: $b") {game().bossPhase==3}
                menu()
            }
            click("Канатная гонка");newBattle()
            verify("Rope race is playable mode") {game().mode==GameMode.ROPE_RACE && game().weapon==Weapon.ROPE && game().fighters.count {it.alive}==1}
            click("Обзор");verify("Race overview has five targets") {game().raceTargets.size==5};menu()
            click("Дуэль / вдвоём");newBattle()
            tap(995f,760f);click("Инженерия");click("Следующая страница");click("Бур / паяльная лампа",true)
            click("ОГОНЬ  ↗");verify("Drill engages without ending turn") {game().active.drillFuel>95 && game().phase==Phase.AIM}
            tap(650f,770f);val x=game().active.x;val rev=game().terrain.revision
            hold(125f,765f,1400)
            verify("Drill moves and removes terrain") {game().active.x>x+30 && game().terrain.revision>rev && game().active.drillFuel<100}
            tap(995f,760f);click("Гаджеты");click("Следующая страница");click("Защитник",true);click("ОГОНЬ  ↗")
            verify("Defender adds real protection") {game().active.shield>=40 && game().devices.any {it.kind==DeviceKind.DEFENDER}}
            click("II  Пауза");click("Сохранить и в меню");click("ПРОДОЛЖИТЬ")
            verify("Save retains drill and defender") {game().active.drillFuel>0 && game().devices.any {it.kind==DeviceKind.DEFENDER}}
            val performance=(activity as MainActivity).performanceReport()
            runOnMainSync {view.suspendGame()}
            verify("Background lifecycle stops music") {!music().isPlaying}
            runOnMainSync {view.resumeGame()}
            click("Продолжить бой");waitFor("Foreground battle can resume music") {music().isPlaying}
            click("II  Пауза");click("Музыка: вкл");click("Продолжить бой")
            verify("Music can stay muted during battle") {!music().isPlaying}
            val oldMusic=music()
            val savedTurn=game().turn;val old=activity;launched=null
            runOnMainSync {old.recreate()}
            val deadline=SystemClock.uptimeMillis()+15000
            while((launched==null || launched===old) && SystemClock.uptimeMillis()<deadline) SystemClock.sleep(50)
            activity=launched ?: error("Activity recreation timed out");SystemClock.sleep(700)
            runOnMainSync {view=activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as BattleView}
            verify("Activity recreation offers save") {field("savedGame")!=null && field("mode")=="menu"}
            verify("Recreation releases old music player") {!oldMusic.isPlaying}
            verify("Music preference survives recreation") {field("musicEnabled")==false && !music().isPlaying}
            click("Музыка: выкл");waitFor("Music can restart after recreation") {music().isPlaying}
            click("ПРОДОЛЖИТЬ");verify("Recreation restores extended match") {game().turn==savedTurn && game().active.drillFuel>0}
            result.putString("stream","\nPASS: $checks Android integration checks\nAPI=${android.os.Build.VERSION.SDK_INT} VIEW=${view.width}x${view.height} DURATION_MS=${SystemClock.uptimeMillis()-began}\n$performance\n")
            finish(Activity.RESULT_OK,result)
        } catch(t:Throwable) {result.putString("stream","\nFAIL after $checks checks: ${t.stackTraceToString()}\n");finish(Activity.RESULT_CANCELED,result)}
    }
}
