package com.riftartillery.game

import android.content.Context
import android.graphics.*
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.MotionEvent
import android.view.View
import com.riftartillery.core.*
import kotlin.math.*
import android.util.AtomicFile
import java.io.File
import java.io.IOException

/** All coordinates are in the game's logical 1440 × 810 viewport. */
class BattleView(context: Context) : View(context) {
    private val prefs = context.getSharedPreferences("rift",Context.MODE_PRIVATE)
    private var sound = prefs.getBoolean("sound",true)
    private var musicEnabled = prefs.getBoolean("music",true)
    private val music=ArenaMusic(context)
    private var tone: ToneGenerator? = try { ToneGenerator(AudioManager.STREAM_MUSIC,45) } catch (_: RuntimeException) { null }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titleFont = Typeface.create("sans-serif-black",Typeface.BOLD)
    private val font = Typeface.create("sans-serif-medium",Typeface.NORMAL)
    private val mono = Typeface.create("monospace",Typeface.BOLD)
    private val bg = Color.rgb(10,17,30)
    private val panel = Color.rgb(20,32,49)
    private val muted = Color.rgb(132,153,175)
    private val white = Color.rgb(237,244,247)
    private val lime = Color.rgb(177,241,112)
    private val cyan = Color.rgb(102,216,230)
    private val coral = Color.rgb(255,130,113)
    private var mode = "menu"
    private var arena = Arena.NEON
    private var bot = true
    private var gameMode=GameMode.DUEL
    private var selectedBoss=Boss.INFERNO
    private var squad=Loadout(Race.entries[prefs.getInt("race",0).coerceIn(0,6)],Hat.entries[prefs.getInt("hat",0).coerceIn(0,3)],Artifact.entries[prefs.getInt("artifact",0).coerceIn(0,3)],Upgrade.entries[prefs.getInt("upgrade",0).coerceIn(0,2)])
    private var loadoutOpen=false
    private var arsenalCategory=Category.BALLISTIC
    private var arsenalPage=0
    private val heroes=BitmapFactory.decodeStream(context.assets.open("heroes-atlas.png"))
    private val bossArt=BitmapFactory.decodeStream(context.assets.open("bosses-atlas.png"))
    private var cameraX=500f
    private var cameraY=850f
    private var zoom=.75f
    private var overview=false
    private var cameraTurn= -1
    private var cameraManual=false
    private var cameraPointer= -1
    private var panX=0f;private var panY=0f
    private var pinchDistance=0f
    private var frameMs=0f
    private var maxFrameMs=0f
    private val drawSamples=ArrayList<Float>()
    private var game: Game? = null
    private val saveFile = AtomicFile(File(context.filesDir,"battle-v1.bin"))
    private var savedGame: Game? = readSavedGame()
    private var lastSavedTurn = -1
    private var paused = false
    private var help = false
    private var arsenal = false
    private var lastNanos = 0L
    private var accumulator = 0f
    private var running = true
    private var scale = 1f
    private var ox = 0f
    private var oy = 0f
    private var terrainBitmap: Bitmap? = null
    private var terrainRevision = -1
    private var ambient = 0f
    private var movePointer = -1
    private var reelPointer = -1
    private var dragPointer = -1
    private var dragControl = ""
    private var lastShots = 0
    private var resultSaved = false
    private var matchSeed = prefs.getInt("seed",41)
    private data class Button(val rect: RectF, val action: () -> Unit,val label:String="Выбрать")
    private val buttons = mutableListOf<Button>()
    private val accessibility=BattleAccessibility(this) {
        fun bounds(r:RectF)=Rect((ox+r.left*scale).toInt(),(oy+r.top*scale).toInt(),(ox+r.right*scale).toInt(),(oy+r.bottom*scale).toInt())
        val list=buttons.map {AccessibleControl(it.label,bounds(it.rect),it.action)}.toMutableList()
        val g=game
        if(mode=="battle" && !paused && !help && !arsenal && !loadoutOpen && g?.canControl==true) {
            list+=AccessibleControl("Угол меньше на 5 градусов: ${g.angle.toInt()}",bounds(RectF(550f,730f,635f,805f))) {g.angle=(g.angle-5).coerceAtLeast(-180f)}
            list+=AccessibleControl("Угол больше на 5 градусов: ${g.angle.toInt()}",bounds(RectF(635f,730f,725f,805f))) {g.angle=(g.angle+5).coerceAtMost(180f)}
            list+=AccessibleControl("Уменьшить силу: ${(g.power*100).toInt()} процентов",bounds(RectF(745f,730f,820f,805f))) {g.power=(g.power-.1f).coerceAtLeast(0f)}
            list+=AccessibleControl("Увеличить силу: ${(g.power*100).toInt()} процентов",bounds(RectF(820f,730f,900f,805f))) {g.power=(g.power+.1f).coerceAtMost(1f)}
            g.fighters.filter {it.alive && it.team!=g.active.team}.forEach {enemy->
                val sx=720+(enemy.x-cameraX)*zoom;val sy=411+(enemy.y-cameraY)*zoom
                if(sx in 0f..1440f && sy in 215f..700f) list+=AccessibleControl("Навести на ${enemy.name}, ${enemy.hp.toInt()} здоровья",bounds(RectF(sx-30,sy-75,sx+30,sy+20))) {if(g.weapon.targeted) g.fire(enemy.x,enemy.y) else aim(g,enemy.x,enemy.y-20)}
            }
        }
        list
    }
    override fun getAccessibilityNodeProvider():android.view.accessibility.AccessibilityNodeProvider=accessibility

    init { isFocusable = true; contentDescription = "RIFT. Пошаговая артиллерия. Управление касанием." }
    fun suspendGame() { running=false;music.sync(arena,musicEnabled,false);lastNanos=0L; if(mode=="battle") {paused=true;checkpoint()};clearInput() }
    fun resumeGame() { running=true;lastNanos=0L;invalidate() }
    fun isBattleRunning()=running && mode=="battle" && !paused && !arsenal && !help && !loadoutOpen
    fun release() { music.release();tone?.release();tone=null;terrainBitmap?.recycle();heroes.recycle();bossArt.recycle() }
    fun back() {
        when { loadoutOpen->loadoutOpen=false;help -> help=false; arsenal -> arsenal=false; mode=="battle" -> {paused=!paused;clearInput()}; else -> help=true }
        invalidate()
    }
    private fun beep() { if(sound) tone?.startTone(ToneGenerator.TONE_PROP_BEEP,65) }
    private fun toggleMusic() {musicEnabled=!musicEnabled;prefs.edit().putBoolean("music",musicEnabled).apply()}
    private fun toggleSound() {sound=!sound;prefs.edit().putBoolean("sound",sound).apply();beep()}
    private fun readSavedGame():Game? {
        if(!saveFile.baseFile.exists()) return null
        return try {saveFile.openRead().use {Game.load(it)}.takeIf {it.phase!=Phase.ENDED}}
        catch(_:IOException) {saveFile.delete();null}
        catch(_:IllegalArgumentException) {saveFile.delete();null}
    }
    private fun checkpoint() {
        val g=game ?: return
        if(g.phase==Phase.ENDED) {saveFile.delete();savedGame=null;return}
        var stream:java.io.FileOutputStream?=null
        try {
            stream=saveFile.startWrite();g.save(stream);saveFile.finishWrite(stream)
            savedGame=g;lastSavedTurn=g.turn
        } catch(_:IOException) {saveFile.failWrite(stream)}
    }
    private fun continueSaved() {
        val saved=savedGame ?: return
        game=saved;arena=saved.arena;bot=saved.againstBot;gameMode=saved.mode;selectedBoss=saved.boss;squad=saved.squad;mode="battle";paused=false
        arsenal=false;help=false;resultSaved=false;terrainRevision=-1;lastSavedTurn=saved.turn
        lastNanos=0;accumulator=0f;clearInput();beep()
    }
    private fun start() {
        matchSeed++;prefs.edit().putInt("seed",matchSeed).apply()
        if(gameMode==GameMode.BOSS) arena=selectedBoss.arena
        game=Game(arena,if(gameMode==GameMode.BOSS) true else bot,matchSeed,gameMode,selectedBoss,squad);mode="battle";paused=false;arsenal=false;help=false
        if(gameMode==GameMode.ROPE_RACE) game?.select(Weapon.ROPE)
        cameraTurn= -1;cameraManual=false;overview=false;zoom=.75f
        terrainBitmap?.recycle();terrainBitmap=null;terrainRevision=-1;resultSaved=false;lastShots=0
        accumulator=0f;lastNanos=0;clearInput();beep()
        checkpoint()
    }
    private fun clearInput() { movePointer=-1;reelPointer=-1;dragPointer=-1;cameraPointer=-1;pinchDistance=0f;game?.move=0f;game?.reel=0f }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now=System.nanoTime()
        val elapsed=if(lastNanos==0L) 0f else ((now-lastNanos)/1e9f).coerceAtMost(.1f)
        lastNanos=now
        ambient+=elapsed
        if(running && mode=="battle" && !paused && !help && !arsenal && !loadoutOpen) {
            accumulator+=elapsed
            while(accumulator>=STEP) {game?.update();accumulator-=STEP}
            val g=game!!
            if(g.shots.size>lastShots) beep()
            lastShots=g.shots.size
            if(g.phase==Phase.ENDED && !resultSaved) {
                resultSaved=true
                val editor=prefs.edit().putInt("matches",prefs.getInt("matches",0)+1)
                if(g.winner==0) editor.putInt("wins",prefs.getInt("wins",0)+1)
                if(g.winner==0) {
                    editor.putInt("scrap",prefs.getInt("scrap",150)+if(g.mode==GameMode.BOSS) 180 else 60)
                    if(g.mode==GameMode.BOSS) editor.putInt("bosses",prefs.getInt("bosses",0) or (1 shl g.boss.ordinal))
                    if(g.mode==GameMode.ROPE_RACE) editor.putFloat("ropeBest",min(prefs.getFloat("ropeBest",9999f),g.raceElapsed))
                }
                editor.apply()
                saveFile.delete();savedGame=null
            }
            if(g.phase!=Phase.ENDED && lastSavedTurn!=g.turn) checkpoint()
        } else accumulator=0f
        scale=min(width/1440f,height/810f)
        ox=(width-1440f*scale)/2;oy=(height-810f*scale)/2
        canvas.drawColor(bg);canvas.save();canvas.translate(ox,oy);canvas.scale(scale,scale)
        buttons.clear()
        if(mode=="menu") drawMenu(canvas) else drawBattle(canvas)
        if(help) drawHelp(canvas)
        if(loadoutOpen) drawLoadout(canvas)
        canvas.restore()
        music.sync(if(mode=="battle") game?.arena ?: arena else arena,musicEnabled,running && !paused)
        frameMs=(System.nanoTime()-now)/1e6f;maxFrameMs=max(maxFrameMs,frameMs)
        if(mode=="battle" && !paused && !help && !arsenal && drawSamples.size<5000) drawSamples+=frameMs
        if(running) {if(mode=="menu" || paused || help || arsenal || loadoutOpen) postInvalidateDelayed(33) else postInvalidateOnAnimation()}
    }
    private fun rect(c:Canvas,x:Float,y:Float,w:Float,h:Float,color:Int,r:Float=0f) {
        paint.color=color;paint.style=Paint.Style.FILL;paint.shader=null
        if(r>0) c.drawRoundRect(x,y,x+w,y+h,r,r,paint) else c.drawRect(x,y,x+w,y+h,paint)
    }
    private fun line(c:Canvas,x:Float,y:Float,x2:Float,y2:Float,color:Int,stroke:Float=1f) {
        paint.color=color;paint.strokeWidth=stroke;paint.shader=null;c.drawLine(x,y,x2,y2,paint)
    }
    private fun circle(c:Canvas,x:Float,y:Float,r:Float,color:Int) {paint.color=color;paint.shader=null;c.drawCircle(x,y,r,paint)}
    private fun text(c:Canvas,s:String,x:Float,y:Float,size:Float=20f,color:Int=white,type:Typeface=font) {
        paint.color=color;paint.textSize=size;paint.typeface=type;paint.shader=null;paint.style=Paint.Style.FILL;c.drawText(s,x,y,paint)
    }
    private fun button(c:Canvas,label:String,x:Float,y:Float,w:Float,h:Float,fill:Int=panel,ink:Int=white,action:()->Unit) {
        rect(c,x,y,w,h,fill,12f)
        paint.typeface=font;paint.textSize=18f
        text(c,label,x+(w-paint.measureText(label))/2,y+h/2+6,18f,ink)
        val accessible=when(label) {"←"->if(arsenal) "Предыдущая страница" else "Шаг влево";"→"->if(arsenal) "Следующая страница" else "Шаг вправо";"↑"->"Подтянуть канат или тяга ранца";"↓"->"Удлинить канат";else->label}
        buttons+=Button(RectF(x,y,x+w,y+h),action,accessible)
    }
    private fun background(c:Canvas,a:Arena) {
        val top=when(a) { Arena.NEON -> Color.rgb(11,22,38);Arena.VOLCANO -> Color.rgb(34,20,32);Arena.ORBIT -> Color.rgb(15,18,43);Arena.CHEMICAL->Color.rgb(18,38,32);Arena.ARCTIC->Color.rgb(20,39,59);Arena.SCRAP->Color.rgb(38,30,25) }
        paint.shader=LinearGradient(0f,0f,0f,750f,top,bg,Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,1440f,810f,paint);paint.shader=null
        for(i in 0..55) {
            val x=(i*193%1440).toFloat();val y=(i*73%470).toFloat()
            circle(c,x,y,if(i%5==0) 1.6f else .8f,Color.argb(70+(sin(ambient+i)*30).toInt(),160,200,220))
        }
        circle(c,1170f,170f,64f,Color.rgb(27,44,61))
        circle(c,1195f,150f,58f,top)
        if(a==Arena.NEON) for(i in 0..22) {
            val x=i*70f-12;val h=100+(i*71%230)
            rect(c,x,610f-h,55f,h.toFloat(),Color.rgb(17,34,48))
            for(j in 0..h/30) for(k in 0..2) if((i+j+k)%3!=0) rect(c,x+8+k*15,620f-h+j*25,4f,7f,Color.argb(65,90,190,200))
            if(i%4==0) line(c,x+4,610f-h,x+51,610f-h,Color.argb(100,100,220,220),2f)
        } else if(a==Arena.VOLCANO) {
            for(i in 0..6) { val p=Path();p.moveTo(i*260f-100,680f);p.lineTo(i*260f+70,330f+(i%3)*50);p.lineTo(i*260f+270,680f);p.close();paint.color=Color.rgb(43,31,43);c.drawPath(p,paint) }
        } else if(a==Arena.CHEMICAL) {
            for(i in 0..8) {
                val x=i*190f;val top=330f+(i%3)*55
                rect(c,x,top,100f,340f,Color.rgb(31,57,47),15f)
                line(c,x,top+70,x+160,top+70,Color.rgb(54,80,61),12f)
                rect(c,x+25,top-90,40f,95f,Color.rgb(39,62,53),4f)
                for(j in 0..3) circle(c,x+45+sin(ambient*.2f+j)*30,top-100-j*35f,18f+j*5,Color.argb(18,140,170,130))
            }
        } else if(a==Arena.ARCTIC) {
            for(i in 0..7) {val p=Path();p.moveTo(i*230f-100,680f);p.lineTo(i*230f+80,250f+(i%3)*40);p.lineTo(i*230f+270,680f);p.close();paint.color=Color.rgb(34,62,82);c.drawPath(p,paint)
                val snow=Path();snow.moveTo(i*230f+80,250f+(i%3)*40);snow.lineTo(i*230f+25,380f);snow.lineTo(i*230f+75,350f);snow.lineTo(i*230f+125,387f);snow.close();paint.color=Color.rgb(91,125,147);c.drawPath(snow,paint)}
            for(i in 0..35) circle(c,(i*137%1440).toFloat(),((i*81+ambient*8)%640),2f,Color.argb(120,220,240,250))
        } else if(a==Arena.SCRAP) {
            for(i in 0..7) {val x=i*230f;rect(c,x,440f+(i%2)*60,160f,210f,Color.rgb(52,44,37),20f);circle(c,x+80,440f,52f,Color.rgb(64,51,42));rect(c,x+35,420f,90f,18f,Color.rgb(86,75,51),4f);line(c,x+20,510f,x-70,600f,Color.rgb(62,50,40),30f)}
            line(c,1100f,200f,1100f,560f,Color.rgb(70,57,44),16f);line(c,880f,210f,1330f,210f,Color.rgb(70,57,44),16f);line(c,970f,210f,970f,380f,muted,2f)
        } else {
            paint.color=Color.rgb(37,42,68);paint.style=Paint.Style.STROKE;paint.strokeWidth=2f
            c.drawOval(700f,180f,1370f,480f,paint);paint.style=Paint.Style.FILL
            for(i in 0..8) rect(c,i*180f,430f+(i%3)*40,100f,7f,Color.rgb(40,49,74))
        }
    }
    private fun drawMenu(c:Canvas) {
        background(c,arena)
        rect(c,58f,45f,38f,38f,lime,10f);text(c,"R",68f,73f,27f,bg,titleFont)
        text(c,"RIFT / ARTILLERY",111f,72f,20f,white,mono)
        text(c,"ЛОКАЛЬНЫЕ СРАЖЕНИЯ",111f,96f,11f,muted,mono)
        button(c,if(musicEnabled) "Музыка: вкл" else "Музыка: выкл",725f,45f,190f,42f) {toggleMusic()}
        button(c,if(sound) "Звук: вкл" else "Звук: выкл",1130f,45f,142f,42f) {toggleSound()}
        button(c,"?",1290f,45f,70f,42f) {help=true}
        text(c,"ПРИЦЕЛЬСЯ. ВЫСТРЕЛИ. ВЫЖИВИ.",62f,168f,14f,lime,mono)
        text(c,"Маленький отряд.",58f,237f,53f,white,titleFont)
        text(c,"Большой разлом.",58f,294f,53f,white,titleFont)
        text(c,"Пошаговая артиллерия на разрушаемых островах.",62f,338f,21f,muted)
        text(c,"Два бойца. Один выстрел. Тысяча способов победить.",62f,370f,21f,muted)
        // Illustrated battle diorama is drawn natively, without third-party art.
        val path=Path();path.moveTo(830f,402f);path.lineTo(873f,346f);path.lineTo(1010f,357f);path.lineTo(1080f,406f);path.lineTo(1030f,540f);path.lineTo(920f,583f);path.lineTo(850f,493f);path.close()
        paint.color=Color.rgb(43,64,71);c.drawPath(path,paint)
        line(c,870f,350f,1006f,363f,lime,6f)
        val p2=Path();p2.moveTo(1160f,500f);p2.lineTo(1220f,453f);p2.lineTo(1352f,466f);p2.lineTo(1390f,523f);p2.lineTo(1324f,633f);p2.lineTo(1200f,589f);p2.close();paint.color=Color.rgb(39,53,67);c.drawPath(p2,paint)
        line(c,1220f,456f,1350f,470f,cyan,5f)
        fighter(c,944f,280f,0,squad.race,true,3.8f)
        fighter(c,1286f,410f,1,if(gameMode==GameMode.BOSS) selectedBoss.race else Race.CYBORG,false,2.8f)
        for(i in 0..17) {val t=i/17f;circle(c,980+280*t,310-100*sin(t*PI).toFloat()+110*t,2.4f,Color.argb(170,177,241,112))}
        text(c,"01 / ВЫБЕРИ АРЕНУ",62f,414f,14f,muted,mono)
        Arena.entries.forEachIndexed {i,a ->
            val x=62f+(i%3)*238;val y=430f+(i/3)*66
            val selected=a==arena
            rect(c,x,y,222f,58f,if(selected) Color.rgb(39,57,55) else panel,12f)
            if(selected) rect(c,x,y+10,3f,38f,lime,1f)
            text(c,a.title,x+13,y+25,16f)
            text(c,"2880 × 1440 / ${if(a==Arena.ORBIT) "0.6" else "1.0"} G",x+13,y+45,10f,muted,mono)
            buttons+=Button(RectF(x,y,x+222,y+58),{arena=a;beep()},a.title)
        }
        text(c,"02 / РЕЖИМ БОЯ",62f,587f,14f,muted,mono)
        button(c,if(bot) "Дуэль / ИИ" else "Дуэль / вдвоём",62f,606f,220f,50f,if(gameMode==GameMode.DUEL) lime else panel,if(gameMode==GameMode.DUEL) bg else white) {if(gameMode==GameMode.DUEL) bot=!bot;gameMode=GameMode.DUEL}
        button(c,"Охота на босса",294f,606f,220f,50f,if(gameMode==GameMode.BOSS) lime else panel,if(gameMode==GameMode.BOSS) bg else white) {gameMode=GameMode.BOSS}
        button(c,"Канатная гонка",526f,606f,234f,50f,if(gameMode==GameMode.ROPE_RACE) lime else panel,if(gameMode==GameMode.ROPE_RACE) bg else white) {gameMode=GameMode.ROPE_RACE}
        button(c,"Отряд / кузница",850f,505f,510f,50f) {loadoutOpen=true}
        button(c,"Босс: ${selectedBoss.title}  →",850f,571f,510f,50f,if(gameMode==GameMode.BOSS) Color.rgb(53,38,44) else panel) {selectedBoss=Boss.entries[(selectedBoss.ordinal+1)%Boss.entries.size];gameMode=GameMode.BOSS}
        text(c,if(gameMode==GameMode.BOSS) selectedBoss.briefing else "7 рас • ${Weapon.entries.size} оружия и инструментов • 6 арен",850f,650f,15f,muted)
        if(savedGame==null) button(c,"НАЧАТЬ БОЙ  →",62f,686f,500f,66f,lime,bg) {start()}
        else {
            button(c,"НОВЫЙ БОЙ  →",62f,686f,244f,66f,lime,bg) {start()}
            button(c,"ПРОДОЛЖИТЬ",318f,686f,244f,66f,Color.rgb(37,65,58),lime) {continueSaved()}
        }
        text(c,"30 сек / ход",590f,715f,16f,white,mono)
        text(c,"${Weapon.entries.size} оружия и инструментов",590f,742f,13f,muted)
        line(c,833f,689f,1360f,689f,Color.rgb(42,59,73))
        text(c,"ТВОЙ ПОСЛУЖНОЙ СПИСОК",840f,721f,12f,muted,mono)
        text(c,"${prefs.getInt("wins",0)} ПОБЕД",840f,758f,26f,lime,titleFont)
        text(c,"${prefs.getInt("matches",0)} завершённых боёв",1050f,755f,16f,muted)
        text(c,"RIFT  /  0.2.1",62f,792f,11f,muted,mono)
        text(c,"ОФЛАЙН • БЕЗ РЕКЛАМЫ • БЕЗ ПОКУПОК",1030f,792f,11f,muted,mono)
    }
    private fun fighter(c:Canvas,x:Float,y:Float,team:Int,race:Race,active:Boolean,s:Float=1f) {
        c.save();c.translate(x,y);c.scale(s,s)
        val color=if(team==0) lime else coral
        paint.color=Color.argb(100,0,0,0);c.drawOval(-20f,12f,20f,24f,paint)
        val walking=mode=="battle" && active && abs(game?.active?.vx ?: 0f)>20
        val row=if(walking && (ambient*9).toInt()%2==1) 1 else 0
        val col=when(race) {Race.DRAGON->0;Race.CYBORG->1;Race.MUTANT->2;Race.DEMON->3;Race.MERCENARY->4;Race.UNDEAD->5;Race.PHANTOM->6}
        val cw=heroes.width/7;val ch=heroes.height/2
        paint.color=Color.WHITE;paint.isFilterBitmap=true
        c.save();if(team==1) c.scale(-1f,1f)
        c.drawBitmap(heroes,Rect(col*cw,row*ch,(col+1)*cw,(row+1)*ch),RectF(-26f,-72f,26f,20f),paint);c.restore()
        rect(c,-16f,19f,32f,3f,color,2f)
        if(active) {paint.color=color;paint.style=Paint.Style.STROKE;paint.strokeWidth=1.3f;c.drawOval(-24f,17f,24f,28f,paint);paint.style=Paint.Style.FILL}
        c.restore()
    }
    private fun terrainImage(g:Game):Bitmap {
        if(terrainBitmap==null || terrainRevision!=g.terrain.revision) {
            val fresh=terrainBitmap==null || terrainRevision<0
            val left=if(fresh) 0 else g.terrain.dirtyLeft;val top=if(fresh) 0 else g.terrain.dirtyTop
            val right=if(fresh) WORLD_W-1 else g.terrain.dirtyRight;val bottom=if(fresh) WORLD_H-1 else g.terrain.dirtyBottom
            val patchW=right-left+1;val patchH=bottom-top+1
            if(patchW<=0 || patchH<=0) {terrainRevision=g.terrain.revision;return terrainBitmap!!}
            val pixels=IntArray(patchW*patchH)
            val ground=when(g.arena) {Arena.NEON->Color.rgb(48,63,73);Arena.VOLCANO->Color.rgb(69,45,43);Arena.ORBIT->Color.rgb(51,53,78);Arena.CHEMICAL->Color.rgb(49,66,48);Arena.ARCTIC->Color.rgb(96,132,159);Arena.SCRAP->Color.rgb(79,64,52)}
            val edge=when(g.arena) {Arena.VOLCANO->coral;Arena.ARCTIC->white;Arena.CHEMICAL->lime;else->cyan}
            for(y in top..bottom) for(x in left..right) {
                val i=y*WORLD_W+x
                if(g.terrain.solid[i]) pixels[(y-top)*patchW+x-left]=when {
                    y<3 || !g.terrain.solid[(y-3)*WORLD_W+x] -> edge
                    (x*7+y*13)%113<3 -> Color.rgb(91,97,98)
                    y%48<2 && x%125<120 -> Color.rgb(30,38,44)
                    else -> ground
                }
            }
            val bitmap=terrainBitmap ?: Bitmap.createBitmap(WORLD_W,WORLD_H,Bitmap.Config.ARGB_8888).also {terrainBitmap=it}
            bitmap.setPixels(pixels,0,patchW,left,top,patchW,patchH)
            terrainRevision=g.terrain.revision
            g.terrain.cleanDirty()
        }
        return terrainBitmap!!
    }
    private fun drawBattle(c:Canvas) {
        val g=game ?: return
        background(c,g.arena)
        if(cameraTurn!=g.turn) {cameraTurn=g.turn;cameraManual=false}
        if(overview) {zoom=.42f;cameraX=WORLD_W/2f;cameraY=WORLD_H/2f}
        else if(!cameraManual) {
            val shot=g.shots.firstOrNull()
            val tx=shot?.x ?: g.active.x;val ty=(shot?.y ?: g.active.y)-110
            val halfW=720/zoom;val halfH=306/zoom
            cameraX+=(tx.coerceIn(halfW,WORLD_W-halfW)-cameraX)*.15f
            cameraY+=(ty.coerceIn(halfH,WORLD_H-halfH)-cameraY)*.15f
        }
        c.save();c.clipRect(0f,105f,1440f,718f)
        c.translate(720f,411f);c.scale(zoom,zoom);c.translate(-cameraX,-cameraY)
        if(g.shake>0 && !paused) c.translate(sin(ambient*100)*g.shake,cos(ambient*90)*g.shake*.5f)
        paint.color=Color.WHITE;c.drawBitmap(terrainImage(g),0f,0f,paint)
        val hazard=when(g.arena) {Arena.VOLCANO->Color.rgb(163,61,43);Arena.ORBIT->Color.rgb(65,44,105);Arena.CHEMICAL->Color.rgb(64,110,40);else->Color.rgb(29,69,85)}
        val waterPath=Path();waterPath.moveTo(0f,g.waterLevel)
        for(x in 0..WORLD_W step 12) waterPath.lineTo(x.toFloat(),g.waterLevel+sin(x*.022f+ambient*2)*3)
        waterPath.lineTo(WORLD_W.toFloat(),WORLD_H.toFloat());waterPath.lineTo(0f,WORLD_H.toFloat());waterPath.close();paint.color=hazard;c.drawPath(waterPath,paint)
        line(c,0f,g.waterLevel,WORLD_W.toFloat(),g.waterLevel,if(g.arena==Arena.VOLCANO) coral else cyan,2f)
        for(h in g.hazards) {
            val color=when(h.effect) {Effect.POISON->Color.argb(80,155,214,66);Effect.BURN->Color.argb(100,255,120,40);Effect.FREEZE->Color.argb(100,90,210,250);Effect.CORROSION->Color.argb(90,200,90,245);else->Color.argb(80,150,80,240)}
            circle(c,h.x,h.y,h.radius,color)
            repeat(7) {i->circle(c,h.x+sin(ambient+i)*h.radius*.65f,h.y+cos(ambient*.6f+i)*h.radius*.5f,15f,color)}
        }
        for(d in g.mineWarnings()) {
            paint.color=Color.argb(35,255,130,113);c.drawCircle(d.x,d.y,Weapon.MINE.radius,paint)
            paint.color=coral;paint.style=Paint.Style.STROKE;paint.strokeWidth=2f
            c.drawCircle(d.x,d.y,Weapon.MINE.radius,paint);paint.style=Paint.Style.FILL
            text(c,"ОПАСНО: МИНА",d.x-42,d.y-Weapon.MINE.radius-10,11f,coral,mono)
        }
        for(d in g.devices) {
            if(d.kind==DeviceKind.DOME) {paint.color=cyan;paint.style=Paint.Style.STROKE;paint.strokeWidth=3f;c.drawCircle(d.x,d.y,135f,paint);paint.style=Paint.Style.FILL}
            else {rect(c,d.x-18,d.y-27,36f,40f,when(d.kind) {DeviceKind.BARREL->Color.rgb(110,145,54);DeviceKind.GENERATOR->cyan;DeviceKind.GEYSER->coral;else->muted},5f)
                text(c,when(d.kind) {DeviceKind.MINE->"!";DeviceKind.SPIDER->"Ж";DeviceKind.TURRET->"T";DeviceKind.GENERATOR->"⚡";DeviceKind.MAGNET->"U";else->"×"},d.x-8,d.y,20f,bg,mono)
            }
        }
        if(g.mode==GameMode.ROPE_RACE) for((i,p) in g.raceTargets.withIndex()) {
            paint.color=if(i<g.checkpoints) muted else lime;paint.style=Paint.Style.STROKE;paint.strokeWidth=4f;c.drawCircle(p.first,p.second,65f,paint);paint.style=Paint.Style.FILL;text(c,"${i+1}",p.first-12,p.second+10,30f,lime,mono)
        }
        if(g.phase==Phase.AIM && g.humanTurn && !g.weapon.targeted && g.active.effects[Effect.BLIND.ordinal]==0) {
            g.trajectory().forEachIndexed {i,p -> circle(c,p.first,p.second,if(i==0) 3f else 2f,Color.argb(190-i*7,177,241,112))}
            val a=g.angle*PI.toFloat()/180;val f=g.active
            line(c,f.x+cos(a)*16,f.y-5+sin(a)*16,f.x+cos(a)*44,f.y-5+sin(a)*44,lime,5f)
            circle(c,f.x+cos(a)*65,f.y-5+sin(a)*65,4f,white)
        }
        g.anchor?.let {a ->
            val points=listOf(a)+g.ropeNodes
            points.zipWithNext().forEach {(p,q)->line(c,p.x,p.y,q.x,q.y,white,2f)}
            val tip=points.last();line(c,g.active.x,g.active.y-20,tip.x,tip.y,white,2f);circle(c,a.x,a.y,6f,lime)
        }
        for(f in g.fighters.filter {it.alive || it.reviveIn>0}) {
            if(!f.alive) {rect(c,f.x-22,f.y-47,44f,63f,Color.rgb(102,111,124),10f);text(c,"+",f.x-11,f.y-12,35f,white,mono);text(c,"ВОЗРОЖДЕНИЕ",f.x-45,f.y-64,11f,lime);continue}
            val active=f.id==g.active.id && g.phase!=Phase.ENDED
            val bossScale=if(g.mode==GameMode.BOSS && f.id==2) 2.7f else 1f
            if(bossScale>1) {
                val cell=bossArt.width/4;paint.color=Color.WHITE;paint.isFilterBitmap=true
                c.drawBitmap(bossArt,Rect(g.boss.ordinal*cell,0,(g.boss.ordinal+1)*cell,bossArt.height),RectF(f.x-105,f.y-230,f.x+105,f.y+18),paint)
            } else fighter(c,f.x,f.y-3,f.team,f.race,active,bossScale)
            val crowded=g.fighters.any {it.alive && it.id<f.id && abs(it.x-f.x)<72 && abs(it.y-f.y)<60}
            val labelY=f.y-(if(crowded) 35f else 0f)-(bossScale-1)*80
            text(c,f.name,f.x-30,labelY-95,12f,if(f.team==0) lime else coral,mono)
            rect(c,f.x-27,labelY-87,54f,5f,bg,2f);rect(c,f.x-27,labelY-87,54*f.hp/f.maxHp,5f,if(f.team==0) lime else coral,2f)
            text(c,"${ceil(f.hp).toInt()}",f.x-10,labelY-73,11f,white,mono)
            if(f.effects.any {it>0}) text(c,Effect.entries.filter {f.effects[it.ordinal]>0}.joinToString(" ") {when(it) {Effect.BURN->"ОГОНЬ";Effect.POISON->"ЯД";Effect.FREEZE->"ЛЁД";Effect.STUN->"СТАН";else->"!"}},f.x-25,labelY-112,10f,coral)
            if(f.loadout.hat!=Hat.NONE) rect(c,f.x-10,f.y-72,20f,7f,if(f.loadout.hat==Hat.CROWN) lime else cyan,2f)
            val bossShield=g.mode==GameMode.BOSS && g.boss==Boss.GENERAL && f.id==2 && g.devices.any {it.kind==DeviceKind.GENERATOR && it.hp>0}
            if(f.shield>0 || bossShield) {paint.color=cyan;paint.style=Paint.Style.STROKE;paint.strokeWidth=2f;c.drawOval(f.x-if(bossShield) 115f else 39f,f.y-if(bossShield) 245f else 84f,f.x+if(bossShield) 115f else 39f,f.y+28,paint);paint.style=Paint.Style.FILL}
            if(f.parachute && f.vy>60) {paint.color=cyan;paint.style=Paint.Style.STROKE;paint.strokeWidth=2f;c.drawArc(f.x-50,f.y-135,f.x+50,f.y-80,180f,180f,false,paint);paint.style=Paint.Style.FILL;line(c,f.x-50,f.y-107,f.x,f.y-25,white,1f);line(c,f.x+50,f.y-107,f.x,f.y-25,white,1f)}
            if(f.drillFuel>0) {val r=g.angle*PI.toFloat()/180;val dx=cos(r);val dy=sin(r);line(c,f.x,f.y-22,f.x+dx*48,f.y-22+dy*48,muted,11f);circle(c,f.x+dx*48,f.y-22+dy*48,10f,cyan)}
            if(active) {val y=labelY-77+sin(ambient*4)*3;val p=Path();p.moveTo(f.x-6,y);p.lineTo(f.x+6,y);p.lineTo(f.x,y+7);p.close();paint.color=lime;c.drawPath(p,paint)}
        }
        for(s in g.shots) {
            circle(c,s.x,s.y,if(s.weapon==Weapon.ROCKET) 5f else 7f,if(s.weapon==Weapon.DYNAMITE) coral else lime)
            if(s.weapon==Weapon.ROCKET) line(c,s.x,s.y,s.x-s.vx*.035f,s.y-s.vy*.035f,coral,3f)
            else text(c,"${ceil(s.fuse.coerceAtLeast(0f)).toInt()}",s.x-4,s.y-15,13f,white,mono)
        }
        for(p in g.particles) circle(c,p.x,p.y,if(p.kind==0) 3f else 2f,if(p.kind==2) cyan else if(p.life>.5f) lime else coral)
        g.texts.forEach {text(c,it.text,it.x-14,it.y,22f,white,mono)}
        if(g.beamLife>0) g.lastBeam?.let {line(c,it[0],it[1],it[2],it[3],cyan,4f)}
        g.beams.forEach {line(c,it.x,it.y,it.x2,it.y2,if(g.weapon==Weapon.LASER) cyan else lime,3f)}
        c.restore()
        // Battle HUD.
        rect(c,0f,0f,1440f,105f,Color.rgb(13,23,37))
        text(c,"R /",28f,41f,28f,lime,titleFont)
        text(c,g.arena.title.uppercase(),91f,30f,13f,muted,mono)
        text(c,"ХОД ${g.turn.toString().padStart(2,'0')}  /  ${g.active.name}",91f,61f,23f,white,titleFont)
        text(c,"${g.active.race.title}  •  ${if(g.active.team==0) "Искра" else "Фантом"}",91f,86f,12f,muted)
        val left=g.fighters.filter {it.team==0}.sumOf {ceil(it.hp.toDouble()).toInt()}
        val right=g.fighters.filter {it.team==1}.sumOf {ceil(it.hp.toDouble()).toInt()}
        text(c,"ИСКРА",495f,29f,12f,lime,mono);text(c,"$left",495f,58f,23f,lime,titleFont)
        val maxLeft=g.fighters.filter {it.team==0}.sumOf {it.maxHp.toDouble()}.toFloat()
        val maxRight=g.fighters.filter {it.team==1}.sumOf {it.maxHp.toDouble()}.toFloat()
        rect(c,495f,70f,150f,5f,panel,2f);rect(c,495f,70f,150*left/maxLeft,5f,lime,2f)
        text(c,"ФАНТОМ",681f,29f,12f,coral,mono);text(c,"$right",681f,58f,23f,coral,titleFont)
        rect(c,681f,70f,150f,5f,panel,2f);rect(c,681f,70f,150*right/maxRight,5f,coral,2f)
        text(c,"ВЕТЕР",888f,29f,11f,muted,mono)
        text(c,"${if(g.wind>=0) "→" else "←"} ${abs(g.wind).toInt()}",888f,62f,25f,cyan,mono)
        text(c,if(g.retreatLeft>0) "ОТХОД" else if(g.phase==Phase.FLIGHT) "ПОЛЁТ" else if(g.phase==Phase.RETREAT) "ОЖИДАНИЕ" else "СЕКУНД",1010f,29f,11f,muted,mono)
        val timer=if(g.phase==Phase.AIM) ceil(g.timeLeft.coerceAtLeast(0f)).toInt().toString().padStart(2,'0') else if(g.retreatLeft>0) ceil(g.retreatLeft).toInt().toString().padStart(2,'0') else "··"
        text(c,timer,1010f,67f,34f,if(g.timeLeft<6) coral else white,mono)
        button(c,"?",1158f,25f,58f,50f) {help=true;clearInput()}
        button(c,"II  Пауза",1230f,25f,182f,50f) {paused=true;clearInput()}
        rect(c,24f,119f,if(g.message.length>70) 1150f else 920f,35f,Color.argb(200,13,23,37),9f)
        text(c,g.message,38f,142f,15f,if(g.humanTurn) white else coral)
        button(c,if(overview) "К бойцу" else "Обзор",24f,163f,110f,40f) {overview=!overview;if(!overview) {zoom=.9f;cameraManual=false}}
        button(c,"−",145f,163f,50f,40f) {overview=false;zoom=(zoom-.15f).coerceAtLeast(.5f)}
        button(c,"+",205f,163f,50f,40f) {overview=false;zoom=(zoom+.15f).coerceAtMost(1.25f)}
        button(c,if(cameraManual) "Камера: ручная" else "Следить",266f,163f,180f,40f) {cameraManual=!cameraManual}
        button(c,"Сменить бойца ↔",458f,163f,196f,40f) {val next=g.fighters.firstOrNull {it.alive && it.team==g.active.team && it.id!=g.active.id};if(next!=null && g.switchFighter(next.id)) {cameraManual=false;clearInput()}}
        if(g.mode==GameMode.BOSS) {rect(c,670f,163f,746f,40f,panel,8f);text(c,g.bossWarning.take(85),683f,188f,12f,coral)}
        if(g.mode==GameMode.ROPE_RACE) text(c,"ТОЧКИ ${g.checkpoints}/5  •  ${g.raceElapsed.toInt()} с",1040f,188f,17f,lime)
        if(g.active.fuel>0) text(c,"ТОПЛИВО ${g.active.fuel.toInt()}",30f,230f,14f,cyan)
        if(g.active.drillFuel>0) text(c,"БУР ${g.active.drillFuel.toInt()}% / прицел + движение",30f,252f,14f,lime)
        paint.color=Color.WHITE;paint.isFilterBitmap=true;c.drawBitmap(terrainImage(g),null,RectF(1100f,610f,1416f,705f),paint)
        g.fighters.filter {it.alive}.forEach {circle(c,1100+it.x/WORLD_W*316,610+it.y/WORLD_H*95,3f,if(it.team==0) lime else coral)}
        drawControls(c,g)
        if(arsenal) drawArsenal(c,g)
        if(paused) drawPause(c)
        if(g.phase==Phase.ENDED) drawResult(c,g)
    }
    private fun drawControls(c:Canvas,g:Game) {
        rect(c,0f,718f,1440f,92f,Color.rgb(13,23,37))
        line(c,0f,718f,1440f,718f,Color.rgb(47,64,76))
        val enabled=g.canControl && !paused
        button(c,"←",22f,737f,66f,55f,if(enabled) panel else bg) {if(g.canControl) g.active.vx= -210f}
        button(c,"→",96f,737f,66f,55f,if(enabled) panel else bg) {if(g.canControl) g.active.vx=210f}
        button(c,"Прыжок",176f,737f,112f,55f) {g.jump()}
        button(c,if(g.anchor==null) "Канат" else "Отцепить",300f,737f,118f,55f,if(g.anchor!=null) Color.rgb(45,70,59) else panel) {g.rope()}
        button(c,"↑",430f,737f,46f,55f) {g.anchor?.let {it.length=(it.length-40).coerceAtLeast(35f)};if(g.active.fuel>0) g.active.vy-=100f}
        button(c,"↓",482f,737f,46f,55f) {g.anchor?.let {it.length=(it.length+40).coerceAtMost(900f)}}
        text(c,"УГОЛ ${g.angle.toInt()}°",554f,744f,11f,muted,mono)
        slider(c,555f,767f,166f,(g.angle+180f)/360f,cyan)
        text(c,"СИЛА ${(g.power*100).toInt()}%",750f,744f,11f,muted,mono)
        slider(c,750f,767f,145f,g.power,lime)
        button(c,g.weapon.title.take(16),925f,737f,160f,55f) {if(g.phase==Phase.AIM && g.humanTurn) {arsenalCategory=g.weapon.category;arsenalPage=0;arsenal=true;clearInput()}}
        button(c,"Навык",1097f,737f,100f,55f,if(g.active.abilityUsed) bg else panel,if(g.active.abilityUsed) muted else cyan) {g.ability()}
        button(c,if(g.weapon.targeted) "ЦЕЛЬ НА КАРТЕ" else "ОГОНЬ  ↗",1210f,737f,206f,55f,if(g.phase==Phase.AIM && g.humanTurn) lime else panel,if(g.phase==Phase.AIM && g.humanTurn) bg else muted) {if(g.humanTurn) g.fire()}
    }
    private fun slider(c:Canvas,x:Float,y:Float,w:Float,value:Float,color:Int) {
        rect(c,x,y,w,5f,panel,3f);rect(c,x,y,w*value.coerceIn(0f,1f),5f,color,3f);circle(c,x+w*value.coerceIn(0f,1f),y+2,8f,color)
    }
    private fun dim(c:Canvas) {rect(c,0f,0f,1440f,810f,Color.argb(210,4,9,17));buttons.clear()}
    private fun drawArsenal(c:Canvas,g:Game) {
        dim(c);rect(c,160f,95f,1120f,620f,panel,24f)
        text(c,"АРСЕНАЛ / ${Weapon.entries.size}",198f,141f,29f,white,titleFont)
        text(c,"Оружие разделено по категориям. Отмечай цели прямо на карте.",198f,168f,16f,muted)
        Category.entries.forEachIndexed {i,cat->
            button(c,cat.title,198f+(i%5)*210,185f+(i/5)*48,198f,39f,if(cat==arsenalCategory) lime else Color.rgb(32,46,63),if(cat==arsenalCategory) bg else white) {arsenalCategory=cat;arsenalPage=0}
        }
        val weapons=Weapon.entries.filter {it.category==arsenalCategory}
        val pageCount=max(1,(weapons.size+5)/6);arsenalPage=arsenalPage.coerceIn(0,pageCount-1)
        weapons.drop(arsenalPage*6).take(6).forEachIndexed {i,w ->
            val x=198f+(i%2)*540;val y=290f+(i/2)*94
            val ammo=g.active.ammo.getValue(w)
            button(c,"${w.title}   ${if(ammo<0) "∞" else "×$ammo"}",x,y,510f,54f,if(g.weapon==w) lime else Color.rgb(31,46,63),if(g.weapon==w) bg else if(ammo==0) muted else white) {g.select(w);arsenal=false;beep()}
            text(c,w.description,x+4,y+77,14f,muted)
        }
        button(c,"←",198f,633f,65f,48f) {arsenalPage=(arsenalPage-1+pageCount)%pageCount}
        text(c,"${arsenalPage+1}/$pageCount",280f,664f,17f,muted,mono)
        button(c,"→",340f,633f,65f,48f) {arsenalPage=(arsenalPage+1)%pageCount}
        button(c,"Таймер: ${g.grenadeTimer.toInt()} с",440f,633f,230f,48f) {g.grenadeTimer=if(g.grenadeTimer>=5) 1f else g.grenadeTimer+1}
        button(c,if(g.hardBounce) "Отскок: жёсткий" else "Отскок: мягкий",685f,633f,265f,48f) {g.hardBounce=!g.hardBounce}
        button(c,"Закрыть",970f,633f,270f,48f) {arsenal=false}
    }
    private fun buy(key:String,ordinal:Int,cost:Int):Boolean {
        if(ordinal==0 || prefs.getInt(key,1) and (1 shl ordinal)!=0) return true
        val scrap=prefs.getInt("scrap",150)
        if(scrap<cost) return false
        prefs.edit().putInt("scrap",scrap-cost).putInt(key,prefs.getInt(key,1) or (1 shl ordinal)).apply();beep();return true
    }
    private fun drawLoadout(c:Canvas) {
        dim(c);rect(c,160f,90f,1120f,630f,panel,24f)
        text(c,"ОТРЯД И КУЗНИЦА",198f,139f,30f,white,titleFont)
        text(c,"МАТЕРИАЛЫ: ${prefs.getInt("scrap",150)}",935f,140f,17f,lime,mono)
        Race.entries.forEachIndexed {i,r->
            val x=198f+i*150
            rect(c,x,168f,140f,160f,if(squad.race==r) Color.rgb(40,63,55) else Color.rgb(25,41,57),10f)
            fighter(c,x+70,274f,0,r,false,1.05f)
            text(c,r.title,x+12,314f,15f,if(squad.race==r) lime else white)
            buttons+=Button(RectF(x,168f,x+140,328f),{squad=squad.copy(race=r)},r.title)
        }
        text(c,"ШЛЕМ / 30 материалов за открытие",198f,361f,15f,muted)
        Hat.entries.forEachIndexed {i,h->button(c,h.title,198f+i*262,377f,248f,43f,if(squad.hat==h) lime else bg,if(squad.hat==h) bg else white) {if(buy("ownedHats",i,30)) squad=squad.copy(hat=h)}}
        text(c,"АРТЕФАКТ / 40 материалов",198f,451f,15f,muted)
        Artifact.entries.forEachIndexed {i,a->button(c,a.title,198f+i*262,467f,248f,43f,if(squad.artifact==a) lime else bg,if(squad.artifact==a) bg else white) {if(buy("ownedArtifacts",i,40)) squad=squad.copy(artifact=a)}}
        text(c,"МОДИФИКАЦИЯ БАЗУКИ / 60 материалов",198f,541f,15f,muted)
        Upgrade.entries.forEachIndexed {i,u->button(c,u.title,198f+i*350,557f,332f,43f,if(squad.upgrade==u) lime else bg,if(squad.upgrade==u) bg else white) {if(buy("ownedUpgrades",i,60)) squad=squad.copy(upgrade=u)}}
        text(c,"Победа: +60 материалов • босс: +180. Открытая экипировка остаётся навсегда.",198f,639f,15f,muted)
        button(c,"Сохранить отряд",198f,660f,1048f,42f,lime,bg) {
            prefs.edit().putInt("race",squad.race.ordinal).putInt("hat",squad.hat.ordinal).putInt("artifact",squad.artifact.ordinal).putInt("upgrade",squad.upgrade.ordinal).apply();loadoutOpen=false
        }
    }
    private fun drawPause(c:Canvas) {
        dim(c);rect(c,475f,205f,490f,462f,panel,24f)
        text(c,"ПАУЗА",520f,266f,37f,white,titleFont)
        text(c,"Разлом подождёт.",520f,305f,19f,muted)
        button(c,"Продолжить бой",520f,339f,400f,58f,lime,bg) {paused=false;lastNanos=0}
        button(c,"Пропустить ход",520f,413f,192f,54f) {game?.skip();paused=false}
        button(c,"Правила",728f,413f,192f,54f) {help=true}
        button(c,"Сохранить и в меню",520f,486f,400f,54f) {checkpoint();mode="menu";paused=false;clearInput()}
        button(c,if(musicEnabled) "Музыка: вкл" else "Музыка: выкл",520f,553f,192f,44f) {toggleMusic()}
        button(c,if(sound) "Звук: вкл" else "Звук: выкл",728f,553f,192f,44f) {toggleSound()}
        text(c,"Бой можно продолжить из главного меню.",520f,635f,15f,muted)
    }
    private fun drawResult(c:Canvas,g:Game) {
        dim(c);rect(c,403f,194f,634f,433f,panel,24f)
        text(c,"БОЙ ЗАВЕРШЁН",449f,246f,14f,lime,mono)
        text(c,when(g.winner) {0->"ИСКРА ПОБЕЖДАЕТ";1->"ФАНТОМ ПОБЕЖДАЕТ";else->"НИЧЬЯ"},449f,305f,34f,white,titleFont)
        text(c,"${g.turn} ходов  •  ${g.arena.title}",449f,348f,19f,muted)
        val survivors=g.fighters.count {it.alive}
        text(c,if(g.winner==0) "Победа! +${if(g.mode==GameMode.BOSS) 180 else 60} материалов. Выживших: $survivors" else "Результат сохранён. Попробуй другую тактику.",449f,388f,18f,muted)
        button(c,"Реванш  →",449f,433f,541f,62f,lime,bg) {gameMode=g.mode;selectedBoss=g.boss;squad=g.squad;start()}
        button(c,"Выбрать арену",449f,513f,541f,55f) {mode="menu";clearInput()}
    }
    private fun drawHelp(c:Canvas) {
        dim(c);rect(c,246f,116f,948f,578f,panel,24f)
        text(c,"ПОЛЕВОЕ РУКОВОДСТВО",288f,174f,32f,white,titleFont)
        val entries=listOf(
            "01  ПРИЦЕЛ" to "Коснись поля для направления. Ползунки внизу меняют угол и силу.",
            "02  ВЫСТРЕЛ" to "Открой арсенал, выбери оружие и нажми «Огонь». Учитывай ветер.",
            "03  ПОЗИЦИЯ" to "Удерживай ← → для ходьбы. Прыжок помогает преодолевать кратеры.",
            "04  КАНАТ" to "Направь прицел в скалу или платформу и нажми «Канат». ↑↓ — длина.",
            "05  ВЫЖИВАНИЕ" to "После выстрела есть 3 с на отход. Падение за линию опасности смертельно.",
            "06  ПОБЕДА" to "Уничтожь обоих врагов. У каждой расы свой навык — один раз за бой."
        )
        entries.forEachIndexed {i,e ->val y=222f+i*60;text(c,e.first,288f,y,13f,lime,mono);text(c,e.second,288f,y+25,17f,white)}
        text(c,"Вдвоём: передавайте устройство, когда сменится отряд.",288f,601f,16f,muted)
        button(c,"Понятно. В бой!",288f,625f,864f,46f,lime,bg) {help=false;lastNanos=0}
    }
    override fun onTouchEvent(event:MotionEvent):Boolean {
        val index=event.actionIndex
        fun x(i:Int)=(event.getX(i)-ox)/scale
        fun y(i:Int)=(event.getY(i)-oy)/scale
        val px=x(index);val py=y(index);val id=event.getPointerId(index)
        val g=game
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN,MotionEvent.ACTION_POINTER_DOWN -> {
                if(event.pointerCount==2 && mode=="battle" && !paused && !help && !arsenal && y(0) in 210f..600f && y(1) in 210f..600f) {
                    clearInput();pinchDistance=hypot(x(0)-x(1),y(0)-y(1));overview=false;cameraManual=true;return true
                }
                if(py<215f || paused || help || arsenal || loadoutOpen || mode=="menu" || g?.phase==Phase.ENDED) {
                    val hit=buttons.lastOrNull {it.rect.contains(px,py)}
                    if(hit!=null) {hit.action();invalidate();return true}
                }
                if(mode=="battle" && !paused && !help && !arsenal && !loadoutOpen && g!=null && py in 215f..705f && px in 1100f..1416f && py>610) {
                    overview=false;cameraManual=true;cameraX=(px-1100)/316*WORLD_W;cameraY=(py-610)/95*WORLD_H;return true
                }
                if(mode=="battle" && !paused && !help && !arsenal && !loadoutOpen && cameraManual && g!=null && !g.weapon.targeted && py in 215f..700f) {
                    cameraPointer=id;panX=px;panY=py;return true
                }
                if(mode=="battle" && !paused && !help && !arsenal && !loadoutOpen && g?.canControl==true) {
                    when {
                        py in 730f..805f && px in 22f..162f -> {movePointer=id;g.move=if(px<90) -1f else 1f;return true}
                        py in 730f..805f && px in 430f..528f -> {reelPointer=id;g.reel=if(px<479) -1f else 1f;return true}
                        py in 730f..805f && px in 544f..730f -> {dragPointer=id;dragControl="angle";g.angle=((px-555)/166*360-180).coerceIn(-180f,180f);return true}
                        py in 730f..805f && px in 739f..908f -> {dragPointer=id;dragControl="power";g.power=((px-750)/145).coerceIn(0f,1f);return true}
                        py in 215f..700f && g.phase==Phase.AIM -> {
                            val world=worldPoint(px,py)
                            if(g.weapon.targeted) g.fire(world.first,world.second)
                            else {dragPointer=id;dragControl="aim";aim(g,world.first,world.second)}
                            return true
                        }
                    }
                }
                buttons.lastOrNull {it.rect.contains(px,py)}?.action?.invoke()
            }
            MotionEvent.ACTION_MOVE -> {
                if(pinchDistance>0 && event.pointerCount==2) {
                    val distance=hypot(x(0)-x(1),y(0)-y(1))
                    zoom=(zoom*distance/pinchDistance.coerceAtLeast(1f)).coerceIn(.42f,1.25f);pinchDistance=distance;return true
                }
                for(i in 0 until event.pointerCount) if(event.getPointerId(i)==cameraPointer) {
                    cameraX=(cameraX-(x(i)-panX)/zoom).coerceIn(0f,WORLD_W.toFloat());cameraY=(cameraY-(y(i)-panY)/zoom).coerceIn(0f,WORLD_H.toFloat());panX=x(i);panY=y(i);return true
                }
                if(g!=null && g.canControl && !paused && !help && !arsenal) for(i in 0 until event.pointerCount) if(event.getPointerId(i)==dragPointer) {
                    when(dragControl) {
                        "angle"->g.angle=((x(i)-555)/166*360-180).coerceIn(-180f,180f)
                        "power"->g.power=((x(i)-750)/145).coerceIn(0f,1f)
                        "aim"->{val world=worldPoint(x(i),y(i));aim(g,world.first,world.second)}
                    }
                }
            }
            MotionEvent.ACTION_UP,MotionEvent.ACTION_POINTER_UP -> {
                if(id==movePointer) {movePointer=-1;g?.move=0f}
                if(id==reelPointer) {reelPointer=-1;g?.reel=0f}
                if(id==dragPointer) dragPointer=-1
                if(id==cameraPointer) cameraPointer= -1
                pinchDistance=0f
                performClick()
            }
            MotionEvent.ACTION_CANCEL ->clearInput()
        }
        invalidate();return true
    }
    private fun worldPoint(x:Float,y:Float)=(cameraX+(x-720)/zoom) to (cameraY+(y-411)/zoom)
    private fun aim(g:Game,x:Float,y:Float) {g.angle=(atan2(y-(g.active.y-20),x-g.active.x)*180/PI).toFloat()}
    override fun performClick():Boolean {super.performClick();return true}
}
