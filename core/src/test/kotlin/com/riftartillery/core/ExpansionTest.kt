package com.riftartillery.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class ExpansionTest {
    private fun empty():Game {
        val g=Game(againstBot=false)
        g.terrain.solid.fill(false);g.devices.clear()
        g.fighters.forEach {it.x=2200f+it.id*100;it.y=600f}
        g.active.x=300f;g.active.y=600f;g.angle=0f;g.power=1f
        return g
    }
    @Test fun plasmaChargeTravelsHitsAndConsumesOneRound() {
        val g=empty();val target=g.fighters[2];target.x=510f
        g.select(Weapon.PLASMA_RIFLE);assertTrue(g.fire())
        assertEquals(3,g.active.ammo[Weapon.PLASMA_RIFLE])
        assertEquals(Weapon.PLASMA_RIFLE,g.shots.single().weapon)
        val hp=target.hp
        repeat(15) {g.update()}
        assertTrue("Plasma impact damages target",target.hp<hp)
        assertTrue(g.shots.isEmpty())
    }
    @Test fun clusterRocketProducesSixBoundedIndependentFragments() {
        val g=empty();g.explode(Shot(900f,600f,0f,0f,Weapon.CLUSTER_ROCKET,0,0f))
        assertEquals(6,g.shots.size);assertTrue(g.shots.all {it.fragment && it.weapon==Weapon.GRENADE})
        assertEquals(6,g.shots.map {it.vx}.distinct().size)
        repeat(200) {g.update()};assertTrue(g.shots.isEmpty())
    }
    @Test fun singularityPullsBothSidesTowardItsExplosion() {
        val g=empty();val left=g.fighters[1];val right=g.fighters[2]
        left.x=840f;right.x=960f
        g.explode(Shot(900f,576f,0f,0f,Weapon.VORTEX,0,0f))
        assertTrue(left.vx>0);assertTrue(right.vx<0)
        assertTrue(left.hp<left.maxHp && right.hp<right.maxHp)
        assertEquals(100,Weapon.entries.size)
    }
    @Test fun versionTwoSaveRetainsStateAndReceivesNewAmmo() {
        val g=javaClass.getResourceAsStream("/save-v2.bin")!!.use {Game.load(it)}
        assertTrue(g.fighters.all {it.x.isFinite() && it.hp in 0f..it.maxHp})
        for(f in g.fighters) for(w in listOf(Weapon.PLASMA_RIFLE,Weapon.CLUSTER_ROCKET,Weapon.VORTEX)) {
            assertEquals(w.stock,f.ammo[w])
        }
        g.fighters[0].ammo[Weapon.VORTEX]=1
        val out=ByteArrayOutputStream();g.save(out)
        val restored=Game.load(ByteArrayInputStream(out.toByteArray()))
        assertEquals(g.arena,restored.arena);assertEquals(g.turn,restored.turn)
        assertEquals(g.timeLeft,restored.timeLeft,0f);assertEquals(g.weapon,restored.weapon)
        assertEquals(g.fighters[0].ammo,restored.fighters[0].ammo)
        assertArrayEquals(g.terrain.solid,restored.terrain.solid)
    }
    @Test fun newArenasHaveDistinctTerrainAndPreserveItInSaves() {
        val canyon=Game(Arena.CANYON,false,17);val harbor=Game(Arena.HARBOR,false,17)
        assertFalse(canyon.terrain.solid.contentEquals(harbor.terrain.solid))
        assertTrue(harbor.terrain.at(1440f,harbor.terrain.surface(1360f)+8))
        for(g in listOf(canyon,harbor)) {
            val out=ByteArrayOutputStream();g.save(out)
            val restored=Game.load(ByteArrayInputStream(out.toByteArray()))
            assertEquals(g.arena,restored.arena);assertArrayEquals(g.terrain.solid,restored.terrain.solid)
        }
    }
}
