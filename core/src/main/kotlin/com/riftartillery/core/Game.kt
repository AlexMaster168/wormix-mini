package com.riftartillery.core

import kotlin.math.*
import kotlin.random.Random
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

const val WORLD_W = 2880
const val WORLD_H = 1440
const val WATER_Y = 1320f
const val STEP = 1f / 60f
const val TURN_SECONDS = 59f

enum class Phase { AIM, FLIGHT, RETREAT, ENDED }
data class Fighter(val id: Int, val team: Int, var name: String, var race: Race, var x: Float, var y: Float) {
    var hp = race.hp.toFloat()
    var vx = 0f
    var vy = 0f
    var grounded = false
    var shield = 0f
    var abilityUsed = false
    var loadout=Loadout(race)
    var maxHp=race.hp.toFloat()
    val effects=IntArray(Effect.entries.size)
    var fuel=0f
    var drillFuel=0f
    var parachute=false
    var aimAssist=false
    var boots=false
    var damageBoost=1f
    var resurrected=false
    var reviveIn=0
    var lavaBounce=false
    val ammo = Weapon.entries.associateWith { it.stock }.toMutableMap()
    val alive get() = hp > 0f
}
data class Shot(var x: Float, var y: Float, var vx: Float, var vy: Float, val weapon: Weapon,
                val owner: Int, var fuse: Float, val fragment: Boolean = false) {
    var age = 0f
    var bounces=0
    var stuck=false
    var attachedTo= -1
    var flying=false
    var targetX=x+vx
    var targetY=y+vy
}
data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val kind: Int)
data class DamageText(val x: Float, var y: Float, val text: String, var life: Float = 1.5f)
data class Anchor(val x: Float, val y: Float, var length: Float)

/** Collision mask is the single authority for both rendering and physics. */
class Terrain(val arena: Arena, seed: Int) {
    val solid = BooleanArray(WORLD_W * WORLD_H)
    val armor=ByteArray(WORLD_W*WORLD_H)
    var revision = 0
        private set
    var dirtyLeft=0;var dirtyTop=0;var dirtyRight=WORLD_W-1;var dirtyBottom=WORLD_H-1
        private set
    init {
        val rng = Random(seed)
        val phase = rng.nextFloat() * 2f
        for (x in 0 until WORLD_W) {
            val top = when(arena) {
                Arena.CANYON -> (1040+sin(x*.0026+phase)*22+sin(x*.011)*12).toInt()
                Arena.HARBOR -> (1060+sin(x*.002+phase)*12).toInt()
                else -> (1040 + sin(x * .003 + phase) * 42 + sin(x * .008 + phase) * 16).toInt()
            }
            val gap = x in 1410..1470 || (arena == Arena.ORBIT && (x in 980..1040 || x in 2180..2240))
            val bottom=if(arena==Arena.ORBIT) min(WORLD_H,top+170+(sin(x*.01)*45).toInt()) else WORLD_H
            if (!gap) for (y in top until bottom) solid[y * WORLD_W + x] = true
        }
        // Floating shelves provide elevated firing positions and rope anchors.
        val shelves=when(arena) {
            Arena.CANYON -> listOf(Triple(440,790,670),Triple(1030,1390,530),Triple(1580,1890,620),Triple(2110,2480,570))
            Arena.HARBOR -> listOf(Triple(480,860,660),Triple(1100,1370,590),Triple(1590,1940,590),Triple(2100,2460,660))
            else -> listOf(Triple(480,820,610),Triple(1070,1350,480),Triple(1560,1840,550),Triple(2070,2440,650))
        }
        for ((left,right,top) in shelves) {
            for(x in left..right) for(y in top..top+38+(sin(x*.02)*10).toInt()) solid[y*WORLD_W+x]=true
        }
        // Connected vaults, bridges and tunnels create routes below the upper battle line.
        if(arena!=Arena.ORBIT) for(cx in listOf(650,1200,1800,2450)) {
            carve(cx.toFloat(),1160f,105f)
            for(x in cx-170..cx+170) for(y in 1148..1252) if(x in 0 until WORLD_W) solid[y*WORLD_W+x]=false
        }
        if(arena!=Arena.ORBIT) for(x in 480..2580) for(y in 1148..1252) solid[y*WORLD_W+x]=false
        if(arena in setOf(Arena.NEON,Arena.SCRAP,Arena.CHEMICAL,Arena.HARBOR)) {
            val left=surface(1360f);val right=surface(1520f)
            addGirder(1440f,(left+right)/2+8,(atan2(right-left,160f)*180/PI).toFloat(),180f)
        }
        revision=0
        dirtyLeft=0;dirtyTop=0;dirtyRight=WORLD_W-1;dirtyBottom=WORLD_H-1
    }
    fun at(x: Float, y: Float): Boolean {
        val ix = floor(x).toInt(); val iy = floor(y).toInt()
        return ix in 0 until WORLD_W && iy in 0 until WORLD_H && solid[iy * WORLD_W + ix]
    }
    fun carve(x: Float, y: Float, radius: Float) {
        for (iy in max(0, (y-radius).toInt())..min(WORLD_H-1, (y+radius).toInt())) {
            for (ix in max(0, (x-radius).toInt())..min(WORLD_W-1, (x+radius).toInt())) {
                if ((ix-x).pow(2) + (iy-y).pow(2) <= radius * radius) {
                    val i=iy*WORLD_W+ix
                    if(armor[i]>1) armor[i]=(armor[i]-1).toByte() else {solid[i]=false;armor[i]=0}
                }
            }
        }
        revision++
        dirtyLeft=min(dirtyLeft,(x-radius-4).toInt().coerceAtLeast(0));dirtyTop=min(dirtyTop,(y-radius-4).toInt().coerceAtLeast(0))
        dirtyRight=max(dirtyRight,(x+radius+4).toInt().coerceAtMost(WORLD_W-1));dirtyBottom=max(dirtyBottom,(y+radius+4).toInt().coerceAtMost(WORLD_H-1))
    }
    fun cleanDirty() {dirtyLeft=WORLD_W-1;dirtyTop=WORLD_H-1;dirtyRight=0;dirtyBottom=0}
    fun addGirder(x:Float,y:Float,angle:Float,length:Float=220f) {
        val r=angle*PI.toFloat()/180
        for(d in -length.toInt()/2..length.toInt()/2) for(t in -9..9) {
            val px=(x+cos(r)*d-sin(r)*t).toInt();val py=(y+sin(r)*d+cos(r)*t).toInt()
            if(px in 0 until WORLD_W && py in 0 until WORLD_H) {solid[py*WORLD_W+px]=true;armor[py*WORLD_W+px]=2}
        }
        revision++;dirtyLeft=0;dirtyTop=0;dirtyRight=WORLD_W-1;dirtyBottom=WORLD_H-1
    }
    fun ray(x: Float, y: Float, dx: Float, dy: Float, distance: Float): Pair<Float, Float>? {
        var d = 0f
        while (d <= distance) {
            val px = x + dx*d; val py = y + dy*d
            if (at(px, py)) return px to py
            d += 2f
        }
        return null
    }
    fun surface(x: Float): Float {
        for (y in 750 until WATER_Y.toInt()) if (at(x, y.toFloat())) return y.toFloat()
        return WATER_Y
    }
}

/** Platform independent fixed-step battle simulation. No Android state or wall clock. */
class Game(val arena: Arena = Arena.NEON, val againstBot: Boolean = true, val seed: Int = 42,
           val mode:GameMode=GameMode.DUEL,val boss:Boss=Boss.INFERNO,val squad:Loadout=Loadout()) {
    private var randomState = if(seed==0) 1 else seed
    private fun random():Float {
        var x=randomState
        x=x xor (x shl 13);x=x xor (x ushr 17);x=x xor (x shl 5)
        randomState=x
        return (x ushr 1)/2147483648f
    }
    val terrain = Terrain(arena, seed)
    val fighters = mutableListOf<Fighter>()
    val shots = mutableListOf<Shot>()
    val particles = mutableListOf<Particle>()
    val texts = mutableListOf<DamageText>()
    val hazards= mutableListOf<Hazard>()
    val devices= mutableListOf<Device>()
    val beams= mutableListOf<Beam>()
    val ropeNodes= mutableListOf<Anchor>()
    var waterLevel=WATER_Y
    var grenadeTimer=3f
    var hardBounce=false
    var target:Pair<Float,Float>?=null
    var grabbedId= -1
    var shotgunShots=0
    var quakeTime=0f
    var checkpoints=0
    var raceElapsed=0f
    val raceTargets=listOf(650f to 540f,1200f to 410f,1710f to 480f,2260f to 580f,2660f to 790f)
    var bossPhase=1
    var bossWarning=""
    var bossActionCount=0
    var switchesThisTurn=0
    var phase = Phase.AIM
        private set
    var activeIndex = 0
        private set
    val active get() = fighters[activeIndex]
    var turn = 1
        private set
    var timeLeft = TURN_SECONDS
        private set
    var wind = 0f
        private set
    var angle = -38f
    var power = .65f
    var weapon = Weapon.ROCKET
    var move = 0f
    var reel = 0f
    var anchor: Anchor? = null
        private set
    var winner: Int? = null
        private set
    var message = "Твой ход. Выбери угол и силу выстрела."
        private set
    var lastBeam: FloatArray? = null
    var beamLife = 0f
    var shake = 0f
    private var phaseTime = 0f
    private var botTime = 0f
    private var nextTeam = 0
    private var jumpBuffer=0f
    private var groundGrace=0f
    private var jumpOwner=0
    private val movementGravity get()=arena.gravity*2.4f
    private val lastByTeam = intArrayOf(-1, -1)
    val humanTurn get() = !againstBot || active.team == 0
    val retreatLeft get() = if(phase==Phase.FLIGHT || phase==Phase.RETREAT) (3f-phaseTime).coerceAtLeast(0f) else 0f
    val canControl get() = active.alive && humanTurn && (phase == Phase.AIM || retreatLeft>0f || shots.any {it.owner==active.id && it.weapon==Weapon.SHEEP && it.flying})
    fun mineWarnings():List<Device> = if(active.loadout.artifact!=Artifact.SAPPER || !humanTurn) emptyList() else devices.filter {
        it.hp>0 && it.kind in setOf(DeviceKind.MINE,DeviceKind.SPIDER) && fighters.getOrNull(it.owner)?.team!=active.team
    }
    init {
        val spawnX = listOf(300f,700f,2040f,2580f)
        spawnX.forEachIndexed { i, x ->
            val race = when(i) { 0 -> squad.race; 1,2 -> Race.CYBORG; else -> Race.MUTANT }
            val ground=minOf(terrain.surface(x-14),terrain.surface(x),terrain.surface(x+14))
            val f=Fighter(i, if(i < 2) 0 else 1, listOf("КОМАНДИР", "ВЕКТОР", "БАСТИОН", "СПОРА")[i], race, x, ground-18)
            if(i==0) {f.loadout=squad;f.maxHp+=squad.hat.hp;f.hp=f.maxHp}
            fighters+=f
        }
        if(mode==GameMode.BOSS) {
            fighters[2].apply {name=boss.title;race=boss.race;maxHp=boss.hp.toFloat();hp=maxHp;x=1980f;y=minOf(terrain.surface(x-14),terrain.surface(x),terrain.surface(x+14))-18}
            fighters[3].apply {name="СТРАЖ";race=boss.race;maxHp=100f;hp=100f}
            fighters.take(2).forEach {it.maxHp+=150f;it.hp=it.maxHp;it.ammo.keys.forEach {w->if(it.ammo[w]!= -1) it.ammo[w]=max(4,it.ammo.getValue(w))}}
            bossWarning=boss.briefing
            if(boss==Boss.GENERAL) {devices+=Device(DeviceKind.GENERATOR,1800f,terrain.surface(1800f)-25,-1,100f);devices+=Device(DeviceKind.GENERATOR,2380f,terrain.surface(2380f)-25,-1,100f)}
        }
        if(mode==GameMode.ROPE_RACE) {
            fighters.drop(1).forEach {it.hp=0f};fighters[0].x=500f;fighters[0].y=560f
            timeLeft=180f;message="Пройди 5 точек на канате. Земля сбрасывает прогресс."
        }
        when(arena) {
            Arena.CHEMICAL->for(x in listOf(880f,1670f,2340f)) devices+=Device(DeviceKind.BARREL,x,terrain.surface(x)-20,-1,25f)
            Arena.NEON->devices+=Device(DeviceKind.BARREL,1800f,terrain.surface(1800f)-20,-1,35f)
            Arena.VOLCANO->devices+=Device(DeviceKind.GEYSER,1650f,terrain.surface(1650f),-1,9999f)
            Arena.SCRAP->devices+=Device(DeviceKind.MAGNET,1440f,310f,-1,40f,0f,0)
            else->Unit
        }
        wind = random()*20-10
        lastByTeam[0] = 0
    }
    fun select(w: Weapon) { if (phase == Phase.AIM && humanTurn) {if(shotgunShots>0 && w!=Weapon.SHOTGUN) {say("Заверши второй выстрел дробовика.");return};weapon = w} }
    fun switchFighter(id:Int,withTool:Boolean=false):Boolean {
        if(phase!=Phase.AIM || !humanTurn || timeLeft<=0 || shotgunShots>0 || mode==GameMode.ROPE_RACE) return false
        val chosen=fighters.getOrNull(id) ?: return false
        if(!chosen.alive || chosen.team!=active.team || chosen.id==active.id || chosen.effects[Effect.STUN.ordinal]>0) return false
        if(switchesThisTurn>=1 && !withTool) {say("Бесплатная смена использована. Выбери инструмент смены персонажа.");return false}
        anchor=null;ropeNodes.clear();move=0f;reel=0f;activeIndex=id;lastByTeam[chosen.team]=id;switchesThisTurn++
        say("Управление передано ${chosen.name}. Время хода сохранено.");return true
    }
    fun jump() {
        if(!canControl) return
        if(jumpOwner!=active.id) {jumpOwner=active.id;groundGrace=0f}
        jumpBuffer=.12f
        takeJump()
    }
    private fun takeJump() {
        if(jumpBuffer<=0 || !canControl || (!active.grounded && groundGrace<=0)) return
        val height=95f*(if(active.boots) 3f else 1f)*(if(active.loadout.artifact==Artifact.GRAVITY) 1.2f else 1f)
        active.vy=-sqrt(2*movementGravity*height)
        val direction=if(abs(move)>.1f) sign(move) else if(abs(active.vx)>10) sign(active.vx) else if(cos(angle*PI/180)>=0) 1f else -1f
        active.vx=direction*180f;active.grounded=false
        jumpBuffer=0f;groundGrace=0f
    }
    fun rope(): Boolean {
        if (!canControl) return false
        if (anchor != null) { anchor = null;ropeNodes.clear(); return true }
        val r = angle * PI.toFloat()/180
        val hit = terrain.ray(active.x, active.y-20, cos(r), sin(r), 900f)
        if (hit == null) { message = "Крюк не нашёл опору. Целься в скалу или платформу."; return false }
        anchor = Anchor(hit.first, hit.second, hypot(active.x-hit.first, active.y-24-hit.second))
        message = "Трос тянет к опоре. ↑ быстрее, ↓ отпустить, «Отцепить» — сброс."
        return true
    }
    fun ability() {
        if (!canControl || phase != Phase.AIM || active.abilityUsed) return
        active.abilityUsed = true
        when(active.race) {
            Race.CYBORG -> { active.shield = 30f; message = "Энергощит: поглотит 30 урона." }
            Race.DRAGON -> { active.vy = -sqrt(2*movementGravity*155f); active.grounded = false; message = "Крылья: усиленный прыжок!" }
            Race.MUTANT -> { active.hp = min(active.maxHp, active.hp+20); message = "Биосинтез: +20 здоровья." }
            Race.DEMON -> if(active.hp>15) {active.hp-=15;active.damageBoost=1.25f;message="Печать крови: следующий выстрел +25%."}
            Race.MERCENARY -> {active.vx=if(cos(angle*PI/180)>0) 330f else -330f;active.shield=50f;message="Тактический перекат: рывок и защита."}
            Race.UNDEAD -> {active.hp=min(active.maxHp,active.hp+25);message="Похищение жизни: восстановлено 25 HP."}
            Race.PHANTOM -> {val ally=fighters.firstOrNull {it.alive && it.team==active.team && it.id!=active.id && hypot(it.x-active.x,it.y-active.y)<600};if(ally!=null) {val x=active.x;val y=active.y;active.x=ally.x;active.y=ally.y;ally.x=x;ally.y=y};message="Астральный сдвиг."}
        }
    }
    internal fun say(s:String) {message=s}
    internal fun roll()=random()
    internal fun hitsFighter(f:Fighter,x:Float,y:Float):Boolean {
        val large=mode==GameMode.BOSS && f.id==2
        return abs(f.x-x)<(if(large) 80f else 17f) && y in f.y-(if(large) 225 else 62)..f.y+17
    }
    fun fire(targetX: Float? = null, targetY: Float? = null): Boolean {
        shots.firstOrNull {it.owner==active.id && it.weapon==Weapon.SHEEP}?.let {if(humanTurn && phase==Phase.FLIGHT) {it.flying=true;return true}}
        if (phase != Phase.AIM || !active.alive) return false
        if(mode==GameMode.ROPE_RACE && weapon!=Weapon.ROPE) {message="В испытании доступен только канат.";return false}
        if ((active.ammo[weapon] ?: 0) == 0 && !(weapon==Weapon.SHOTGUN && shotgunShots==1)) { message = "Боезапас исчерпан. Выбери другое оружие."; return false }
        if(targetX!=null && targetY!=null && (!targetX.isFinite() || !targetY.isFinite() || targetX !in 0f..WORLD_W.toFloat() || targetY !in 0f..WORLD_H.toFloat())) {say("Цель за пределами карты.");return false}
        val a = active
        val r = angle * PI.toFloat()/180f
        val special=fireContent(targetX,targetY)
        if(special==FireResult.FAILED) return false
        if(special==FireResult.FREE || special==FireResult.CONTINUE) {
            if(special==FireResult.FREE && weapon!=Weapon.ROPE && a.ammo.getValue(weapon)>0) a.ammo[weapon]=a.ammo.getValue(weapon)-1
            if(special==FireResult.CONTINUE && weapon==Weapon.SHOTGUN && a.ammo.getValue(weapon)>0) a.ammo[weapon]=a.ammo.getValue(weapon)-1
            return true
        }
        if(special==FireResult.USED) {
            if(weapon!=Weapon.SHOTGUN && a.ammo.getValue(weapon)>0) a.ammo[weapon]=a.ammo.getValue(weapon)-1
            phase=if(shots.isEmpty() && quakeTime<=0) Phase.RETREAT else Phase.FLIGHT
            phaseTime=0f;move=0f;return true
        }
        if (weapon == Weapon.TELEPORT) {
            if (targetX == null || targetY == null || !validPosition(targetX, targetY) || targetY >= WATER_Y-16) {
                message = "Телепорт: коснись свободной точки над землёй."; return false
            }
            anchor = null; a.x = targetX; a.y = targetY; a.vx = 0f; a.vy = 0f
            burst(a.x, a.y, 24, 2)
        } else if (weapon == Weapon.HEAL) {
            if (a.hp >= a.maxHp && a.effects.all {it==0}) { message = "Здоровье уже полное."; return false }
            val gain = min(50f, a.maxHp-a.hp); a.hp += gain;a.effects.fill(0)
            texts += DamageText(a.x, a.y-30, "+${gain.toInt()}")
        } else if (weapon == Weapon.SNIPER) {
            val dx = cos(r); val dy = sin(r)
            var bx = a.x; var by = a.y-5
            for (d in 20..1800 step 2) {
                bx = a.x+dx*d; by = a.y-5+dy*d
                val hit = fighters.firstOrNull { it.alive && it.id != a.id && hypot(it.x-bx, it.y-by)<17 }
                if (hit != null) { hurt(hit, if(by<hit.y-15) 70f else weapon.damage,DamageType.BULLET,a.id); break }
                if (terrain.at(bx, by)) { burst(bx, by, 10, 0); break }
            }
            lastBeam = floatArrayOf(a.x, a.y-5, bx, by); beamLife = .35f
        } else {
            val speed = weapon.speed*(.20f+.80f*power.coerceIn(0f,1f))
            val x = if (weapon == Weapon.DYNAMITE) a.x+if(cos(r)>0) 19 else -19 else a.x+cos(r)*23
            val y = if (weapon == Weapon.DYNAMITE) a.y else a.y-5+sin(r)*23
            val count=if(weapon==Weapon.ROCKET && a.loadout.upgrade==Upgrade.TRIPLE) 3 else 1
            repeat(count) {i->val ar=r+if(count==3) (i-1)*.09f else 0f
                shots += Shot(x,y,cos(ar)*speed,sin(ar)*speed,weapon,a.id,if(weapon==Weapon.GRENADE) grenadeTimer else weapon.fuse)
            }
        }
        if ((a.ammo[weapon] ?: -1) > 0) a.ammo[weapon] = a.ammo.getValue(weapon)-1
        phase = if (shots.isEmpty()) Phase.RETREAT else Phase.FLIGHT
        phaseTime = 0f; move = 0f
        message = if (phase == Phase.FLIGHT) "Выстрел! 3 секунды на отход, пока снаряд в полёте." else "Отход! 3 секунды на укрытие."
        return true
    }
    fun skip() { if (phase == Phase.AIM && humanTurn) { anchor = null; nextTurn() } }
    fun validPosition(x: Float, y: Float): Boolean {
        if (x < 16 || x > WORLD_W-16 || y < 64 || y > waterLevel-18) return false
        for (dx in -14..14 step 7) for (dy in -62..17 step 7) if (terrain.at(x+dx, y+dy)) return false
        return fighters.none { it.alive && it.id != active.id && abs(it.x-x)<34 && abs(it.y-y)<80 }
    }
    internal fun hurt(f: Fighter, raw: Float,type:DamageType=DamageType.EXPLOSION,owner:Int= -1) {
        if(!f.alive) {
            if(f.reviveIn>0) {f.shield=max(0f,f.shield-raw);if(f.shield<=0) {f.reviveIn=0;texts+=DamageText(f.x,f.y-80,"ПЕЧАТЬ РАЗБИТА")}}
            return
        }
        if(mode==GameMode.BOSS && boss==Boss.GENERAL && f.id==2 && devices.any {it.kind==DeviceKind.GENERATOR && it.hp>0}) {texts+=DamageText(f.x,f.y-70,"ЩИТ");return}
        if(type==DamageType.FIRE && (f.race==Race.DEMON || f.loadout.hat==Hat.CROWN)) return
        if(type==DamageType.POISON && (f.race==Race.CYBORG || f.loadout.hat==Hat.MASK)) return
        if(type==DamageType.POISON && f.race==Race.MUTANT) {f.hp=min(f.maxHp,f.hp+10);return}
        val attacker=fighters.firstOrNull {it.id==owner}
        var damage=raw*(1-f.race.resist-f.loadout.hat.armor)*(if(f.effects[Effect.CORROSION.ordinal]>0) 1.2f else 1f)
        damage*=attacker?.damageBoost ?: 1f
        if(attacker?.race==Race.MERCENARY && type==DamageType.BULLET) damage*=1.12f
        if(f.race==Race.CYBORG && type==DamageType.ELECTRIC) damage*=1.2f
        val blocked = min(f.shield, damage); f.shield -= blocked; damage -= blocked
        var shieldSpent=blocked
        for(drone in devices.filter {it.kind==DeviceKind.DEFENDER && it.owner==f.id && it.hp>0}) {val used=min(drone.hp,shieldSpent);drone.hp-=used;shieldSpent-=used}
        if (damage > 0f) {
            val dealt=min(f.hp,damage);f.hp=max(0f,f.hp-damage);if(damage>.5f) texts+=DamageText(f.x,f.y-72,"−${damage.roundToInt()}")
            if(attacker!=null && attacker.team!=f.team && (attacker.race==Race.UNDEAD || attacker.loadout.artifact==Artifact.BLOOD)) attacker.hp=min(attacker.maxHp,attacker.hp+dealt*.2f)
            if(f.hp<=0 && f.race==Race.UNDEAD && !f.resurrected) {f.reviveIn=2;f.resurrected=true;f.shield=30f}
        }
    }
    internal fun burst(x: Float, y: Float, count: Int, kind: Int) {
        repeat(min(count,(650-particles.size).coerceAtLeast(0))) {
            val r = random()*PI.toFloat()*2; val v = 25+random()*150
            particles += Particle(x,y,cos(r)*v,sin(r)*v,.4f+random()*.7f,kind)
        }
    }
    internal fun explode(s: Shot) {
        if(s.weapon==Weapon.GRAVITY && !s.stuck) {hazards+=Hazard(s.x,s.y,230f,Effect.STUN,2,s.owner);s.fuse=2f;s.stuck=true;shots+=s;return}
        if(s.weapon==Weapon.GRAVITY) hazards.removeAll {it.effect==Effect.STUN && hypot(it.x-s.x,it.y-s.y)<5}
        val radius = if(s.fragment) 37f else s.weapon.radius
        val damage = if(s.fragment) 20f else s.weapon.damage
        if(s.weapon !in setOf(Weapon.CRYO,Weapon.GAS,Weapon.FLASH)) terrain.carve(s.x,s.y,if(s.weapon==Weapon.ROCKET && fighters[s.owner].loadout.upgrade==Upgrade.PLASMA) radius*1.4f else radius)
        shake = 7f
        burst(s.x,s.y,36,0)
        for (f in fighters.filter { it.alive || it.reviveIn>0 }) {
            val large=mode==GameMode.BOSS && f.id==2
            val hitX=s.x.coerceIn(f.x-(if(large) 80 else 14),f.x+(if(large) 80 else 14))
            val hitY=s.y.coerceIn(f.y-(if(large) 225 else 62),f.y+17)
            val dist = hypot(hitX-s.x,hitY-s.y)
            if (dist < radius+25) {
                val factor = (1-dist/(radius+25)).coerceIn(0f,1f)
                val amount=if(s.weapon==Weapon.ROCKET && fighters[s.owner].loadout.upgrade==Upgrade.TRIPLE) damage*.6f else damage
                hurt(f,amount*factor,s.weapon.damageType,s.owner)
                applyWeaponEffect(s.weapon,f)
                val knock = (if(f.race == Race.CYBORG) .7f else 1f)*(if(large) .25f else 1f)
                if(s.weapon==Weapon.VORTEX) {
                    val distance=max(10f,hypot(f.x-s.x,f.y-24-s.y))
                    f.vx+=(s.x-f.x)/distance*520*factor*knock
                    f.vy+=(s.y-(f.y-24))/distance*520*factor*knock-70
                } else {
                    f.vx += (f.x-s.x)/max(10f,dist)*210*factor*knock
                    f.vy -= (90+180*factor)*knock
                }
                f.grounded = false
            }
        }
        damageDevices(s.x,s.y,radius,damage)
        if(s.weapon in setOf(Weapon.MOLOTOV,Weapon.NAPALM,Weapon.FLAME,Weapon.GAS,Weapon.SKUNK,Weapon.CRYO,Weapon.ACID)) {
            val effect=when(s.weapon) {Weapon.GAS,Weapon.SKUNK->Effect.POISON;Weapon.CRYO->Effect.FREEZE;Weapon.ACID->Effect.CORROSION;else->Effect.BURN}
            hazards+=Hazard(s.x,s.y,radius+20,effect,if(effect==Effect.POISON) 4 else 3,s.owner)
            if(s.weapon==Weapon.ACID) for(i in 0..8) terrain.carve(s.x+(i-4)*13,s.y+i*15,20f)
        }
        val fragments=when(s.weapon) {Weapon.CLUSTER->5;Weapon.BANANA,Weapon.CLUSTER_ROCKET->6;Weapon.MORTAR->4;else->0}
        if(!s.fragment) repeat(fragments) { i ->
            shots += Shot(s.x,s.y-8,(i-fragments/2)*100f,-190f-random()*130,Weapon.GRENADE,s.owner,1.3f,true)
        }
    }
    fun update(dt: Float = STEP) {
        require(dt > 0f && dt <= .05f)
        beamLife = max(0f,beamLife-dt); shake = max(0f,shake-dt*18)
        particles.forEach { it.life -= dt; it.x += it.vx*dt; it.y += it.vy*dt; it.vy += 160*dt }
        particles.removeAll { it.life <= 0 }
        texts.forEach { it.life -= dt; it.y -= dt*22 }; texts.removeAll { it.life <= 0 }
        beams.forEach {it.life-=dt};beams.removeAll {it.life<=0}
        if (phase == Phase.ENDED) return
        phaseTime += dt
        if (anchor?.let { !terrain.at(it.x,it.y) } == true) anchor = null
        for (f in fighters.filter { it.alive }) updateFighter(f,dt)
        separateFighters()
        updateShots(dt)
        updateContent(dt)
        if(mode==GameMode.ROPE_RACE) {
            raceElapsed+=dt;timeLeft=180f-raceElapsed
            val checkpoint=raceTargets.getOrNull(checkpoints)
            if(checkpoint!=null && hypot(active.x-checkpoint.first,active.y-checkpoint.second)<80 && anchor!=null) {checkpoints++;message="Контрольная точка $checkpoints / 5"}
            if(active.grounded && checkpoints>0) {checkpoints=0;message="Касание земли: контрольные точки сброшены."}
            if(checkpoints==raceTargets.size || !active.alive || timeLeft<=0) {winner=if(checkpoints==raceTargets.size) 0 else 1;phase=Phase.ENDED;anchor=null}
            return
        }
        val teams = fighters.filter { it.alive || it.reviveIn>0 }.map { it.team }.distinct()
        if (teams.size <= 1 && shots.isEmpty()) {
            winner = teams.firstOrNull() ?: -1; phase = Phase.ENDED; anchor = null; move = 0f
            message = when(winner) { 0 -> "Победа отряда «Искра»!"; 1 -> "Победа отряда «Фантом»!"; else -> "Ничья. Разлом забрал всех." }
            return
        }
        when(phase) {
            Phase.AIM -> {
                timeLeft -= dt
                if (timeLeft <= 0 || !active.alive) nextTurn()
                else if (!humanTurn) updateBot(dt)
            }
            Phase.FLIGHT -> if (shots.isEmpty() && quakeTime<=0) {
                phase = Phase.RETREAT
                message = if(retreatLeft>0) "Отход! Укройся до смены хода." else "Ожидание завершения падения…"
            } else if(phaseTime>3f) message="Снаряд в полёте…"
            Phase.RETREAT -> if (phaseTime >= 3f && fighters.none { it.alive && abs(it.vy)>20 }) nextTurn()
            Phase.ENDED -> Unit
        }
        // Never let an unsettled character stall the match indefinitely.
        if (phase == Phase.RETREAT && phaseTime > 6f) nextTurn()
    }
    private fun updateFighter(f: Fighter, dt: Float) {
        // Resolve small terrain penetrations, including saves made with older collider dimensions.
        var correction=0
        while(bodyBlocked(f.x,f.y) && correction<48) {f.y-=1f;correction++}
        val controlled = f.id == active.id && canControl && shots.none {it.weapon==Weapon.SHEEP && it.flying}
        if (controlled) {
            if(jumpOwner!=f.id) {jumpOwner=f.id;groundGrace=0f;jumpBuffer=0f}
            groundGrace=if(f.grounded) .10f else max(0f,groundGrace-dt)
            takeJump();jumpBuffer=max(0f,jumpBuffer-dt)
            val direction=move*(if(f.effects[Effect.BLIND.ordinal]>0) -1 else 1)
            if (anchor != null) f.vx += move*170*dt
            else if (move != 0f) f.vx = direction*145*(if(f.effects[Effect.FREEZE.ordinal]>0) .7f else 1f)
            if(f.fuel>0 && reel<0) {f.vy-=(movementGravity+650)*dt;f.fuel=max(0f,f.fuel-25*dt);burst(f.x,f.y+20,1,0)}
            if(f.drillFuel>0 && move!=0f) {
                val r=angle*PI.toFloat()/180;val sx=cos(r)*move;val sy=sin(r)*move
                terrain.carve(f.x+sx*36,f.y-24+sy*36,45f)
                fighters.filter {it.alive && it.id!=f.id && hypot(it.x-f.x,it.y-f.y)<85}.forEach {hurt(it,12*dt,DamageType.MELEE,f.id)}
                f.vx=sx*110;f.vy=sy*110-movementGravity*dt;f.drillFuel=max(0f,f.drillFuel-12*dt)
                burst(f.x+sx*40,f.y-20+sy*40,1,2)
            }
        }
        f.vy += movementGravity*dt
        if(f.parachute && f.vy>120) f.vy=120f
        val wasGrounded = f.grounded
        val steps = max(1,ceil(abs(f.vy*dt)/3).toInt())
        val dy = f.vy*dt/steps
        f.grounded = false
        repeat(steps) {
            if (!bodyBlocked(f.x,f.y+dy)) f.y += dy
            else {
                if (dy > 0) {
                    f.grounded = true
                    val safeSpeed=sqrt(2*movementGravity*135f)
                    if (f.vy > safeSpeed && f.race != Race.DRAGON) hurt(f,(f.vy-safeSpeed)*.08f*(if(f.race==Race.PHANTOM) .5f else 1f))
                }
                f.vy = 0f
            }
        }
        val dx = f.vx*dt
        if (!bodyBlocked(f.x+dx,f.y)) f.x += dx
        else if ((wasGrounded || f.grounded) && f.vy>=0 && !bodyBlocked(f.x+dx,f.y-7)) { f.x += dx; f.y -= 7 }
        else f.vx = 0f
        f.vx *= if (f.grounded) (if(arena==Arena.ARCTIC || hazards.any {it.effect==Effect.FREEZE && hypot(it.x-f.x,it.y-f.y)<it.radius}) .98f else .82f) else .995f
        f.x = f.x.coerceIn(15f,WORLD_W-15f)
        if (f.id == active.id) anchor?.let { original ->
            updateRopeWrapping()
            val a=ropeNodes.lastOrNull() ?: original
            if(canControl) a.length = (a.length+(if(reel>0) reel*240 else -300+reel*180)*dt).coerceIn(75f,900f)
            val ax = f.x-a.x; val ay = f.y-24-a.y; val dist = hypot(ax,ay)
            if (dist > a.length) {
                val nx = ax/dist; val ny = ay/dist
                val count=max(1,ceil((dist-a.length)/3).toInt())
                val dx= -nx*(dist-a.length)/count;val dy= -ny*(dist-a.length)/count
                var blocked=false
                repeat(count) {
                    if(!bodyBlocked(f.x+dx,f.y+dy)) {f.x+=dx;f.y+=dy;f.grounded=false}
                    else if(!bodyBlocked(f.x+dx,f.y)) {f.x+=dx;blocked=true}
                    else if(!bodyBlocked(f.x,f.y+dy)) {f.y+=dy;f.grounded=false;blocked=true}
                    else blocked=true
                }
                if(blocked) a.length=max(a.length,hypot(f.x-a.x,f.y-24-a.y))
                val radial = f.vx*nx+f.vy*ny
                if (radial>0) { f.vx -= radial*nx; f.vy -= radial*ny }
                if(!blocked && canControl && reel<=0 && dist>95) {
                    val pullSpeed=300-reel*180
                    val extra= -pullSpeed-(f.vx*nx+f.vy*ny)
                    if(extra<0) {f.vx+=extra*nx;f.vy+=extra*ny}
                }
            }
        }
        if (f.y > waterLevel) {
            if(arena==Arena.VOLCANO && f.race==Race.DEMON && !f.lavaBounce) {f.lavaBounce=true;f.y=waterLevel-20;f.vy=-380f;hurt(f,25f)}
            else {f.hp=0f;f.reviveIn=0;burst(f.x,waterLevel,24,2);texts+=DamageText(f.x,waterLevel-30,"В БЕЗДНУ")}
        }
    }
    private fun updateRopeWrapping() {
        val root=anchor ?: return
        while(ropeNodes.isNotEmpty()) {
            val previous=ropeNodes.getOrNull(ropeNodes.lastIndex-1) ?: root
            val distance=hypot(active.x-previous.x,active.y-20-previous.y)
            val hit=terrain.ray(active.x,active.y-20,(previous.x-active.x)/max(1f,distance),(previous.y-active.y+20)/max(1f,distance),(distance-10).coerceAtLeast(0f))
            if(hit==null) ropeNodes.removeAt(ropeNodes.lastIndex) else break
        }
        val tip=ropeNodes.lastOrNull() ?: root
        val distance=hypot(active.x-tip.x,active.y-20-tip.y)
        val hit=terrain.ray(active.x,active.y-20,(tip.x-active.x)/max(1f,distance),(tip.y-active.y+20)/max(1f,distance),(distance-14).coerceAtLeast(0f))
        if(hit!=null && ropeNodes.size<12 && hypot(hit.first-tip.x,hit.second-tip.y)>24) {
            val candidates=listOf(-12f to -12f,12f to -12f,-12f to 12f,12f to 12f)
            val corner=candidates.map {hit.first+it.first to hit.second+it.second}.filter {!terrain.at(it.first,it.second)}.minByOrNull {hypot(it.first-active.x,it.second-active.y)}
            if(corner!=null) ropeNodes+=Anchor(corner.first,corner.second,max(35f,distance-hypot(tip.x-corner.first,tip.y-corner.second)))
        }
    }
    private fun bodyBlocked(x: Float,y: Float): Boolean =
        terrain.at(x-14,y-62) || terrain.at(x+14,y-62) || terrain.at(x-14,y-31) || terrain.at(x+14,y-31) || terrain.at(x-14,y-7) || terrain.at(x+14,y-7) ||
        terrain.at(x-14,y+16) || terrain.at(x+14,y+16) || terrain.at(x,y+17)

    private fun separateFighters() {
        fun displace(f:Fighter,dx:Float,dy:Float):Boolean {
            if(f.x+dx !in 15f..WORLD_W-15f) return false
            var targetY=f.y+dy
            if(bodyBlocked(f.x+dx,targetY)) {
                if(dy!=0f || bodyBlocked(f.x+dx,targetY-7)) return false
                targetY-=7
            }
            f.x+=dx;f.y=targetY;return true
        }
        repeat(3) {
            for(i in fighters.indices) for(j in i+1 until fighters.size) {
                val a=fighters[i];val b=fighters[j]
                if(!a.alive || !b.alive) continue
                if(a.race==Race.PHANTOM && !a.grounded || b.race==Race.PHANTOM && !b.grounded) continue
                val overlapX=34-abs(a.x-b.x);val overlapY=80-abs(a.y-b.y)
                if(overlapX<=0 || overlapY<=0) continue
                val horizontal=overlapX<overlapY
                val direction=if((if(horizontal) a.x-b.x else a.y-b.y)<=0) -1f else 1f
                val shift=(if(horizontal) overlapX else overlapY)+.1f
                val dx=if(horizontal) shift*direction else 0f
                val dy=if(horizontal) 0f else shift*direction
                val movedA=displace(a,dx/2,dy/2)
                val movedB=displace(b,if(movedA) -dx/2 else -dx,if(movedA) -dy/2 else -dy)
                if(!movedB && movedA) displace(a,dx/2,dy/2)
                if(horizontal) {
                    if(a.vx*direction<0) a.vx=0f
                    if(b.vx*direction>0) b.vx=0f
                } else {
                    val upper=if(direction<0) a else b
                    val lower=if(direction<0) b else a
                    if(upper.vy>0) upper.vy=0f
                    if(lower.vy<0) lower.vy=0f
                    upper.grounded=true
                }
            }
        }
    }

    private fun updateShots(dt: Float) {
        val current = shots.toList()
        for(s in current) {
            s.age += dt
            val w=s.weapon
            if(w!=Weapon.HOLY || (abs(s.vx)<12 && abs(s.vy)<12)) s.fuse-=dt
            if(s.attachedTo in fighters.indices && w==Weapon.STICKY) {s.x=fighters[s.attachedTo].x;s.y=fighters[s.attachedTo].y-20}
            if(s.stuck) {if(s.fuse<=0) {shots.remove(s);explode(s)};continue}
            if(w==Weapon.HOMING) {
                val d=hypot(s.targetX-s.x,s.targetY-s.y).coerceAtLeast(1f)
                s.vx+=(s.targetX-s.x)/d*450*dt;s.vy+=(s.targetY-s.y)/d*450*dt
                val speed=hypot(s.vx,s.vy).coerceAtLeast(1f);s.vx=s.vx/speed*620;s.vy=s.vy/speed*620
            } else if(w==Weapon.SHEEP && s.flying) {
                val heading=atan2(s.vy,s.vx)+move*2.4f*dt;s.vx=cos(heading)*410;s.vy=sin(heading)*410
            } else {s.vx+=wind*2.8f*dt;s.vy+=arena.gravity*dt}
            if(w==Weapon.MOLE) {
                val wasSolid=terrain.at(s.x+sign(s.vx)*22,s.y)
                if(wasSolid) {s.flying=true;terrain.carve(s.x,s.y,14f)}
                s.x+=s.vx*dt;s.y+=s.vy*.25f*dt
                if((s.flying && !wasSolid) || s.fuse<=0) {shots.remove(s);explode(s)}
                continue
            }
            if(w==Weapon.SKUNK && (s.age*5).toInt()!=(max(0f,s.age-dt)*5).toInt() && hazards.size<100) hazards+=Hazard(s.x,s.y,50f,Effect.POISON,4,s.owner)
            for(dome in devices.filter {it.kind==DeviceKind.DOME && it.owner in fighters.indices && fighters[it.owner].team!=fighters[s.owner].team}) {
                val distance=hypot(s.x-dome.x,s.y-dome.y)
                if(distance in 125f..145f && (s.x-dome.x)*s.vx+(s.y-dome.y)*s.vy<0) {s.vx*= -1;s.vy*= -1;dome.hp-=10}
            }
            var detonate = false
            val steps = max(1,ceil(hypot(s.vx,s.vy)*dt/3).toInt())
            for(i in 0 until steps) {
                val nx = s.x+s.vx*dt/steps; val ny = s.y+s.vy*dt/steps
                val victim=fighters.firstOrNull {(it.alive || it.reviveIn>0) && (it.id!=s.owner || s.age>.4f) && hitsFighter(it,nx,ny)}
                val contact=terrain.at(nx,ny)
                if(w==Weapon.BOAR && contact) {terrain.carve(nx,ny,25f);s.x=nx;s.y=ny;continue}
                if(w==Weapon.DISC && victim!=null && s.attachedTo!=victim.id) {hurt(victim,25f,DamageType.BULLET,s.owner);s.attachedTo=victim.id}
                if(w==Weapon.BOAR && victim!=null && s.attachedTo!=victim.id) {hurt(victim,25f,DamageType.MELEE,s.owner);victim.vx+=sign(s.vx)*250;victim.vy= -160f;s.attachedTo=victim.id}
                if(contact || victim!=null && w!=Weapon.DISC && w!=Weapon.BOAR) {
                    if(w.kind in setOf(Kind.IMPACT,Kind.GUIDED,Kind.GLOBAL) || w==Weapon.SHEEP && s.flying) {s.x=nx;s.y=ny;detonate=true;break}
                    if(w==Weapon.STICKY) {s.x=nx;s.y=ny;s.vx=0f;s.vy=0f;s.stuck=true;s.attachedTo=victim?.id ?: -1;break}
                    if(w.kind==Kind.WALKER) {s.vy= -145f;s.x=nx;s.y=ny-4;break}
                    s.bounces++
                    if(w==Weapon.DISC && s.bounces>=6) {detonate=true;break}
                    val bounce=if(w==Weapon.BANANA) .9f else if(w==Weapon.DISC) 1f else if(hardBounce) .7f else .45f
                    if (terrain.at(nx,s.y)) s.vx *= -bounce else s.vx *= if(w==Weapon.DISC) 1f else .78f
                    if (terrain.at(s.x,ny)) s.vy *= -bounce else s.vy *= -bounce
                    if (abs(s.vy)<12) s.vy=0f
                    break
                } else { s.x=nx; s.y=ny }
            }
            if (w.kind in setOf(Kind.BOUNCE,Kind.WALKER) && s.fuse<=0) detonate = true
            if (s.age>18 || s.y>waterLevel+20 || s.x< -100 || s.x>WORLD_W+100) { shots.remove(s);continue }
            if (detonate) { shots.remove(s);explode(s) }
        }
    }
    private fun nextTurn() {
        for(e in listOf(Effect.BLIND,Effect.FREEZE)) if(active.effects[e.ordinal]>0) active.effects[e.ordinal]--
        fighters.filter {it.team==active.team}.forEach {it.boots=false;it.fuel=0f;it.drillFuel=0f;it.parachute=false;it.aimAssist=false;it.damageBoost=1f}
        anchor=null;ropeNodes.clear();move=0f;reel=0f;shotgunShots=0;grabbedId= -1;switchesThisTurn=0
        nextTeam=1-active.team
        jumpBuffer=0f;groundGrace=0f
        var candidates=fighters.indices.filter { fighters[it].alive && fighters[it].team==nextTeam }
        if(candidates.isEmpty()) {contentTurn();candidates=fighters.indices.filter {fighters[it].alive && fighters[it].team==nextTeam};if(candidates.isEmpty()) {nextTeam=active.team;candidates=fighters.indices.filter {fighters[it].alive && fighters[it].team==nextTeam}};if(candidates.isEmpty()) return}
        activeIndex=candidates.firstOrNull { it>lastByTeam[nextTeam] } ?: candidates.first()
        lastByTeam[nextTeam]=activeIndex
        turn++;timeLeft=TURN_SECONDS;phase=Phase.AIM;phaseTime=0f;botTime=0f
        contentTurn()
        wind=random()*20-10
        angle=if(active.team==0) -38f else -142f
        power=.65f;weapon=Weapon.ROCKET
        if(active.race==Race.MUTANT) active.hp=min(active.maxHp,active.hp+5)
        for(effect in Effect.entries) if(active.effects[effect.ordinal]>0) {
            when(effect) {Effect.BURN->hurt(active,10f,DamageType.FIRE);Effect.POISON->hurt(active,15f,DamageType.POISON);Effect.BLEED->hurt(active,5f,DamageType.MELEE);else->Unit}
            if(effect !in listOf(Effect.FREEZE,Effect.BLIND)) active.effects[effect.ordinal]--
            if(effect==Effect.STUN) timeLeft=0f
        }
        message="Ход: ${active.name}. ${if(humanTurn) "Прицелься и стреляй." else "Противник выбирает траекторию…"}"
    }
    private fun updateBot(dt:Float) {
        botTime+=dt
        if(botTime<1.6f) return
        if(bossAttack()) return
        val target=fighters.filter { it.alive && it.team!=active.team }.minByOrNull { abs(it.x-active.x) } ?: return
        var best=Float.MAX_VALUE;var bestAngle=-135f;var bestPower=.65f
        val direction=if(target.x>active.x) 1 else -1
        // Search with the same integration and wind as gameplay, including terrain occlusion.
        for(deg in 22..78 step 4) for(p in 3..10) {
            val a=if(direction==1) -deg.toFloat() else -180f+deg
            val r=a*PI.toFloat()/180;val strength=p/10f
            var x=active.x+cos(r)*23;var y=active.y-5+sin(r)*23
            var vx=cos(r)*Weapon.ROCKET.speed*(.2f+.8f*strength)
            var vy=sin(r)*Weapon.ROCKET.speed*(.2f+.8f*strength)
            var closest=Float.MAX_VALUE
            for(t in 0..420) {
                vx+=wind*2.8f*STEP;vy+=arena.gravity*STEP;x+=vx*STEP;y+=vy*STEP
                closest=min(closest,hypot(x-target.x,y-target.y))
                if(terrain.at(x,y) || y>WATER_Y || x<0 || x>WORLD_W) break
            }
            if(closest<best) {best=closest;bestAngle=a;bestPower=strength}
        }
        angle=bestAngle;power=bestPower;weapon=if(best>180 && active.ammo.getValue(Weapon.AIRSTRIKE)>0) Weapon.AIRSTRIKE else Weapon.ROCKET;fire(target.x,target.y)
    }
    fun trajectory(): List<Pair<Float,Float>> {
        if(weapon.kind !in setOf(Kind.IMPACT,Kind.BOUNCE)) return emptyList()
        val r=angle*PI.toFloat()/180
        var x=active.x+cos(r)*23;var y=active.y-5+sin(r)*23
        var vx=cos(r)*weapon.speed*(.2f+.8f*power);var vy=sin(r)*weapon.speed*(.2f+.8f*power)
        return buildList {
            for(i in 0..if(active.aimAssist) 320 else 150) {
                vx+=wind*2.8f*STEP;vy+=arena.gravity*STEP;x+=vx*STEP;y+=vy*STEP
                if(terrain.at(x,y)||y>WATER_Y) break
                if(i%6==0) add(x to y)
            }
        }
    }

    /** Explicit versioned binary format; never deserializes executable objects. Caller owns stream. */
    fun save(output:OutputStream) {
        val d=DataOutputStream(output.buffered(64*1024))
        d.writeInt(0x52494654);d.writeInt(3);d.writeInt(Weapon.entries.size)
        d.writeInt(arena.ordinal);d.writeBoolean(againstBot);d.writeInt(seed)
        d.writeInt(mode.ordinal);d.writeInt(boss.ordinal)
        d.writeInt(squad.race.ordinal);d.writeInt(squad.hat.ordinal);d.writeInt(squad.artifact.ordinal);d.writeInt(squad.upgrade.ordinal)
        d.writeInt(randomState)
        d.writeInt(activeIndex);d.writeInt(turn);d.writeInt(phase.ordinal)
        d.writeFloat(timeLeft);d.writeFloat(wind);d.writeFloat(angle);d.writeFloat(power);d.writeInt(weapon.ordinal)
        d.writeFloat(phaseTime);d.writeFloat(botTime);d.writeInt(lastByTeam[0]);d.writeInt(lastByTeam[1])
        d.writeInt(winner ?: -2);d.writeUTF(message)
        d.writeBoolean(anchor!=null);anchor?.let {d.writeFloat(it.x);d.writeFloat(it.y);d.writeFloat(it.length)}
        for(f in fighters) {
            d.writeInt(f.race.ordinal);d.writeUTF(f.name);d.writeFloat(f.maxHp)
            d.writeInt(f.loadout.race.ordinal);d.writeInt(f.loadout.hat.ordinal);d.writeInt(f.loadout.artifact.ordinal);d.writeInt(f.loadout.upgrade.ordinal)
            d.writeFloat(f.x);d.writeFloat(f.y);d.writeFloat(f.vx);d.writeFloat(f.vy);d.writeFloat(f.hp)
            d.writeBoolean(f.grounded);d.writeFloat(f.shield);d.writeBoolean(f.abilityUsed)
            Weapon.entries.forEach {d.writeInt(f.ammo.getValue(it))}
            f.effects.forEach {d.writeInt(it)};d.writeFloat(f.fuel);d.writeFloat(f.drillFuel);d.writeBoolean(f.parachute);d.writeBoolean(f.aimAssist);d.writeBoolean(f.boots);d.writeFloat(f.damageBoost);d.writeBoolean(f.resurrected);d.writeInt(f.reviveIn);d.writeBoolean(f.lavaBounce)
        }
        d.writeInt(shots.size)
        for(s in shots) {
            d.writeFloat(s.x);d.writeFloat(s.y);d.writeFloat(s.vx);d.writeFloat(s.vy)
            d.writeInt(s.weapon.ordinal);d.writeInt(s.owner);d.writeFloat(s.fuse);d.writeBoolean(s.fragment);d.writeFloat(s.age)
            d.writeInt(s.bounces);d.writeBoolean(s.stuck);d.writeInt(s.attachedTo);d.writeBoolean(s.flying);d.writeFloat(s.targetX);d.writeFloat(s.targetY)
        }
        val mask=terrain.solid
        for(i in mask.indices step 8) {
            var bits=0
            for(b in 0..7) if(i+b<mask.size && mask[i+b]) bits=bits or (1 shl b)
            d.writeByte(bits)
        }
        d.writeInt(terrain.armor.count {it>0})
        terrain.armor.forEachIndexed {i,v->if(v>0) {d.writeInt(i);d.writeByte(v.toInt())}}
        d.writeFloat(waterLevel);d.writeFloat(grenadeTimer);d.writeBoolean(hardBounce);d.writeFloat(quakeTime)
        d.writeInt(checkpoints);d.writeFloat(raceElapsed);d.writeInt(bossPhase);d.writeInt(bossActionCount);d.writeUTF(bossWarning)
        d.writeInt(grabbedId);d.writeInt(shotgunShots);d.writeInt(switchesThisTurn)
        d.writeInt(ropeNodes.size);ropeNodes.forEach {d.writeFloat(it.x);d.writeFloat(it.y);d.writeFloat(it.length)}
        d.writeInt(hazards.size);hazards.forEach {d.writeFloat(it.x);d.writeFloat(it.y);d.writeFloat(it.radius);d.writeInt(it.effect.ordinal);d.writeInt(it.turns);d.writeInt(it.owner)}
        d.writeInt(devices.size);devices.forEach {d.writeInt(it.kind.ordinal);d.writeFloat(it.x);d.writeFloat(it.y);d.writeInt(it.owner);d.writeFloat(it.hp);d.writeFloat(it.timer);d.writeInt(it.turns);d.writeBoolean(it.triggered)}
        d.flush()
    }
    companion object {
        fun load(input:InputStream):Game {
            val d=DataInputStream(input.buffered(64*1024))
            require(d.readInt()==0x52494654) {"Invalid save header"}
            val version=d.readInt();require(version in 1..3) {"Unsupported save version"}
            val savedWeapons=if(version>=3) d.readInt().also {require(it in 57..Weapon.entries.size)} else if(version==2) 57 else 7
            fun number():Float = d.readFloat().also {require(it.isFinite() && abs(it)<100000)}
            fun index(size:Int)=d.readInt().also {require(it in 0 until size)}
            val arena=Arena.entries[index(Arena.entries.size)]
            val bot=d.readBoolean();val seed=d.readInt()
            fun readLoadout()=Loadout(Race.entries[index(Race.entries.size)],Hat.entries[index(Hat.entries.size)],Artifact.entries[index(Artifact.entries.size)],Upgrade.entries[index(Upgrade.entries.size)])
            val mode=if(version>=2) GameMode.entries[index(GameMode.entries.size)] else GameMode.DUEL
            val boss=if(version>=2) Boss.entries[index(Boss.entries.size)] else Boss.INFERNO
            val squad=if(version>=2) readLoadout() else Loadout()
            val g=Game(arena,bot,seed,mode,boss,squad)
            g.randomState=d.readInt();g.activeIndex=index(4);g.turn=d.readInt().also {require(it>0)}
            g.phase=Phase.entries[index(Phase.entries.size)]
            g.timeLeft=number().also {require(it in -1f..180.1f)};g.wind=number().also {require(it in -10f..10f)}
            g.angle=number();g.power=number().also {require(it in 0f..1f)};g.weapon=Weapon.entries[index(Weapon.entries.size)]
            g.phaseTime=number();g.botTime=number()
            g.lastByTeam[0]=d.readInt().also {require(it in -1..3)};g.lastByTeam[1]=d.readInt().also {require(it in -1..3)}
            val winner=d.readInt().also {require(it in -2..1)};g.winner=if(winner== -2) null else winner
            g.message=d.readUTF().also {require(it.length<1000)}
            if(d.readBoolean()) g.anchor=Anchor(number(),number(),number().also {require(it in 0f..901f)})
            for(f in g.fighters) {
                if(version>=2) {f.race=Race.entries[index(Race.entries.size)];f.name=d.readUTF().also {require(it.length<100)};f.maxHp=number().also {require(it in 1f..5000f)};f.loadout=readLoadout()}
                f.x=number();f.y=number();f.vx=number();f.vy=number();f.hp=number().also {require(it in 0f..f.maxHp)}
                f.grounded=d.readBoolean();f.shield=number().also {require(it in 0f..150f)};f.abilityUsed=d.readBoolean()
                Weapon.entries.take(savedWeapons).forEach {w->f.ammo[w]=d.readInt().also {require(it in -1..50)}}
                if(version>=2) {f.effects.indices.forEach {f.effects[it]=d.readInt().also {v->require(v in 0..10)}};f.fuel=number();f.drillFuel=number();f.parachute=d.readBoolean();f.aimAssist=d.readBoolean();f.boots=d.readBoolean();f.damageBoost=number();f.resurrected=d.readBoolean();f.reviveIn=d.readInt().also {require(it in 0..2)};f.lavaBounce=d.readBoolean()}
                else {f.x*=2;f.y*=WATER_Y/704f}
            }
            val count=d.readInt().also {require(it in 0..200)}
            repeat(count) {
                val x=number();val y=number();val vx=number();val vy=number()
                val w=Weapon.entries[index(Weapon.entries.size)];val owner=index(4)
                val s=Shot(x,y,vx,vy,w,owner,number(),d.readBoolean());s.age=number()
                if(version>=2) {s.bounces=d.readInt();s.stuck=d.readBoolean();s.attachedTo=d.readInt().also {require(it in -1..3)};s.flying=d.readBoolean();s.targetX=number();s.targetY=number()}
                else {s.x*=2;s.y*=WATER_Y/704;s.vx*=2;s.vy*=WATER_Y/704}
                g.shots+=s
            }
            val mask=g.terrain.solid
            val incoming=if(version==1) BooleanArray(1440*810) else mask
            for(i in incoming.indices step 8) {
                val bits=d.readUnsignedByte()
                for(b in 0..7) if(i+b<incoming.size) incoming[i+b]=(bits and (1 shl b))!=0
            }
            if(version==1) {g.terrain.armor.fill(0);for(y in 0 until WORLD_H) for(x in 0 until WORLD_W) mask[y*WORLD_W+x]=incoming[(y*704f/WATER_Y).toInt().coerceIn(0,809)*1440+(x/2)]}
            else {
                g.terrain.armor.fill(0)
                repeat(d.readInt().also {require(it in 0..200000)}) {val i=index(WORLD_W*WORLD_H);g.terrain.armor[i]=d.readByte().also {require(it in 1..2)}}
                g.waterLevel=number().also {require(it in 500f..WATER_Y)};g.grenadeTimer=number().also {require(it in 1f..5f)};g.hardBounce=d.readBoolean();g.quakeTime=number()
                g.checkpoints=d.readInt().also {require(it in 0..5)};g.raceElapsed=number();g.bossPhase=d.readInt().also {require(it in 1..3)};g.bossActionCount=d.readInt();g.bossWarning=d.readUTF()
                g.grabbedId=d.readInt().also {require(it in -1..3)};g.shotgunShots=d.readInt().also {require(it in 0..1)};g.switchesThisTurn=d.readInt().also {require(it in 0..20)}
                repeat(d.readInt().also {require(it in 0..12)}) {g.ropeNodes+=Anchor(number(),number(),number())}
                g.hazards.clear();repeat(d.readInt().also {require(it in 0..256)}) {g.hazards+=Hazard(number(),number(),number(),Effect.entries[index(Effect.entries.size)],d.readInt().also {require(it in 0..10)},d.readInt().also {require(it in -1..3)})}
                g.devices.clear();repeat(d.readInt().also {require(it in 0..100)}) {
                    val kind=DeviceKind.entries[index(DeviceKind.entries.size)];val x=number();val y=number();val owner=d.readInt().also {require(it in -1..3)}
                    g.devices+=Device(kind,x,y,owner,number(),number(),d.readInt(),d.readBoolean())
                }
            }
            return g
        }
    }
}
