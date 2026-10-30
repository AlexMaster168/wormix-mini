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
    private fun setAim(angle:Float?=null,power:Float?=null) {
        tap(700f,765f)
        if(angle!=null) tap(320+(angle+180)/360*800,280f)
        if(power!=null) tap(320+power*800,490f)
        click("Готово")
    }
    private fun selectThroughArsenal(w:Weapon) {
        tap(1000f,765f);click(w.category.title)
        repeat(Weapon.entries.filter {it.category==w.category}.indexOf(w)/6) {click("Следующая страница")}
        click(w.title,true)
    }
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
            val shopPrefs=targetContext.getSharedPreferences("rift",Context.MODE_PRIVATE)
            runOnMainSync {shopPrefs.edit().putInt("scrap",150).putString("shopStock","").putBoolean("useSupplies",true).commit()}
            click("Магазин");verify("Shop opens from menu") {field("shopOpen")==true}
            click("Купить / 30");verify("Shop atomically spends wallet and stores pack") {shopPrefs.getInt("scrap",0)==120 && Market.decode(shopPrefs.getString("shopStock","").orEmpty()).packs[0]==1}
            click("Готово");newBattle()
            verify("Purchased pack applies to new battle once") {game().active.ammo[Weapon.AK47]==Weapon.AK47.stock+3 && Market.decode(shopPrefs.getString("shopStock","").orEmpty()).packs[0]==0}
            runOnMainSync {game().active.hp=40f};hold(125f,765f,600)
            click("Использовать: Аптечная станция")
            verify("Interactive medical station heals without consuming turn") {game().active.hp==70f && game().phase==Phase.AIM}
            menu();click("Магазин");click("Ауры");click("Купить / 40")
            verify("Aura purchase equips permanent cosmetic") {Market.decode(shopPrefs.getString("shopStock","").orEmpty()).selected==1 && shopPrefs.getInt("scrap",0)==80}
            click("Аура: Лайм");verify("Owned default aura can be equipped") {Market.decode(shopPrefs.getString("shopStock","").orEmpty()).selected==0}
            click("Надеть");verify("Purchased aura can be reequipped for free") {Market.decode(shopPrefs.getString("shopStock","").orEmpty()).selected==1 && shopPrefs.getInt("scrap",0)==80}
            runOnMainSync {shopPrefs.edit().putInt("scrap",0).commit()};click("Купить / 50")
            verify("Unaffordable UI purchase changes neither wallet nor ownership") {shopPrefs.getInt("scrap",-1)==0 && Market.decode(shopPrefs.getString("shopStock","").orEmpty()).owned==3}
            runOnMainSync {shopPrefs.edit().putInt("scrap",150).commit()};click("Готово")
            if(field("musicEnabled")==false) click("Музыка: выкл")
            click("Выбор музыки");verify("Music picker opens") {field("musicOpen")==true}
            for(t in MusicTrack.entries) {click(t.title);waitFor("Manual track plays: $t") {music().currentTrack==t && music().isPlaying}}
            click("Следующий трек");waitFor("Track next wraps") {music().currentTrack==MusicTrack.NEON}
            click("Предыдущий трек");waitFor("Track previous wraps") {music().currentTrack==MusicTrack.BOSS}
            click("Готово");click(Arena.CRYSTAL.title)
            verify("Manual music survives arena selection") {music().currentTrack==MusicTrack.BOSS}
            click("Выбор музыки");click("По локации");click("Готово")
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
            for(a in listOf(Arena.CANYON,Arena.HARBOR,Arena.JUNGLE,Arena.RUINS,Arena.STORM,Arena.CRYSTAL)) {
                click(a.title);newBattle()
                waitFor("New arena starts with safe spawns: $a") {game().arena==a && game().fighters.all {it.alive && it.grounded}}
                click("II  Пауза");click("Сохранить и в меню")
            }
            for(w in listOf(Weapon.PLASMA_RIFLE,Weapon.CLUSTER_ROCKET,Weapon.VORTEX,Weapon.HE_ROCKET,Weapon.WATERMELON,Weapon.HEAVY_FLAMER,Weapon.RAILGUN,Weapon.SMOKE,Weapon.TRIPLE_ROCKET,Weapon.EMP_GRENADE,Weapon.MIRROR_GUN,Weapon.AUTO_SHOTGUN,Weapon.HEAL_GRENADE,Weapon.BLACK_HOLE,Weapon.ICE_SHARDS,Weapon.THUNDER_ORB)) {
                newBattle();waitFor("Ready to test $w") {game().active.grounded}
                selectThroughArsenal(w);verify("New weapon selected through arsenal: $w") {game().weapon==w}
                val stock=game().active.ammo.getValue(w);tap(1300f,765f)
                verify("New weapon fires and consumes one round: $w") {game().active.ammo.getValue(w)==stock-1 && game().phase!=Phase.AIM}
                click("II  Пауза");click("Сохранить и в меню")
            }
            click(Arena.RUINS.title);newBattle();waitFor("Ready to test non-consuming tools") {game().active.grounded}
            tap(700f,765f);verify("Large aim panel opens") {field("aimPanel")==true}
            val aimTime=game().timeLeft;SystemClock.sleep(250);verify("Aim panel freezes battle") {game().timeLeft==aimTime}
            val oldAngle=game().angle;click("+1°");verify("Aim one-degree button") {kotlin.math.abs(game().angle-oldAngle-1)<.01f}
            tap(520f,280f);tap(960f,490f);verify("Long aim and power sliders") {kotlin.math.abs(game().angle+90)<1 && kotlin.math.abs(game().power-.8f)<.01f}
            val sliderDrag=SystemClock.uptimeMillis();event(MotionEvent.ACTION_DOWN,520f,280f,sliderDrag);event(MotionEvent.ACTION_MOVE,920f,280f,sliderDrag);event(MotionEvent.ACTION_UP,920f,280f,sliderDrag)
            verify("Aim slider follows finger continuously") {kotlin.math.abs(game().angle-90)<1}
            val aimStock=game().active.ammo.toMap();val aimActor=game().active.x
            tap(1300f,765f);hold(125f,765f,160)
            verify("Aim overlay blocks fire and movement underneath") {game().active.ammo==aimStock && game().active.x==aimActor && game().move==0f}
            click("Готово");verify("Aim panel closes") {field("aimPanel")==false}
            val cancelled=SystemClock.uptimeMillis();event(MotionEvent.ACTION_DOWN,125f,765f,cancelled);event(MotionEvent.ACTION_CANCEL,125f,765f,cancelled)
            verify("Cancelled touch releases movement") {game().move==0f && field("movePointer")==-1}
            val drag=SystemClock.uptimeMillis();event(MotionEvent.ACTION_DOWN,800f,400f,drag);val dragAngle=game().angle
            event(MotionEvent.ACTION_MOVE,860f,430f,drag);event(MotionEvent.ACTION_UP,860f,430f,drag)
            verify("Field drag finely adjusts direction") {kotlin.math.abs(game().angle-dragAngle-10.8f)<.5f}
            runOnMainSync {game().active.hp=40f};selectThroughArsenal(Weapon.HEAL);val medTime=game().timeLeft
            click("ОГОНЬ  ↗");verify("Medkit keeps turn and spends stock") {game().phase==Phase.AIM && game().active.hp==90f && game().active.ammo[Weapon.HEAL]==1 && game().timeLeft>medTime-1}
            selectThroughArsenal(Weapon.TELEPORT);val teleportTurn=game().turn;val teleportY=game().active.y
            tap(800f,400f);verify("Teleport keeps turn and attack") {game().phase==Phase.AIM && game().turn==teleportTurn && game().active.ammo[Weapon.TELEPORT]==1 && game().active.y<teleportY-30}
            selectThroughArsenal(Weapon.JETBOARD);click("ОГОНЬ  ↗");verify("Jetboard activates without attack") {game().active.jetboard && game().active.fuel>150 && game().phase==Phase.AIM}
            val boardY=game().active.y;hold(450f,765f,750);verify("Flight button lifts jetboard") {game().active.y<boardY-45 && game().active.fuel<150}
            menu();newBattle();waitFor("Ready to test unit collision") {game().active.grounded}
            runOnMainSync {game().fighters[1].apply {x=game().active.x+65;y=game().active.y;vx=0f;vy=0f}}
            val otherX=game().fighters[1].x;hold(125f,765f,1200)
            verify("Walking touch passes through ally without pushing") {kotlin.math.abs(game().fighters[1].x-otherX)<.1f && game().active.x>otherX+33}
            menu()
            click("Отряд / кузница");verify("Loadout opens") {field("loadoutOpen")==true}
            click("Фантом");click("Сохранить отряд");verify("Race selection persists") {(field("squad") as Loadout).race==Race.PHANTOM}
            click(Arena.ORBIT.title)
            if(field("bot")==true) click("Дуэль / ИИ")
            newBattle();verify("Large orbit hotseat starts") {game().arena==Arena.ORBIT && !game().againstBot && WORLD_W==2880}
            waitFor("All fighters survive large arena spawn") {game().fighters.all {it.alive && it.grounded}}
            verify("New turn has 59 seconds") {game().timeLeft in 55f..59f}
            tap(1290f,242f);verify("Touch enemy locator moves camera") {field("cameraManual")==true && (field("cameraX") as Float)>1700}
            click("К бойцу");verify("Locator return follows active fighter") {field("cameraManual")==false}
            val jumpX=game().active.x
            tap(230f,765f);waitFor("Jump button launches forward immediately (x=${game().active.x}, vy=${game().active.vy}, grounded=${game().active.grounded})",2000) {game().active.x>jumpX+5 && game().active.vy<0}
            waitFor("Short jump lands") {game().active.grounded}
            hold(125f,765f,1000);setAim(-90f)
            val ropeY=game().active.y;tap(355f,765f)
            waitFor("Rope button automatically pulls fighter upward") {game().anchor!=null && game().active.y<ropeY-90}
            click("Отцепить");verify("Rope button detaches") {game().anchor==null}
            click("Обзор");verify("Overview camera enabled") {field("overview")==true}
            click("К бойцу");verify("Follow camera restored") {field("overview")==false}
            click("+");verify("Camera zoom responds") {(field("zoom") as Float)> .9f}
            pinch();verify("Two finger pinch zooms and clamps") {field("cameraManual")==true && field("zoom")==1.6f}
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
            setAim(power=.85f);verify("Touch power slider") {game().power>.8f}
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
            setAim(26f);val x=game().active.x;val rev=game().terrain.revision
            hold(125f,765f,1400)
            verify("Drill moves and removes terrain") {game().active.x>x+30 && game().terrain.revision>rev && game().active.drillFuel<100}
            tap(995f,760f);click("Гаджеты");click("Следующая страница");click("Защитник",true);click("ОГОНЬ  ↗")
            verify("Defender adds real protection") {game().active.shield>=40 && game().devices.any {it.kind==DeviceKind.DEFENDER}}
            click("II  Пауза");click("Сохранить и в меню");click("ПРОДОЛЖИТЬ")
            verify("Save retains drill and defender") {game().active.drillFuel>0 && game().devices.any {it.kind==DeviceKind.DEFENDER}}
            click("II  Пауза");click("Выбор музыки");click(MusicTrack.RETRO.title)
            waitFor("Music picker previews track while paused") {music().currentTrack==MusicTrack.RETRO && music().isPlaying}
            val musicTime=game().timeLeft;val musicAngle=game().angle;val musicStock=game().active.ammo.toMap()
            tap(1300f,765f);hold(125f,765f,160)
            verify("Music overlay blocks battle controls and timer") {game().timeLeft==musicTime && game().angle==musicAngle && game().active.ammo==musicStock && game().move==0f}
            click("Готово");waitFor("Closing picker restores paused audio state") {!music().isPlaying};click("Продолжить бой")
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
            verify("Selected track survives recreation") {field("musicSelection")==MusicTrack.RETRO.ordinal}
            verify("Purchased aura survives Activity recreation") {Market.decode(shopPrefs.getString("shopStock","").orEmpty()).selected==1}
            click("Музыка: выкл");waitFor("Music can restart after recreation") {music().isPlaying}
            click("ПРОДОЛЖИТЬ");verify("Recreation restores extended match") {game().turn==savedTurn && game().active.drillFuel>0}
            result.putString("stream","\nPASS: $checks Android integration checks\nAPI=${android.os.Build.VERSION.SDK_INT} VIEW=${view.width}x${view.height} DURATION_MS=${SystemClock.uptimeMillis()-began}\n$performance\n")
            finish(Activity.RESULT_OK,result)
        } catch(t:Throwable) {result.putString("stream","\nFAIL after $checks checks: ${t.stackTraceToString()}\n");finish(Activity.RESULT_CANCELED,result)}
    }
}
