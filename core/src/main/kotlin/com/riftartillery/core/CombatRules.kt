package com.riftartillery.core

import kotlin.math.*

internal enum class FireResult { NORMAL, FAILED, USED, FREE, CONTINUE }

internal fun Game.fireContent(tx:Float?,ty:Float?):FireResult {
    val w=weapon;val f=active;val r=angle*PI.toFloat()/180;val dx=cos(r);val dy=sin(r)
    if(w.targeted && (tx==null || ty==null)) {say("${w.title}: отметь цель на карте.");return FireResult.FAILED}
    if(tx!=null && ty!=null) target=tx to ty
    fireExpansion()?.let {return it}
    when(w) {
        Weapon.HEAL -> {
            if(f.hp>=f.maxHp && f.effects.all {it==0}) {say("Здоровье уже полное.");return FireResult.FAILED}
            val gain=min(50f,f.maxHp-f.hp);f.hp+=gain;f.effects.fill(0)
            texts+=DamageText(f.x,f.y-30,"+${gain.toInt()}");say("Лечение применено. Можно двигаться и атаковать.")
            return FireResult.FREE
        }
        Weapon.TELEPORT -> {
            if(!validPosition(tx!!,ty!!)) {say("Телепорт: выбери свободную точку над землёй.");return FireResult.FAILED}
            clearRope();f.x=tx;f.y=ty;f.vx=0f;f.vy=0f;f.grounded=false
            burst(f.x,f.y,24,2);say("Телепортация завершена. Ход продолжается.");return FireResult.FREE
        }
        Weapon.ROPE -> {return if(rope()) FireResult.FREE else FireResult.FAILED}
        Weapon.JETPACK -> {f.jetboard=false;f.fuel=100f;say("Ранец включён: удерживай ↑ для тяги.");return FireResult.FREE}
        Weapon.JETBOARD -> {f.jetboard=true;f.fuel=160f;f.parachute=true;say("Доска включена: ↑ подъём, ↓ снижение, ← → полёт.");return FireResult.FREE}
        Weapon.BOOTS -> {f.boots=true;say("Прыжок усилен до конца хода.");return FireResult.FREE}
        Weapon.DIGGER -> {f.drillFuel=100f;say("Бур включён. Направь прицел и удерживай движение для проходки.");return FireResult.FREE}
        Weapon.DEFENDER -> {val gained=min(40f,120-f.shield);if(gained<=0) {say("Защита уже максимальна.");return FireResult.FAILED};f.shield+=gained;devices+=Device(DeviceKind.DEFENDER,f.x,f.y-95,f.id,gained,0f,2);say("Защитник активен: +${gained.toInt()} защиты на два хода.");return FireResult.FREE}
        Weapon.PARACHUTE -> {f.parachute=true;say("Парашют готов к раскрытию.");return FireResult.FREE}
        Weapon.AIM_SIGHT -> {f.aimAssist=true;say("Прогноз траектории усилен.");return FireResult.FREE}
        Weapon.SWITCH -> {
            val next=fighters.firstOrNull {it.alive && it.team==f.team && it.id!=f.id}
            return if(next!=null && switchFighter(next.id,true)) FireResult.FREE else FireResult.FAILED
        }
        Weapon.TELEKINESIS -> {
            if(grabbedId<0) {
                val hit=fighters.firstOrNull {it.alive && it.id!=f.id && hypot(it.x-tx!!,it.y-ty!!)<65 && hypot(it.x-f.x,it.y-f.y)<700}
                if(hit==null) {say("Выбери бойца в пределах 700 пикселей.");return FireResult.FAILED}
                grabbedId=hit.id;say("Теперь выбери место переноса.");return FireResult.CONTINUE
            }
            if(!validPosition(tx!!,ty!!)) {say("Место переноса занято.");return FireResult.FAILED}
            fighters[grabbedId].apply {x=tx;y=ty;vx=0f;vy=0f};grabbedId= -1;return FireResult.USED
        }
        else->Unit
    }
    if(w.kind==Kind.DEPLOY) {
        if(w==Weapon.GIRDER) {
            if(tx!! !in 100f..WORLD_W-100f || ty!! !in 200f..waterLevel-100 || terrain.at(tx,ty)) {say("Для балки нужна свободная точка.");return FireResult.FAILED}
            terrain.addGirder(tx,ty,angle);return FireResult.USED
        }
        val kind=when(w) {Weapon.MINE->DeviceKind.MINE;Weapon.SPIDER->DeviceKind.SPIDER;Weapon.TURRET->DeviceKind.TURRET;else->DeviceKind.DOME}
        if(devices.size>=40) {say("Лимит устройств на карте.");return FireResult.FAILED}
        devices+=Device(kind,f.x+dx*40,f.y+8,f.id,if(kind==DeviceKind.DOME) 120f else 60f,2f,if(kind==DeviceKind.DOME) 2 else 8)
        return FireResult.USED
    }
    if(w.kind==Kind.GLOBAL) {
        val x=tx ?: fighters.filter {it.alive && it.team!=f.team}.firstOrNull()?.x ?: WORLD_W/2f
        when(w) {
            Weapon.FLOOD -> {waterLevel=(waterLevel-90).coerceAtLeast(550f);say("Уровень опасности поднялся!")}
            Weapon.QUAKE -> {quakeTime=4f;say("Землетрясение!")}
            Weapon.ION -> {
                for(y in 100..waterLevel.toInt() step 20) terrain.carve(x,y.toFloat(),27f)
                fighters.filter {it.alive && abs(it.x-x)<45}.forEach {hurt(it,70f,DamageType.ELECTRIC,f.id)}
                beams+=Beam(x,0f,x,waterLevel,1.2f);damageDevices(x,waterLevel/2,waterLevel,30f)
            }
            Weapon.BUNKER -> {
                val surface=terrain.surface(x)
                for(y in surface.toInt()..surface.toInt()+150 step 12) terrain.carve(x,y.toFloat(),18f)
                explode(Shot(x,surface+150,0f,0f,w,f.id,0f))
            }
            else-> {
                val count=if(w==Weapon.COMET) 3 else if(w==Weapon.DRONE_STRIKE) 5 else if(w==Weapon.METEOR) 8 else if(w==Weapon.CARPET_BOMB) 12 else 6
                repeat(count) {i->
                    val px=if(w==Weapon.COMET) (x+(i-1)*140).coerceIn(40f,WORLD_W-40f) else if(w==Weapon.METEOR) 150+roll()*(WORLD_W-300) else (x+(i-2.5f)*90).coerceIn(40f,WORLD_W-40f)
                    shots+=Shot(px,50f-i*75,if(w==Weapon.METEOR) (roll()-.5f)*150 else 0f,240f,w,f.id,0f)
                }
            }
        }
        return FireResult.USED
    }
    if(w.kind==Kind.MELEE) {
        if(w==Weapon.KAMIKAZE) {
            val x=(f.x+dx*280).coerceIn(30f,WORLD_W-30f);val y=f.y+dy*120
            for(i in 0..20) terrain.carve(f.x+(x-f.x)*i/20,f.y+(y-f.y)*i/20,35f)
            explode(Shot(x,y,0f,0f,w,f.id,0f));f.hp=0f;f.reviveIn=0
        } else {
            val victim=fighters.filter {it.alive && it.id!=f.id && hypot(it.x-f.x,it.y-f.y)<105 && (it.x-f.x)*dx>= -10}.minByOrNull {hypot(it.x-f.x,it.y-f.y)}
            if(victim==null) {say("Цель вне досягаемости удара.");return FireResult.FAILED}
            hurt(victim,w.damage,w.damageType,f.id)
            if(w==Weapon.HAMMER) {terrain.carve(victim.x,victim.y+35,45f);victim.vy=160f}
            if(w==Weapon.BAT) {victim.vx=dx*600;victim.vy=dy*600-120}
            if(w==Weapon.TASER) victim.effects[Effect.STUN.ordinal]=1
        }
        return FireResult.USED
    }
    if(w.kind==Kind.BEAM) {
        if(w in setOf(Weapon.REPULSOR,Weapon.SONIC)) {
            val range=if(w==Weapon.SONIC) 320f else 220f
            fighters.filter {it.alive && it.id!=f.id && hypot(it.x-f.x,it.y-f.y)<range && (it.x-f.x)*dx+(it.y-f.y)*dy>0}.forEach {hurt(it,w.damage,DamageType.ELECTRIC,f.id);it.vx+=dx*650;it.vy= -230f}
            beams+=Beam(f.x,f.y-20,f.x+dx*range,f.y-20+dy*range);return FireResult.USED
        }
        if(w in setOf(Weapon.FLAME,Weapon.HEAVY_FLAMER,Weapon.FROST_THROWER)) {
            val range=if(w==Weapon.HEAVY_FLAMER) 310f else if(w==Weapon.FROST_THROWER) 270f else 230f
            val effect=if(w==Weapon.FROST_THROWER) Effect.FREEZE else Effect.BURN
            for(i in 1..12) {val x=f.x+dx*i*range/12;val y=f.y-20+dy*i*range/12
                hazards+=Hazard(x,y,if(w==Weapon.HEAVY_FLAMER) 40f else 28f,effect,3,f.id)
                if(effect==Effect.BURN) terrain.carve(x,y,8f)
            }
            fighters.filter {it.alive && it.id!=f.id && hypot(it.x-f.x,it.y-f.y)<range && (it.x-f.x)*dx+(it.y-f.y)*dy>0}.forEach {hurt(it,w.damage,w.damageType,f.id);applyWeaponEffect(w,it)}
            return FireResult.USED
        }
        if(w==Weapon.DIGGER) {
            for(i in 1..12) {val x=f.x+dx*i*12;val y=f.y+dy*i*12;terrain.carve(x,y,27f)}
            fighters.filter {it.alive && it.id!=f.id && hypot(it.x-f.x,it.y-f.y)<160}.forEach {hurt(it,30f,DamageType.MELEE,f.id)}
            return FireResult.USED
        }
        val count=when(w) {Weapon.TRI_LASER->3;Weapon.FLECHETTE->24;Weapon.SCATTER_PISTOL->6;Weapon.RAIL_BURST->2;Weapon.UZI->15;Weapon.MINIGUN->30;Weapon.AK47->7;Weapon.BURST_RIFLE->5;Weapon.AUTO_SHOTGUN->10;Weapon.SHRAPNEL_CANNON->16;Weapon.SHURIKEN->3;Weapon.DUAL_PISTOLS->8;Weapon.NAILGUN->20;else->1}
        repeat(count) {i->
            val spread=when(w) {Weapon.FLECHETTE->.2f;Weapon.SCATTER_PISTOL->.4f;Weapon.UZI->.09f;Weapon.MINIGUN->.055f;Weapon.AK47->.035f;Weapon.BURST_RIFLE->.05f;Weapon.AUTO_SHOTGUN->.28f;Weapon.SHRAPNEL_CANNON->.16f;Weapon.SHURIKEN->.018f;Weapon.SHOTGUN->.025f;else->0f}
            val ray=r+(if(w==Weapon.TRI_LASER) (i-1)*.08f else (roll()-.5f)*spread);val rx=cos(ray);val ry=sin(ray)
            var bx=f.x;var by=f.y-20;val pierced= mutableSetOf<Int>();var drilled=0
            for(d in 25..3500 step 3) {
                bx=f.x+rx*d;by=f.y-20+ry*d
                val device=devices.firstOrNull {it.hp>0 && hypot(it.x-bx,it.y-by)<22}
                if(device!=null) {device.hp-=w.damage/count;break}
                val hit=fighters.firstOrNull {(it.alive || it.reviveIn>0) && it.id!=f.id && it.id !in pierced && hitsFighter(it,bx,by)}
                if(hit!=null) {
                    val dmg=if(w==Weapon.SNIPER && by<hit.y-40) 70f else w.damage/count
                    hurt(hit,dmg,w.damageType,f.id);applyWeaponEffect(w,hit);pierced+=hit.id
                    hit.vx+=rx*(if(w==Weapon.SHOTGUN) 230 else if(w==Weapon.HAND_CANNON) 330 else 12);hit.vy+=ry*20
                    if(w in setOf(Weapon.TESLA,Weapon.CHAIN_LIGHTNING,Weapon.ARC_THROWER)) {
                        val limit=if(w==Weapon.TESLA) 2 else 3
                        fighters.filter {it.alive && it.id!=f.id && it.id!=hit.id && hypot(it.x-hit.x,it.y-hit.y)<if(w==Weapon.TESLA) 180 else 260}.take(limit).forEachIndexed {n,next->hurt(next,if(w==Weapon.TESLA) 30f-n*15 else 40f-n*10,DamageType.ELECTRIC,f.id);beams+=Beam(hit.x,hit.y-20,next.x,next.y-20)}
                    }
                    if(w !in setOf(Weapon.SNIPER,Weapon.RAILGUN,Weapon.PHOTON_RIFLE) || pierced.size==(if(w in setOf(Weapon.RAILGUN,Weapon.PHOTON_RIFLE)) 3 else 2)) break
                }
                if(terrain.at(bx,by)) {
                    val limit=when(w) {Weapon.LASER->40;Weapon.RAILGUN->100;Weapon.GAUSS->60;Weapon.LASER_CUTTER->60;Weapon.RAIL_BURST->90;Weapon.MINIGUN->5;else->0}
                    if(drilled<limit) {terrain.carve(bx,by,if(w==Weapon.MINIGUN) 4f else 6f);drilled++}
                    else break
                }
                if(bx<0 || bx>WORLD_W || by>waterLevel) break
            }
            beams+=Beam(f.x,f.y-20,bx,by,if(w==Weapon.LASER) .65f else .25f)
        }
        f.vx-=dx*if(w==Weapon.MINIGUN) 75 else if(w==Weapon.AK47) 30 else 0
        if(w==Weapon.SHOTGUN && shotgunShots==0) {shotgunShots=1;say("Дробовик: остался второй выстрел.");return FireResult.CONTINUE}
        shotgunShots=0;return FireResult.USED
    }
    if(w.kind==Kind.GUIDED || w.kind==Kind.WALKER) {
        repeat(if(w==Weapon.SEEKER_SWARM) 3 else 1) {i->
            val ar=r+(if(w==Weapon.SEEKER_SWARM) (i-1)*.12f else 0f)
            val s=Shot(f.x+dx*30,f.y-15,cos(ar)*w.speed,if(w.kind==Kind.WALKER) 0f else sin(ar)*w.speed,w,f.id,w.fuse)
            s.targetX=tx ?: f.x+dx*1000;s.targetY=ty ?: f.y;shots+=s
        };return FireResult.USED
    }
    return FireResult.NORMAL
}

internal fun Game.applyWeaponEffect(w:Weapon,f:Fighter) {
    val effect=when {w.iceEffect->Effect.FREEZE;w.poisonEffect->Effect.POISON;w.fireEffect->Effect.BURN;w in setOf(Weapon.ACID,Weapon.ACID_CLUSTER,Weapon.ACID_ROCKET,Weapon.ACID_SPRAYER)->Effect.CORROSION;w in setOf(Weapon.FLASH,Weapon.SMOKE)->Effect.BLIND;w.bleedEffect->Effect.BLEED;w in setOf(Weapon.SHOCK_ROCKET,Weapon.EMP_GRENADE,Weapon.THUNDER_ORB,Weapon.NOVA_BOMB,Weapon.SHOCK_CLUSTER)->Effect.STUN;else->null}
    if(effect!=null) {
        if(effect==Effect.BURN && f.race==Race.DEMON) return
        if(effect==Effect.POISON && f.race in setOf(Race.CYBORG,Race.MUTANT)) return
        f.effects[effect.ordinal]=max(f.effects[effect.ordinal],if(effect in setOf(Effect.BLIND,Effect.FREEZE)) 1 else 3)
    }
}

internal fun Game.damageDevices(x:Float,y:Float,radius:Float,damage:Float) {
    devices.filter {it.hp>0 && hypot(it.x-x,it.y-y)<radius+25}.forEach {it.hp-=damage}
}

internal fun Game.updateContent(dt:Float) {
    for(h in hazards) {
        if(h.effect==Effect.POISON) {h.x=(h.x+wind*dt).coerceIn(0f,WORLD_W.toFloat());if(!terrain.at(h.x,h.y+2)) h.y+=14*dt}
        if(h.effect==Effect.STUN) fighters.filter {it.alive && hypot(it.x-h.x,it.y-h.y)<h.radius}.forEach {
            it.vx+=(h.x-it.x)*dt*3;it.vy+=(h.y-it.y)*dt*3
        }
    }
    if(quakeTime>0) {quakeTime-=dt;shake=8f;fighters.filter {it.alive}.forEach {it.vx+=(roll()-.5f)*90*dt;if(it.grounded) {it.vy= -90f;it.grounded=false}}}
    for(d in devices.toList()) {
        if(d.kind.airdrop) {updateAirdrop(d,dt);continue}
        if(d.kind.interactive && d.hp>0) {d.timer=(d.timer-dt).coerceAtLeast(0f);continue}
        if(d.kind==DeviceKind.DEFENDER) {if(d.hp<=0) devices.remove(d) else fighters.getOrNull(d.owner)?.let {d.x=it.x+25;d.y=it.y-100};continue}
        if(d.hp<=0) {
            devices.remove(d)
            if(d.kind in setOf(DeviceKind.ACID_BARREL,DeviceKind.SHOCK_BARREL)) explode(Shot(d.x,d.y,0f,0f,if(d.kind==DeviceKind.ACID_BARREL) Weapon.ACID_ROCKET else Weapon.THUNDER_ORB,0,0f))
            if(d.kind in setOf(DeviceKind.BARREL,DeviceKind.GENERATOR)) {
                explode(Shot(d.x,d.y,0f,0f,if(arena==Arena.CHEMICAL) Weapon.GAS else Weapon.ROCKET,d.owner.coerceAtLeast(0),0f))
            }
            if(d.kind==DeviceKind.MAGNET) devices+=d.copy(hp=9999f,timer=8f,triggered=true)
            continue
        }
        if(d.kind==DeviceKind.MAGNET && d.triggered && d.timer>0) {d.timer-=dt;shots.forEach {s->s.vy-=400*dt};continue}
        if(d.kind !in setOf(DeviceKind.MINE,DeviceKind.SPIDER)) continue
        d.timer-=dt
        val owner=fighters.getOrNull(d.owner)
        val enemy=fighters.filter {it.alive && (d.kind==DeviceKind.MINE || it.team!=owner?.team)}.minByOrNull {hypot(it.x-d.x,it.y-d.y)}
        if(d.kind==DeviceKind.SPIDER && d.timer<=0 && enemy!=null && hypot(enemy.x-d.x,enemy.y-d.y)<350) {
            val step=sign(enemy.x-d.x)*120*dt
            if(!terrain.at(d.x+step,d.y-20)) d.x+=step else d.y-=90*dt
            if(!terrain.at(d.x,d.y+12)) d.y+=110*dt
        }
        if(d.timer<=0 && !d.triggered && enemy!=null && hypot(enemy.x-d.x,enemy.y-d.y)<50) {d.triggered=true;d.timer=1.5f}
        if(d.triggered && d.timer<=0) {devices.remove(d);explode(Shot(d.x,d.y,0f,0f,Weapon.MINE,d.owner,0f))}
    }
}

internal fun Game.contentTurn() {
    hazards.forEach {it.turns--};hazards.removeAll {it.turns<=0}
    if(turn%3==0 && devices.none {it.kind.airdrop && it.timer>0}) spawnAirdrop()
    devices.filter {!it.kind.interactive}.forEach {it.turns--}
    devices.filter {it.turns<=0 && it.kind==DeviceKind.DEFENDER}.forEach {fighters.getOrNull(it.owner)?.let {f->f.shield=max(0f,f.shield-it.hp)}}
    devices.removeAll {it.turns<=0 && it.kind in setOf(DeviceKind.TURRET,DeviceKind.DOME,DeviceKind.DEFENDER,DeviceKind.ARC_TURRET)}
    for(f in fighters) {
        if(f.reviveIn>0) {f.reviveIn--;if(f.reviveIn==0 && f.y<waterLevel) {f.hp=30f;f.shield=0f}}
        f.lavaBounce=false
        if(!f.alive) continue
        for(h in hazards.filter {hypot(it.x-f.x,it.y-f.y)<it.radius}) {
            when(h.effect) {
                Effect.POISON->hurt(f,15f,DamageType.POISON,h.owner)
                Effect.BURN->hurt(f,25f,DamageType.FIRE,h.owner)
                Effect.CORROSION->hurt(f,12f,DamageType.ACID,h.owner)
                else->Unit
            }
            if(h.effect!=Effect.STUN) f.effects[h.effect.ordinal]=max(f.effects[h.effect.ordinal],1)
        }
    }
    for(d in devices.filter {it.kind in setOf(DeviceKind.TURRET,DeviceKind.ARC_TURRET) && it.owner in fighters.indices && fighters[it.owner].team!=active.team}) {
        val target=fighters.filter {it.alive && it.team==active.team && hypot(it.x-d.x,it.y-d.y)<800}.firstOrNull {
            val dist=hypot(it.x-d.x,it.y-d.y);terrain.ray(d.x,d.y-20,(it.x-d.x)/dist,(it.y-d.y)/dist,dist)==null
        }
        if(target!=null) {hurt(target,if(d.kind==DeviceKind.ARC_TURRET) 22f else 20f,if(d.kind==DeviceKind.ARC_TURRET) DamageType.ELECTRIC else DamageType.BULLET,d.owner);beams+=Beam(d.x,d.y-20,target.x,target.y-20)}
    }
    if(arena==Arena.VOLCANO && turn%3==0) devices.filter {it.kind==DeviceKind.GEYSER}.forEach {g->
        fighters.filter {it.alive && abs(it.x-g.x)<100}.forEach {hurt(it,30f,DamageType.FIRE);it.vy= -330f}
        hazards+=Hazard(g.x,g.y-80,95f,Effect.BURN,2,-1)
    }
    if(mode==GameMode.BOSS) {
        val b=fighters[2];bossPhase=if(b.hp<b.maxHp*.33f) 3 else if(b.hp<b.maxHp*.66f) 2 else 1
        bossWarning=when(boss) {Boss.INFERNO->"Следующая атака: метеоритный дождь";Boss.ALCHEMIST->"Следующая атака: газ и кислота";Boss.FAFNIR->"Следующая атака: дыхание и порыв ветра";Boss.GENERAL->"Следующая атака: плазма и турели"}+" • фаза $bossPhase"
    }
}

internal fun Game.bossAttack():Boolean {
    if(mode!=GameMode.BOSS || active.id!=2) return false
    bossActionCount++
    val victim=fighters.filter {it.alive && it.team==0}.minByOrNull {it.hp} ?: return false
    val w=when(boss) {Boss.INFERNO->if(bossPhase==1) Weapon.AIRSTRIKE else Weapon.METEOR;Boss.ALCHEMIST->if(bossActionCount%2==0) Weapon.ACID else Weapon.GAS;Boss.FAFNIR->Weapon.NAPALM;Boss.GENERAL->if(bossPhase>=2) Weapon.ION else Weapon.AIRSTRIKE}
    if(boss==Boss.FAFNIR) fighters.filter {it.alive && it.team==0}.forEach {it.vx-=150f*bossPhase;it.vy-=80f}
    if(boss==Boss.GENERAL && bossActionCount%2==0 && devices.count {it.kind==DeviceKind.TURRET}<4) devices+=Device(DeviceKind.TURRET,active.x-100,active.y,active.id)
    active.ammo[w]=max(1,active.ammo[w] ?: 0);weapon=w
    angle=(atan2(victim.y-active.y,victim.x-active.x)*180/PI).toFloat()
    if(w.kind==Kind.GLOBAL) return fire(victim.x,victim.y)
    shots+=Shot(victim.x-50,300f,40f,180f,w,active.id,if(w==Weapon.GAS) 1.5f else 0f)
    active.ammo[w]=(active.ammo.getValue(w)-1).coerceAtLeast(0);startContentAttack();return true
}
