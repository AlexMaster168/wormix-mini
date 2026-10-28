package com.riftartillery.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class MovementTest {
    private fun flat(arena:Arena=Arena.NEON,race:Race=Race.MERCENARY):Game {
        val g=Game(arena,false,41,squad=Loadout(race))
        g.terrain.solid.fill(false);g.terrain.armor.fill(0)
        for(y in 1000 until WORLD_H) g.terrain.solid.fill(true,y*WORLD_W,(y+1)*WORLD_W)
        g.fighters.forEach {it.x=2100f+it.id*120;it.y=982f;it.vx=0f;it.vy=0f;it.grounded=true}
        g.active.x=300f
        return g
    }
    @Test fun turnLastsFiftyNineSecondsAndAllySwitchDoesNotRefillIt() {
        val g=flat();assertEquals(59f,g.timeLeft,0f)
        repeat(1800) {g.update()};assertEquals(1,g.turn);assertTrue(g.timeLeft in 28.9f..29.1f)
        val before=g.timeLeft;assertTrue(g.switchFighter(1));assertEquals(before,g.timeLeft,0f)
        repeat(1742) {g.update()};assertEquals(2,g.turn);assertTrue(g.timeLeft>58)
    }
    @Test fun ordinaryJumpIsShortForwardAndDoesNotHurtOnLanding() {
        for(arena in listOf(Arena.NEON,Arena.ORBIT)) {
            val g=flat(arena);val x=g.active.x;val y=g.active.y;val hp=g.active.hp
            g.jump();var top=y;var frames=0
            do {g.update();top=min(top,g.active.y);frames++} while(!g.active.grounded && frames<150)
            assertTrue("Jump lands promptly in $arena: $frames frames",frames in 45..85)
            assertTrue("Predictable jump height",y-top in 85f..100f)
            assertTrue("Standing jump moves forward",g.active.x-x in 100f..210f)
            assertEquals(hp,g.active.hp,0f)
        }
    }
    @Test fun jumpUsesWalkingDirectionAndCannotJumpAgainInAir() {
        val g=flat();g.move=-1f;g.jump();assertTrue(g.active.vx<0)
        g.update();val vy=g.active.vy;g.jump();assertEquals(vy,g.active.vy,0f)
    }
    @Test fun uphillJumpRetainsForwardImpulseInBothDirections() {
        for(direction in listOf(-1f,1f)) {
            val g=flat();g.terrain.solid.fill(false)
            for(x in 0 until WORLD_W) {
                val surface=(1000-(x-300)*direction*.3f).toInt().coerceIn(100,WORLD_H-1)
                for(y in surface until WORLD_H) g.terrain.solid[y*WORLD_W+x]=true
            }
            g.active.y=978f;g.move=direction;g.jump();g.update()
            assertTrue("Uphill jump keeps moving: $direction",(g.active.x-300)*direction>2f)
            assertTrue(g.active.vy<0)
        }
    }
    @Test fun jumpImmediatelyBeforeLandingIsBuffered() {
        val g=flat();g.active.y=973f;g.active.vy=160f;g.active.grounded=false
        g.jump();repeat(8) {g.update()}
        assertTrue("Early tap should launch after landing",g.active.vy< -200)
    }
    @Test fun jumpStillWorksForAFewFramesAfterLeavingEdge() {
        val g=flat()
        for(y in 1000 until WORLD_H) g.terrain.solid.fill(false,y*WORLD_W+360,(y+1)*WORLD_W)
        g.active.x=365f;g.move=1f
        g.update();repeat(3) {g.update()};assertFalse(g.active.grounded)
        g.jump();assertTrue("Coyote-time jump",g.active.vy< -300)
    }
    @Test fun groundRoutesHaveNoTallSupportWallsAndCavesFitAWholeFighter() {
        for(arena in Arena.entries) for(seed in 0..4) {
            val t=Terrain(arena,seed)
            for(x in 100 until WORLD_W-100 step 20) {
                assertFalse("No support wall at $arena/$seed/$x",t.at(x.toFloat(),850f))
                if(arena!=Arena.ORBIT && x in 500..2560 && x !in 1400..1480) {
                    assertTrue("Full-height cave at $arena/$x",(1150..1248).none {t.at(x.toFloat(),it.toFloat())})
                }
            }
            for(x in listOf(980f,1410f,2180f)) if(arena==Arena.ORBIT || x==1410f) {
                assertTrue("Gap remains within ordinary jump range",(0..90).any {t.at(x+it,1100f)})
            }
        }
    }
    @Test fun everySpawnCanWalkFartherThanTheOldSupportWall() {
        for(arena in Arena.entries) for(seed in 0..4) for(id in 0..3) {
            val g=Game(arena,false,seed);repeat(100) {g.update()}
            if(id>=2) g.skip()
            if(g.active.id!=id) assertTrue(g.switchFighter(id))
            val start=g.active.x;g.move=1f
            repeat(100) {
                if(g.active.grounded && !g.terrain.at(g.active.x+55,g.active.y+21)) g.jump()
                g.update()
            }
            assertTrue("Walk/jump route: $arena/$seed/$id at ${g.active.x},${g.active.y}",g.active.x>start+200)
            assertTrue(g.active.alive)
        }
    }
    @Test fun centralGapCanBeCrossedWithAnOrdinaryJumpOnEveryArena() {
        for(arena in Arena.entries) for(seed in 0..4) {
            val g=Game(arena,false,seed,squad=Loadout(Race.MERCENARY))
            g.active.x=1320f;g.active.y=g.terrain.surface(1320f)-18
            repeat(60) {g.update()};g.move=1f
            repeat(20) {g.update()};g.jump()
            repeat(95) {g.update()}
            assertTrue("Ordinary jump crosses $arena/$seed",g.active.x>1550 && g.active.alive)
        }
    }
    @Test fun ropeAutomaticallyPullsPlayerAndCanPayOutAndDetach() {
        val g=flat();g.active.x=600f;g.active.y=982f
        g.terrain.addGirder(600f,600f,0f,240f);g.angle=-90f
        assertTrue(g.rope());val startY=g.active.y;val initialLength=g.anchor!!.length
        repeat(35) {g.update()}
        assertTrue("Grapple must pull upwards",g.active.y<startY-130)
        assertTrue(g.anchor!!.length<initialLength-120)
        val length=g.anchor!!.length;g.reel=1f;repeat(12) {g.update()}
        assertTrue("Down pays out",g.anchor!!.length>length+30)
        val vx=g.active.vx;val vy=g.active.vy;assertTrue(g.rope())
        assertEquals(vx,g.active.vx,0f);assertEquals(vy,g.active.vy,0f)
    }
    @Test fun ropeWinchStopsBelowCeilingWithoutPassingThroughIt() {
        val g=flat();g.active.x=600f;g.terrain.addGirder(600f,600f,0f,500f);g.angle=-90f
        assertTrue(g.rope());repeat(160) {g.update()}
        assertTrue("Player stays below solid ceiling",g.active.y-62>610f)
        assertTrue(g.active.y<780)
        assertTrue(g.active.alive)
    }
}
