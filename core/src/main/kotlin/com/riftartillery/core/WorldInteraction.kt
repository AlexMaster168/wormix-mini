package com.riftartillery.core

import kotlin.math.*

val DeviceKind.interactive get()=ordinal in DeviceKind.SUPPLY.ordinal..DeviceKind.PORTAL.ordinal
val DeviceKind.label get()=when(this) {
    DeviceKind.SUPPLY->"Снабжение";DeviceKind.AMMO_CRATE->"Боеприпасы";DeviceKind.MED_STATION->"Аптечная станция"
    DeviceKind.JUMP_PAD->"Трамплин";DeviceKind.BRIDGE_SWITCH->"Мост";DeviceKind.PORTAL->"Портал"
    DeviceKind.ACID_BARREL->"Кислота";DeviceKind.SHOCK_BARREL->"Электробочка";DeviceKind.ARC_TURRET->"Тесла-турель"
    else->name
}
val DeviceKind.tint get()=when(this) {
    DeviceKind.MED_STATION,DeviceKind.ACID_BARREL->0xffb1f070.toInt()
    DeviceKind.JUMP_PAD->0xffff8a70.toInt();DeviceKind.PORTAL->0xffbf8aff.toInt()
    DeviceKind.SHOCK_BARREL->0xffffda65.toInt();else->0xff65d9ff.toInt()
}
fun Game.nearbyInteraction():Device? {
    if(phase!=Phase.AIM || !canControl) return null
    return devices.filter {it.kind.interactive && it.hp>0 && it.timer<=0 && hypot(it.x-active.x,it.y-active.y)<110}
        .minByOrNull {hypot(it.x-active.x,it.y-active.y)}
}
fun Game.interact():Boolean {
    val d=nearbyInteraction() ?: return false;val f=active
    fun grant(w:Weapon,n:Int) {if(f.ammo.getValue(w)>=0) f.ammo[w]=(f.ammo.getValue(w)+n).coerceAtMost(99)}
    when(d.kind) {
        DeviceKind.SUPPLY->{grant(Weapon.HEAL,1);grant(Weapon.GRENADE,3);grant(Weapon.BOLT_ROCKET,2);devices.remove(d);say("Снабжение: аптечка, три гранаты и две Иглы.")}
        DeviceKind.AMMO_CRATE->{val w=if(f.ammo.getValue(weapon)<0) Weapon.BURST_RIFLE else weapon;grant(w,2);devices.remove(d);say("${w.title}: +2 боеприпаса.")}
        DeviceKind.MED_STATION->{
            if(f.hp>=f.maxHp && f.effects.all {it==0}) {say("Лечение не требуется.");return false}
            f.hp=min(f.maxHp,f.hp+30);f.effects.fill(0);d.turns--;d.timer=3f
            if(d.turns<=0) devices.remove(d)
            texts+=DamageText(f.x,f.y-60,"+30 HP");say("Станция восстановила здоровье. Атака сохранена.")
        }
        DeviceKind.JUMP_PAD->{clearRope();f.vy= -650f;f.grounded=false;f.parachute=true;d.timer=2.5f;say("Трамплин: парашют включён. Управляй полётом!")}
        DeviceKind.BRIDGE_SWITCH->{
            d.triggered=!d.triggered;d.timer=1f
            if(d.triggered) terrain.addGirder(1440f,1010f,0f,280f)
            else for(x in 1300..1580 step 8) terrain.carve(x.toFloat(),1010f,13f)
            say(if(d.triggered) "Мост раскрыт через центральный разлом." else "Мост убран.")
        }
        DeviceKind.PORTAL->{
            val next=devices.firstOrNull {it!==d && it.kind==DeviceKind.PORTAL && it.hp>0} ?: return false
            if(!validPosition(next.x,next.y-2)) {say("Выход портала занят.");return false}
            clearRope();f.x=next.x;f.y=next.y-2;f.vx=0f;f.vy=0f;f.grounded=false;d.timer=3f;next.timer=3f;say("Переход выполнен. Ход и атака сохранены.")
        }
        else->return false
    }
    burst(f.x,f.y-25,14,2);return true
}
internal fun Game.populateInteractions() {
    if(mode==GameMode.ROPE_RACE) return
    fun put(kind:DeviceKind,x:Float,hp:Float=70f,charges:Int=999) {
        val safeX=if(terrain.surface(x)>=waterLevel-100) (1..12).map {x+it*15}.firstOrNull {it<WORLD_W-30 && terrain.surface(it)<waterLevel-100} ?: return else x
        devices+=Device(kind,safeX,terrain.surface(safeX)-20,-1,hp,0f,charges)
    }
    put(DeviceKind.MED_STATION,410f,80f,3);put(DeviceKind.SUPPLY,870f);put(DeviceKind.AMMO_CRATE,1930f)
    put(DeviceKind.JUMP_PAD,1230f,120f);put(DeviceKind.BRIDGE_SWITCH,1320f,120f)
    put(DeviceKind.PORTAL,1010f,100f);put(DeviceKind.PORTAL,2300f,100f)
    put(DeviceKind.ACID_BARREL,1630f,30f);put(DeviceKind.SHOCK_BARREL,2460f,35f)
}

internal fun Game.fireExpansion():FireResult? {
    val f=active;val r=angle*PI.toFloat()/180;val dx=cos(r);val dy=sin(r)
    when(weapon) {
        Weapon.SHIELD_MINE->{if(f.shield>=120) return FireResult.FAILED;f.shield=min(120f,f.shield+25);say("Щитовой маяк: +25 защиты. Атака сохранена.");return FireResult.FREE}
        Weapon.BARRIER->{val p=target ?: return FireResult.FAILED;if(p.first !in 100f..WORLD_W-100f || p.second !in 200f..waterLevel-100 || terrain.at(p.first,p.second)) return FireResult.FAILED;terrain.addGirder(p.first,p.second,90f,170f);return FireResult.USED}
        Weapon.HEAL_TOTEM,Weapon.ARC_TURRET->{
            if(devices.size>=90) return FireResult.FAILED
            val x=(f.x+dx*55).coerceIn(25f,WORLD_W-25f)
            devices+=Device(if(weapon==Weapon.HEAL_TOTEM) DeviceKind.MED_STATION else DeviceKind.ARC_TURRET,x,f.y,f.id,65f,0f,if(weapon==Weapon.HEAL_TOTEM) 3 else 4)
            return FireResult.USED
        }
        Weapon.MIRROR_GUN->{
            var x=f.x+dx*25;var y=f.y-20+dy*25;var vx=dx;var vy=dy;var startX=x;var startY=y;var bounces=0;val hits=mutableSetOf<Int>()
            for(i in 0..1200) {
                val nx=x+vx*3;val ny=y+vy*3
                if(nx !in 0f..WORLD_W.toFloat() || ny !in 0f..waterLevel) break
                if(terrain.at(nx,ny)) {
                    beams+=Beam(startX,startY,x,y,.6f);if(bounces++>=2) break
                    if(terrain.at(nx,y)) vx= -vx else vy= -vy
                    startX=x;startY=y;continue
                }
                x=nx;y=ny
                fighters.firstOrNull {it.alive && it.id!=f.id && it.id !in hits && hitsFighter(it,x,y)}?.let {hurt(it,35f,DamageType.ELECTRIC,f.id);hits+=it.id}
            }
            beams+=Beam(startX,startY,x,y,.6f);return FireResult.USED
        }
        else->return null
    }
}
internal fun Game.explodeExpansion(s:Shot):Boolean {
    if(s.weapon==Weapon.HEAL_GRENADE) {
        val team=fighters[s.owner].team
        fighters.filter {it.alive && it.team==team && hypot(it.x-s.x,it.y-25-s.y)<130}.forEach {it.hp=min(it.maxHp,it.hp+35);it.effects.fill(0);texts+=DamageText(it.x,it.y-50,"+35 HP")}
        burst(s.x,s.y,25,2);return true
    }
    if(s.weapon==Weapon.ICE_SHARDS && !s.fragment) {
        repeat(7) {val a=-PI.toFloat()+it*PI.toFloat()/6;shots+=Shot(s.x,s.y-8,cos(a)*260,sin(a)*260-70,Weapon.CRYO,s.owner,0f,true)}
        burst(s.x,s.y,20,2);return true
    }
    if(s.weapon==Weapon.BLACK_HOLE) hazards+=Hazard(s.x,s.y,230f,Effect.STUN,2,s.owner)
    return false
}
