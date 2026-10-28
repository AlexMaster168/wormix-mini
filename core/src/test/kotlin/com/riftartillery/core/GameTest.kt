package com.riftartillery.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException

class GameTest {
    private fun settle(g:Game) { repeat(100) {g.update()} }
    private fun advanceToNext(g:Game) { val turn=g.turn;repeat(1200) {if(g.turn==turn && g.phase!=Phase.ENDED) g.update()};assertTrue("Turn must resolve",g.turn>turn || g.phase==Phase.ENDED) }
    @Test fun terrainSeedIsReproducible() {
        assertArrayEquals(Terrain(Arena.NEON,3).solid,Terrain(Arena.NEON,3).solid)
        assertFalse(Terrain(Arena.NEON,3).solid.contentEquals(Terrain(Arena.NEON,4).solid))
    }
    @Test fun craterRemovesOnlyCircleAndUpdatesRevision() {
        val t=Terrain(Arena.NEON,4);assertTrue(t.at(200f,1250f))
        t.carve(200f,1250f,30f)
        assertFalse(t.at(200f,1250f));assertTrue(t.at(240f,1250f));assertEquals(1,t.revision)
        t.carve(-20f,-20f,100f);t.carve(1450f,820f,100f)
    }
    @Test fun outsideWorldIsNeverSolid() {
        val t=Terrain(Arena.ORBIT,0)
        assertFalse(t.at(-.1f,600f));assertFalse(t.at(WORLD_W.toFloat(),600f));assertFalse(t.at(10f,-1f))
    }
    @Test fun allArenasSpawnLivingGroundedTeams() {
        for(a in Arena.entries) for(seed in 0..8) {
            val g=Game(a,false,seed);settle(g)
            assertEquals(4,g.fighters.count {it.alive})
            assertTrue(g.fighters.all {it.grounded})
            assertTrue(g.fighters.all {it.y<WATER_Y-20})
        }
    }
    @Test fun timeoutAlternatesTeamsAndRotatesFighters() {
        val g=Game(againstBot=false)
        val ids=mutableListOf(g.active.id)
        repeat(4) {g.skip();ids+=g.active.id}
        assertEquals(listOf(0,2,1,3,0),ids)
    }
    @Test fun realTimeoutAdvancesTurn() {
        val g=Game(againstBot=false);repeat(1802) {g.update()}
        assertEquals(2,g.turn);assertEquals(1,g.active.team)
    }
    @Test fun oneAttackPerTurnAndAmmoConsumedOnce() {
        val g=Game(againstBot=false);g.select(Weapon.GRENADE)
        assertTrue(g.fire());assertFalse(g.fire())
        assertEquals(4,g.active.ammo[Weapon.GRENADE])
        advanceToNext(g)
    }
    @Test fun emptyAmmoRejectsWithoutConsumingTurn() {
        val g=Game();g.active.ammo[Weapon.CLUSTER]=0;g.select(Weapon.CLUSTER)
        assertFalse(g.fire());assertEquals(Phase.AIM,g.phase)
    }
    @Test fun fullHealthDoesNotWasteMedkit() {
        val g=Game();g.select(Weapon.HEAL)
        assertFalse(g.fire());assertEquals(2,g.active.ammo[Weapon.HEAL])
        g.active.hp=65f;assertTrue(g.fire());assertEquals(100f,g.active.hp,.01f)
    }
    @Test fun teleportRejectsGroundWaterAndOccupiedLocations() {
        val g=Game();g.select(Weapon.TELEPORT)
        assertFalse(g.fire(200f,1280f));assertFalse(g.fire(1440f,WATER_Y+5))
        assertFalse(g.fire(g.fighters[1].x,g.fighters[1].y))
        assertEquals(2,g.active.ammo[Weapon.TELEPORT])
        assertTrue(g.fire(500f,220f));assertEquals(500f,g.active.x,.01f)
        assertEquals(1,g.active.ammo[Weapon.TELEPORT])
    }
    @Test fun ropeAttachesDetachesAndPreservesVelocity() {
        val g=Game();settle(g)
        g.active.x=600f;g.active.y=750f;g.angle=-90f
        assertTrue(g.rope());assertNotNull(g.anchor)
        g.active.vx=84f;assertTrue(g.rope());assertNull(g.anchor);assertEquals(84f,g.active.vx,0f)
    }
    @Test fun destroyingRopeAnchorReleasesRope() {
        val g=Game();g.active.x=600f;g.active.y=750f;g.angle=-90f
        assertTrue(g.rope());val a=g.anchor!!
        g.terrain.carve(a.x,a.y,30f);g.update();assertNull(g.anchor)
    }
    @Test fun ropeMissDoesNotCreateAnchor() {
        val g=Game();g.angle=-90f;assertFalse(g.rope());assertNull(g.anchor)
    }
    @Test fun raceAbilityIsOnlyUsableOncePerBattle() {
        val g=Game(againstBot=false);g.active.hp=60f;g.ability();assertTrue(g.active.abilityUsed)
        g.active.vy=0f;g.ability();assertEquals(0f,g.active.vy,0f)
        g.skip();assertEquals(Race.CYBORG,g.active.race)
        g.ability();assertEquals(30f,g.active.shield,0f)
    }
    @Test fun jumpRequiresGroundContact() {
        val g=Game();settle(g);g.jump();assertTrue(g.active.vy<0)
        g.active.vy=-20f;g.jump();assertEquals(-20f,g.active.vy,0f)
    }
    @Test fun waterEliminatesAndCanEndGame() {
        val g=Game()
        g.fighters.filter {it.team==1}.forEach {it.x=1440f;it.y=WATER_Y+10}
        g.update();assertEquals(Phase.ENDED,g.phase);assertEquals(0,g.winner)
    }
    @Test fun simultaneousEliminationIsDraw() {
        val g=Game();g.fighters.forEach {it.hp=0f};g.update()
        assertEquals(Phase.ENDED,g.phase);assertEquals(-1,g.winner)
    }
    @Test fun sniperDamagesEnemyAndShieldAbsorbsDamage() {
        val g=Game(againstBot=false);g.active.x=200f;g.active.y=200f
        val enemy=g.fighters[2];enemy.x=350f;enemy.y=195f;enemy.shield=30f
        g.select(Weapon.SNIPER);g.angle=0f;assertTrue(g.fire())
        assertEquals(93.25f,enemy.hp,.1f);assertEquals(0f,enemy.shield,0f)
        assertTrue(g.beams.isNotEmpty())
    }
    @Test fun sniperCannotShootThroughTerrain() {
        val g=Game();g.active.x=400f;g.active.y=640f
        val enemy=g.fighters[2];enemy.x=900f;enemy.y=635f
        g.select(Weapon.SNIPER);g.angle=0f;g.fire();assertEquals(110f,enemy.hp,0f)
    }
    @Test fun explosionCarvesTerrainAndAppliesDamage() {
        val g=Game(againstBot=false);settle(g)
        val before=g.terrain.solid.count {it};g.select(Weapon.DYNAMITE);assertTrue(g.fire())
        repeat(320) {g.update()}
        assertTrue(g.terrain.solid.count {it}<before)
        assertTrue(g.fighters[0].hp<100f)
    }
    @Test fun dynamiteAllowsEscapeBeforeDetonation() {
        val g=Game(againstBot=false);settle(g)
        val before=g.active.x
        g.select(Weapon.DYNAMITE);g.fire()
        assertTrue(g.canControl);assertEquals(3f,g.retreatLeft,0f)
        g.move=1f;repeat(120) {g.update()};g.move=0f
        assertTrue(g.active.x>before+100)
        assertTrue(g.shots.isNotEmpty())
        assertTrue(g.canControl)
        repeat(62) {g.update()}
        assertFalse(g.canControl)
        assertEquals(Phase.FLIGHT,g.phase)
    }
    @Test fun clusterSpawnsFiveIndependentFragments() {
        val g=Game();g.active.x=690f;g.active.y=200f;g.select(Weapon.CLUSTER);g.power=.8f;g.angle=-90f;g.fire()
        var sawFragments=false
        repeat(200) {g.update();if(g.shots.count {it.fragment}==5) sawFragments=true}
        assertTrue(sawFragments)
    }
    @Test fun botShootsAndHandsControlBack() {
        val g=Game(againstBot=true);g.skip();var shot=false
        repeat(1000) {if(g.phase!=Phase.ENDED) {g.update();if(g.shots.isNotEmpty()) shot=true}}
        assertTrue(shot);assertTrue(g.humanTurn || g.phase==Phase.ENDED)
    }
    @Test fun deterministicInputsProduceIdenticalBattles() {
        val a=Game(seed=17);val b=Game(seed=17)
        a.fire();b.fire();repeat(1400) {a.update();b.update()}
        assertEquals(a.turn,b.turn)
        a.fighters.zip(b.fighters).forEach {(x,y)->assertEquals(x.x,y.x,0f);assertEquals(x.y,y.y,0f);assertEquals(x.hp,y.hp,0f)}
        assertArrayEquals(a.terrain.solid,b.terrain.solid)
    }
    @Test fun longSimulationRemainsFiniteAndBounded() {
        for(a in Arena.entries) {
            val g=Game(a,false,100)
            repeat(12000) {
                if(g.phase==Phase.AIM) {g.angle=if(g.active.team==0) -35f else -145f;g.fire()}
                g.update()
                assertTrue(g.fighters.all {it.x.isFinite() && it.y.isFinite() && it.hp>=0 && it.hp<=it.race.hp})
                assertTrue(g.particles.size<=650)
            }
        }
    }
    @Test fun orbitalGravityProducesSlowerFall() {
        val a=Game(Arena.NEON);val b=Game(Arena.ORBIT)
        a.active.y=200f;b.active.y=200f
        repeat(30) {a.update();b.update()}
        assertTrue(a.active.y>b.active.y)
    }
    @Test fun fightersDoNotOverlapAfterKnockback() {
        val g=Game(againstBot=false)
        g.fighters[0].x=250f;g.fighters[0].y=220f
        g.fighters[1].x=260f;g.fighters[1].y=220f
        g.update()
        assertTrue(abs(g.fighters[0].x-g.fighters[1].x)>=34f)
    }
    @Test fun fighterCanStandOnAnotherWithoutFallingThrough() {
        val g=Game(againstBot=false)
        g.fighters[0].x=250f;g.fighters[0].y=200f
        g.fighters[1].x=250f;g.fighters[1].y=275f
        g.update()
        assertTrue(abs(g.fighters[0].y-g.fighters[1].y)>=80)
        assertTrue(g.fighters[0].grounded)
    }
    @Test fun movementWorksFromSpawnOnEveryArena() {
        for(a in Arena.entries) for(seed in 0..8) {
            val g=Game(a,false,seed);settle(g)
            val before=g.active.x;g.move=1f
            repeat(30) {g.update()}
            assertTrue("Spawn must permit walking: $a/$seed",g.active.x>before+10)
        }
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsVariableOversizedTick() {Game().update(.5f)}

    private fun restored(g:Game):Game {
        val out=ByteArrayOutputStream();g.save(out)
        assertTrue("Save mask is bit-packed",out.size()<600000)
        return Game.load(ByteArrayInputStream(out.toByteArray()))
    }
    @Test fun saveRestoresFlightAndFutureSimulationExactly() {
        val a=Game(seed=12);a.select(Weapon.CLUSTER);a.fire();repeat(40) {a.update()}
        val b=restored(a)
        assertEquals(a.shots.size,b.shots.size)
        assertEquals(a.phase,b.phase);assertEquals(a.active.ammo,b.active.ammo)
        repeat(1000) {a.update();b.update()}
        a.fighters.zip(b.fighters).forEach {(x,y)->assertEquals(x.x,y.x,0f);assertEquals(x.hp,y.hp,0f);assertEquals(x.y,y.y,0f)}
        assertEquals(a.wind,b.wind,0f);assertEquals(a.turn,b.turn)
        assertArrayEquals(a.terrain.solid,b.terrain.solid)
    }
    @Test fun saveRestoresCraterRopeShieldAndInventory() {
        val a=Game(againstBot=false)
        a.terrain.carve(900f,1280f,80f)
        a.active.x=600f;a.active.y=750f;a.angle=-90f;a.rope()
        a.active.shield=25f;a.active.abilityUsed=true;a.active.ammo[Weapon.GRENADE]=2
        val b=restored(a)
        assertEquals(a.anchor,b.anchor);assertEquals(25f,b.active.shield,0f)
        assertTrue(b.active.abilityUsed);assertEquals(2,b.active.ammo[Weapon.GRENADE])
        assertFalse(b.terrain.at(900f,1280f))
    }
    @Test(expected=IllegalArgumentException::class) fun saveRejectsForeignFormat() {
        Game.load(ByteArrayInputStream(ByteArray(200)))
    }
    @Test(expected=EOFException::class) fun saveRejectsTruncatedData() {
        val out=ByteArrayOutputStream();Game().save(out)
        Game.load(ByteArrayInputStream(out.toByteArray().copyOf(100)))
    }
    @Test(expected=IllegalArgumentException::class) fun saveRejectsUnknownVersion() {
        val out=ByteArrayOutputStream();Game().save(out)
        val bytes=out.toByteArray();bytes[7]=99
        Game.load(ByteArrayInputStream(bytes))
    }
}
