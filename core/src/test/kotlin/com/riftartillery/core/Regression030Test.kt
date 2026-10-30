package com.riftartillery.core

import java.io.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class Regression030Test {
    private fun flat():Game {
        val g=Game(againstBot=false,squad=Loadout(Race.MERCENARY))
        g.terrain.solid.fill(false);g.terrain.armor.fill(0);g.devices.clear()
        for(y in 1000 until WORLD_H) g.terrain.solid.fill(true,y*WORLD_W,(y+1)*WORLD_W)
        g.fighters.forEach {it.x=2100f+it.id*100;it.y=982f;it.vx=0f;it.vy=0f;it.grounded=true}
        g.active.x=300f
        return g
    }
    private fun roundTrip(g:Game):Game {val out=ByteArrayOutputStream();g.save(out);return Game.load(ByteArrayInputStream(out.toByteArray()))}
    @Test fun walkingDoesNotPushAlliesOrEnemiesInEitherDirection() {
        for(id in listOf(1,2)) for(direction in listOf(-1f,1f)) {
            val g=flat();val other=g.fighters[id];other.x=300+direction*60;val start=other.x
            g.move=direction;repeat(240) {g.update()}
            assertEquals("Stationary unit stays put: $id/$direction",start,other.x,.01f)
            assertTrue("Walked completely past unit: $id/$direction",(g.active.x-other.x)*direction>100)
            assertTrue(other.grounded && other.alive)
        }
    }
    @Test fun walkingOutOfOverlapDoesNotMoveTheOtherUnit() {
        val g=flat();g.fighters[1].x=330f;g.move=1f
        repeat(60) {g.update()};assertEquals(330f,g.fighters[1].x,.01f)
        assertTrue(g.active.x>400)
    }
    @Test fun stoppingInsideAnotherUnitDoesNotPushEitherFighter() {
        val g=flat();val other=g.fighters[1];other.x=330f
        g.move=1f;repeat(12) {g.update()};g.move=0f;repeat(90) {g.update()}
        assertEquals(330f,other.x,.01f);assertTrue(abs(g.active.x-other.x)<34)
        val x=g.active.x;repeat(90) {g.update()};assertEquals(x,g.active.x,.01f)
        g.move=-1f;repeat(45) {g.update()};assertTrue(g.active.x<other.x-50);assertEquals(330f,other.x,.01f)
    }
    @Test fun medkitDoesNotConsumeTurnAndAttackRemainsAvailable() {
        val g=flat();repeat(80) {g.update()};g.active.hp=30f
        g.active.effects[Effect.POISON.ordinal]=2;val time=g.timeLeft;val turn=g.turn
        g.select(Weapon.HEAL);assertTrue(g.fire());assertEquals(80f,g.active.hp,.01f)
        assertEquals(1,g.active.ammo[Weapon.HEAL]);assertEquals(Phase.AIM,g.phase)
        assertEquals(time,g.timeLeft,0f);assertEquals(turn,g.turn);assertTrue(g.active.effects.all {it==0})
        g.select(Weapon.ROCKET);assertTrue(g.fire());assertFalse(g.fire())
    }
    @Test fun unnecessaryMedkitDoesNotConsumeStock() {
        val g=flat();g.select(Weapon.HEAL);assertFalse(g.fire())
        assertEquals(2,g.active.ammo[Weapon.HEAL]);assertEquals(Phase.AIM,g.phase)
    }
    @Test fun teleportDoesNotConsumeTurnAndCanBeFollowedByAttack() {
        val g=flat();val time=g.timeLeft;g.select(Weapon.TELEPORT)
        assertTrue(g.fire(900f,700f));assertEquals(900f,g.active.x,0f)
        assertEquals(1,g.active.ammo[Weapon.TELEPORT]);assertEquals(Phase.AIM,g.phase)
        assertEquals(time,g.timeLeft,0f);g.select(Weapon.ROCKET);assertTrue(g.fire())
    }
    @Test fun invalidTeleportStillRejectsGroundUnitsAndRaisedWater() {
        val g=flat();g.select(Weapon.TELEPORT)
        assertFalse(g.fire(500f,1050f));assertFalse(g.fire(g.fighters[1].x,g.fighters[1].y))
        g.waterLevel=800f;assertFalse(g.fire(500f,810f))
        assertEquals(2,g.active.ammo[Weapon.TELEPORT]);assertEquals(Phase.AIM,g.phase)
    }
    @Test fun jetboardCanFlyHoverAndPersistWithoutSpendingAttack() {
        val g=flat();g.select(Weapon.JETBOARD);assertTrue(g.fire());assertEquals(Phase.AIM,g.phase)
        assertTrue(g.active.jetboard);assertEquals(160f,g.active.fuel,0f)
        g.reel=-1f;g.move=1f;val y=g.active.y
        repeat(35) {g.update()};assertTrue(g.active.y<y-60);assertTrue(g.active.x>390)
        val restored=roundTrip(g);assertTrue(restored.active.jetboard)
        assertEquals(g.active.fuel,restored.active.fuel,0f)
        g.reel=0f;g.move=0f;repeat(120) {g.update()};assertTrue(abs(g.active.vy)<10)
    }
    @Test fun watermelonAndFragmentGrenadesCreateTheirDocumentedPayloads() {
        for((w,count) in listOf(Weapon.WATERMELON to 8,Weapon.PINEAPPLE to 10,Weapon.FRAG_GRENADE to 12,Weapon.FIREWORKS to 9)) {
            val g=flat();g.explode(Shot(1000f,500f,0f,0f,w,0,0f))
            assertEquals(w.title,count,g.shots.size);assertTrue(g.shots.all {it.fragment})
            repeat(240) {g.update()};assertTrue(g.shots.isEmpty())
        }
    }
    @Test fun elementalClustersProduceDistinctPayloads() {
        for((w,payload) in listOf(Weapon.ACID_CLUSTER to Weapon.ACID,Weapon.FIRE_CLUSTER to Weapon.MOLOTOV,Weapon.ICE_CLUSTER to Weapon.CRYO)) {
            val g=flat();g.explode(Shot(1000f,500f,0f,0f,w,0,0f))
            assertEquals(5,g.shots.size);assertTrue(g.shots.all {it.weapon==payload})
        }
    }
    @Test fun vampireHealsOnlyFromEnemyDamage() {
        val g=flat();g.active.hp=20f;g.fighters[2].x=800f
        g.explode(Shot(800f,960f,0f,0f,Weapon.VAMPIRE,0,0f));assertTrue(g.active.hp>20)
        val hp=g.active.hp;g.fighters[1].x=1100f
        g.explode(Shot(1100f,960f,0f,0f,Weapon.VAMPIRE,0,0f));assertEquals(hp,g.active.hp,0f)
    }
    @Test fun empRemovesProtectionAndDisablesNearbyTurret() {
        val g=flat();g.fighters[2].x=800f;g.fighters[2].shield=100f
        val turret=Device(DeviceKind.TURRET,800f,950f,2);g.devices+=turret
        g.explode(Shot(800f,960f,0f,0f,Weapon.EMP_GRENADE,0,0f))
        assertEquals(0f,g.fighters[2].shield,0f);assertTrue(turret.hp<=0)
    }
    @Test fun smokePreservesTerrainAndLeavesBlindingCloud() {
        val g=flat();val before=g.terrain.solid.copyOf()
        g.explode(Shot(800f,990f,0f,0f,Weapon.SMOKE,0,0f))
        assertArrayEquals(before,g.terrain.solid);assertTrue(g.hazards.any {it.effect==Effect.BLIND && it.turns==2})
    }
    @Test fun tripleAndGuidedSwarmFireExactlyThreeShots() {
        for(w in listOf(Weapon.TRIPLE_ROCKET,Weapon.SEEKER_SWARM)) {
            val g=flat();g.select(w);assertTrue(g.fire(if(w.targeted) 1500f else null,if(w.targeted) 700f else null))
            assertEquals(3,g.shots.size);assertEquals(1,g.active.ammo[w])
        }
    }
    @Test fun bouncingMineDeploysRealMineRatherThanOnlyExploding() {
        val g=flat();g.explode(Shot(800f,980f,0f,0f,Weapon.BOUNCING_MINE,0,0f))
        assertTrue(g.devices.any {it.kind==DeviceKind.MINE && it.owner==0})
    }
    @Test fun actualVersionThreeSaveMigratesAndReceivesNewInventory() {
        val g=javaClass.getResourceAsStream("/save-v3.bin")!!.use {Game.load(it)}
        assertFalse(g.active.jetboard)
        for(w in Weapon.entries.drop(60)) assertEquals(w.stock,g.active.ammo[w])
        assertEquals(g.active.ammo,roundTrip(g).active.ammo)
    }
    @Test fun musicLibraryContainsEveryArenaAndFourManualBonusTracks() {
        assertEquals(16,MusicTrack.entries.size)
        Arena.entries.forEach {assertEquals(it.name.lowercase(),MusicTrack.entries[it.ordinal].asset)}
        assertEquals(16,MusicTrack.entries.map {it.asset}.distinct().size)
    }
}
