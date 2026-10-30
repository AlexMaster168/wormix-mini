package com.riftartillery.core

import org.junit.Assert.*
import org.junit.Test
import java.io.*

class Expansion040Test {
    private fun game():Game {
        val g=Game(againstBot=false);g.devices.clear();g.terrain.solid.fill(false);g.terrain.armor.fill(0)
        for(y in 1000 until WORLD_H) g.terrain.solid.fill(true,y*WORLD_W,(y+1)*WORLD_W)
        g.fighters.forEach {it.x=300f+it.id*600;it.y=982f;it.grounded=true};return g
    }
    @Test fun everyNewWeaponFiresAndConsumesExactlyOneRound() {
        assertEquals(120,Weapon.entries.size)
        for(w in Weapon.entries.drop(100)) {
            val g=game();g.select(w);val before=g.active.ammo.getValue(w)
            assertTrue(w.name,g.fire(if(w.targeted) 1100f else null,if(w.targeted) 700f else null))
            assertEquals(w.name,before-1,g.active.ammo.getValue(w))
            val data=ByteArrayOutputStream();g.save(data);val restored=Game.load(ByteArrayInputStream(data.toByteArray()))
            assertEquals(w,restored.weapon);assertEquals(g.active.ammo,restored.active.ammo)
            repeat(120) {restored.update()};assertTrue(restored.fighters.all {it.x.isFinite() && it.y.isFinite()})
        }
    }
    @Test fun shopRejectsUnaffordableInvalidAndExcessPurchases() {
        assertNull(Market.buy(0,"",0));assertNull(Market.buy(1000,"",-1));assertNull(Market.buy(1000,"",999))
        var balance=1000;var stock=""
        repeat(9) {val p=Market.buy(balance,stock,0)!!;balance=p.balance;stock=p.inventory}
        assertNull(Market.buy(balance,stock,0));assertEquals(730,balance);assertEquals(9,Market.decode(stock).packs[0])
    }
    @Test fun cosmeticsArePermanentAndCannotBeBoughtTwice() {
        val aura=Market.offers.first {it.aura==2};val p=Market.buy(150,"",aura.id)!!
        assertEquals(100,p.balance);assertEquals(2,Market.decode(p.inventory).selected)
        assertNull(Market.buy(p.balance,p.inventory,aura.id));assertNull(Market.equip(p.inventory,3))
        assertEquals(0,Market.decode(Market.equip(p.inventory,0)!!).selected)
    }
    @Test fun purchasedPackIsAppliedOnceAndConsumedOnlyOnNewBattle() {
        val p=Market.buy(150,"",0)!!;val g=game();val before=g.active.ammo.getValue(Weapon.AK47)
        val stock=Market.apply(g,p.inventory);assertEquals(before+3,g.active.ammo[Weapon.AK47]);assertEquals(0,Market.decode(stock).packs[0])
        Market.apply(g,stock);assertEquals(before+3,g.active.ammo[Weapon.AK47])
        val bytes=ByteArrayOutputStream();g.save(bytes);val restored=Game.load(ByteArrayInputStream(bytes.toByteArray()))
        assertEquals(before+3,restored.active.ammo[Weapon.AK47])
    }
    @Test fun packsDoNotBuffBotAndAreNotSpentInRace() {
        val p=Market.buy(150,"",0)!!;val bot=Game(againstBot=true);Market.apply(bot,p.inventory)
        assertEquals(Weapon.AK47.stock,bot.fighters[2].ammo[Weapon.AK47])
        val race=Game(mode=GameMode.ROPE_RACE);assertEquals(1,Market.decode(Market.apply(race,p.inventory)).packs[0])
    }
    @Test fun malformedShopDataHasSafeBoundsAndDefaultAura() {
        for(text in listOf("", "garbage", "1;9999;90;-1", "x".repeat(3000))) {val stock=Market.decode(text);assertEquals(1,stock.owned);assertEquals(0,stock.packs.sum())}
        val stock=Market.decode(Market.Stock().encode());assertEquals(Market.offers.size,stock.packs.size)
        assertEquals(20,Market.reward(GameMode.DUEL,1));assertEquals(180,Market.reward(GameMode.BOSS,0))
    }
    @Test fun allCombatMapsHaveInteractiveDevicesAndRaceDoesNot() {
        for(a in Arena.entries) {val g=Game(a,false);assertEquals(7,g.devices.count {it.kind.interactive});assertTrue(g.devices.all {it.x in 0f..WORLD_W.toFloat()});assertTrue(g.devices.filter {it.kind.interactive}.all {it.y<g.waterLevel-100})}
        assertTrue(Game(mode=GameMode.ROPE_RACE).devices.none {it.kind.interactive})
    }
    @Test fun ammoCrateIsSingleUseAndDoesNotSpendAttack() {
        val g=game();g.select(Weapon.GRENADE);val time=g.timeLeft;g.devices+=Device(DeviceKind.AMMO_CRATE,330f,982f,-1,70f,0f)
        assertTrue(g.interact());assertEquals(7,g.active.ammo[Weapon.GRENADE]);assertFalse(g.interact());assertEquals(Phase.AIM,g.phase);assertEquals(time,g.timeLeft,0f)
    }
    @Test fun medicalStationChecksNeedAndChargesPersist() {
        val g=game();val d=Device(DeviceKind.MED_STATION,330f,982f,-1,80f,0f,3);g.devices+=d
        assertFalse(g.interact());assertEquals(3,d.turns);g.active.hp=10f;assertTrue(g.interact());assertEquals(40f,g.active.hp,0f);assertEquals(2,d.turns);assertFalse(g.interact())
        val bytes=ByteArrayOutputStream();g.save(bytes);val restored=Game.load(ByteArrayInputStream(bytes.toByteArray()));assertEquals(2,restored.devices.single().turns);assertEquals(3f,restored.devices.single().timer,0f)
    }
    @Test fun portalsMovePlayerAndPreventImmediateLoop() {
        val g=game();g.devices+=Device(DeviceKind.PORTAL,330f,982f,-1,70f,0f);g.devices+=Device(DeviceKind.PORTAL,1200f,982f,-1,70f,0f)
        assertTrue(g.interact());assertEquals(1200f,g.active.x,0f);assertFalse(g.interact());assertEquals(Phase.AIM,g.phase)
    }
    @Test fun jumpPadLaunchesPlayerWithParachuteAndCooldown() {
        val g=game();g.devices+=Device(DeviceKind.JUMP_PAD,330f,982f,-1,70f,0f)
        assertTrue(g.interact());assertEquals(-650f,g.active.vy,0f);assertTrue(g.active.parachute);assertFalse(g.interact())
        repeat(15) {g.update()};assertTrue(g.active.y<900)
    }
    @Test fun switchBuildsAndRemovesCentralBridge() {
        val g=game();g.terrain.carve(1440f,1010f,160f);g.devices+=Device(DeviceKind.BRIDGE_SWITCH,330f,982f,-1,70f,0f)
        assertFalse(g.terrain.at(1440f,1010f));assertTrue(g.interact());assertTrue(g.terrain.at(1440f,1010f))
        g.devices.single().timer=0f;assertTrue(g.interact());assertFalse(g.terrain.at(1440f,1010f))
    }
    @Test fun healingGrenadeOnlyHealsAlliesAndNeverCarves() {
        val g=game();g.fighters[1].x=350f;g.fighters[2].x=380f;g.fighters.forEach {it.hp=20f;it.effects[0]=2};val rev=g.terrain.revision
        g.explode(Shot(340f,960f,0f,0f,Weapon.HEAL_GRENADE,0,0f))
        assertEquals(55f,g.active.hp,0f);assertEquals(55f,g.fighters[1].hp,0f);assertEquals(20f,g.fighters[2].hp,0f);assertEquals(0,g.active.effects[0]);assertEquals(rev,g.terrain.revision)
    }
    @Test fun newAreaWeaponsHaveDistinctPayloads() {
        val g=game();g.explode(Shot(1100f,500f,0f,0f,Weapon.ICE_SHARDS,0,0f));assertEquals(7,g.shots.size);assertTrue(g.shots.all {it.weapon==Weapon.CRYO})
        g.explode(Shot(1100f,500f,0f,0f,Weapon.BLACK_HOLE,0,0f));assertTrue(g.hazards.any {it.effect==Effect.STUN})
        val drone=game();drone.select(Weapon.DRONE_STRIKE);assertTrue(drone.fire(1200f,500f));assertEquals(5,drone.shots.size)
    }
    @Test fun mirrorLaserReflectsAndLeavesTerrainIntact() {
        val g=game();for(x in 500..520) for(y in 700..999) g.terrain.solid[y*WORLD_W+x]=true
        g.angle=0f;g.select(Weapon.MIRROR_GUN);val revision=g.terrain.revision;assertTrue(g.fire());assertTrue(g.beams.size>=2);assertEquals(revision,g.terrain.revision)
    }
}
