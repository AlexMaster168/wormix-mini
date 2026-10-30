package com.riftartillery.core

import kotlin.math.*

val DeviceKind.airdrop get()=ordinal in DeviceKind.AIRDROP_AMMO.ordinal..DeviceKind.AIRDROP_RARE.ordinal
fun Game.airdropStatus():String {
    val d=devices.filter {it.kind.airdrop && it.hp>0}.minByOrNull {if(!it.triggered) -10000f+it.y else abs(it.x-active.x)} ?: return ""
    return "${d.kind.label}: "+if(d.timer>0) "через ${ceil(d.timer).toInt()} с" else if(!d.triggered) "снижается ↓" else "на земле / F"
}
internal fun Game.spawnAirdrop(kind:DeviceKind?=null,requestedX:Float?=null):Boolean {
    if(mode==GameMode.ROPE_RACE || devices.count {it.kind.airdrop}>=4 || devices.size>=90) return false
    val type=kind ?: DeviceKind.entries[DeviceKind.AIRDROP_AMMO.ordinal+(roll()*4).toInt().coerceIn(0,3)]
    require(type.airdrop)
    val x=(requestedX ?: (450+roll()*1950)).coerceIn(60f,WORLD_W-60f)
    val loot=Weapon.entries.drop(120).filter {it.kind !in setOf(Kind.UTILITY,Kind.DEPLOY,Kind.GLOBAL)}
    val weapon=loot[(roll()*loot.size).toInt().coerceIn(0,loot.lastIndex)]
    devices+=Device(type,x,70f,-1,55f,3f,weapon.ordinal,false)
    say("ДРОП: ${type.label}. Доставка через 3 секунды.")
    return true
}
internal fun Game.updateAirdrop(d:Device,dt:Float) {
    if(d.hp<=0 || d.y>waterLevel-15) {devices.remove(d);return}
    if(d.timer>0) {d.timer=(d.timer-dt).coerceAtLeast(0f);return}
    if(d.triggered) {
        if(terrain.at(d.x,d.y+22)) return
        d.triggered=false
    }
    d.x=(d.x+wind*dt*.8f).coerceIn(30f,WORLD_W-30f)
    repeat(4) {
        if(terrain.at(d.x,d.y+22)) {d.triggered=true;return}
        d.y+=32f*dt
    }
}
internal fun Game.collectAirdrop(d:Device):Boolean {
    if(!d.triggered || d.timer>0 || d.hp<=0) return false
    val f=active
    when(d.kind) {
        DeviceKind.AIRDROP_MED->{
            if(f.hp>=f.maxHp && f.effects.all {it==0}) {say("Здоровье полное. Медицинский дроп оставлен.");return false}
            val gain=min(45f,f.maxHp-f.hp);f.hp+=gain;f.effects.fill(0);texts+=DamageText(f.x,f.y-50,"+${gain.toInt()} HP");say("Медицинский дроп: лечение и антидот.")
        }
        DeviceKind.AIRDROP_ARMOR->{if(f.shield>=120) return false;f.shield=min(120f,f.shield+35);say("Бронекомплект: +35 защиты, атака сохранена.")}
        DeviceKind.AIRDROP_AMMO,DeviceKind.AIRDROP_RARE->{
            val w=if(d.kind==DeviceKind.AIRDROP_RARE) Weapon.entries[d.turns.coerceIn(120,Weapon.entries.lastIndex)] else if(f.ammo.getValue(weapon)<0) Weapon.BURST_RIFLE else weapon
            val count=if(d.kind==DeviceKind.AIRDROP_RARE) 3 else 2
            if(f.ammo.getValue(w)>=99) {say("Боеприпасы уже заполнены.");return false}
            f.ammo[w]=(f.ammo.getValue(w).coerceAtLeast(0)+count).coerceAtMost(99)
            val equip=d.kind==DeviceKind.AIRDROP_RARE && shotgunShots==0
            if(equip) select(w)
            say("Подобрано: ${w.title} +$count${if(equip) ". Оружие выбрано." else "."}")
        }
        else->return false
    }
    devices.remove(d);burst(f.x,f.y-25,18,2);return true
}
internal fun Game.fire050():FireResult? {
    val f=active;val r=angle*PI.toFloat()/180;val dx=cos(r);val dy=sin(r)
    when(weapon) {
        Weapon.AIRDROP_BEACON,Weapon.MED_DRONE->{
            val point=target ?: return FireResult.FAILED
            return if(spawnAirdrop(if(weapon==Weapon.MED_DRONE) DeviceKind.AIRDROP_MED else null,point.first)) FireResult.FREE else FireResult.FAILED
        }
        Weapon.FIRE_LANCE,Weapon.VENOM_SPRAYER,Weapon.ACID_SPRAYER->{
            val effect=when(weapon) {Weapon.FIRE_LANCE->Effect.BURN;Weapon.VENOM_SPRAYER->Effect.POISON;else->Effect.CORROSION}
            val range=when(weapon) {Weapon.FIRE_LANCE->400f;Weapon.VENOM_SPRAYER->300f;else->245f}
            repeat(10) {i->val x=f.x+dx*(i+1)*range/10;val y=f.y-20+dy*(i+1)*range/10;hazards+=Hazard(x,y,30f,effect,3,f.id);if(effect!=Effect.POISON) terrain.carve(x,y,if(effect==Effect.CORROSION) 15f else 5f)}
            fighters.filter {it.alive && it.id!=f.id && hypot(it.x-f.x,it.y-f.y)<range && (it.x-f.x)*dx+(it.y-f.y)*dy>0}.forEach {hurt(it,weapon.damage,weapon.damageType,f.id);applyWeaponEffect(weapon,it)}
            return FireResult.USED
        }
        Weapon.SATELLITE->{
            val point=target ?: return FireResult.FAILED
            for(offset in -1..1) {val x=(point.first+offset*60).coerceIn(30f,WORLD_W-30f)
                for(y in 100..waterLevel.toInt() step 20) terrain.carve(x,y.toFloat(),19f)
                fighters.filter {it.alive && abs(it.x-x)<28}.forEach {hurt(it,50f,DamageType.ELECTRIC,f.id)}
                damageDevices(x,waterLevel/2,40f,60f);beams+=Beam(x,0f,x,waterLevel,.9f)
            }
            return FireResult.USED
        }
        else->return null
    }
}
internal fun Game.explode050(s:Shot):Boolean {
    if(s.weapon in setOf(Weapon.SHOCK_CLUSTER,Weapon.METAL_STORM) && !s.fragment) {
        val count=if(s.weapon==Weapon.SHOCK_CLUSTER) 6 else 12
        val payload=if(s.weapon==Weapon.SHOCK_CLUSTER) Weapon.THUNDER_ORB else Weapon.SPIKE_GRENADE
        repeat(count) {i->val a=-PI.toFloat()+i*PI.toFloat()/(count-1);shots+=Shot(s.x,s.y-8,cos(a)*280,sin(a)*280-90,payload,s.owner,.8f,true)}
        burst(s.x,s.y,28,2);return true
    }
    if(s.weapon==Weapon.TAR_BOMB) hazards+=Hazard(s.x,s.y,120f,Effect.FREEZE,3,s.owner)
    if(s.weapon==Weapon.VACUUM_ROCKET) hazards+=Hazard(s.x,s.y,200f,Effect.STUN,2,s.owner)
    return false
}
