package com.riftartillery.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
import java.io.*

class ContentTest {
    @Test fun sapperBeltRevealsEnemyMinesAndPersistsThroughSaveAndAllySwitch() {
        val game=Game(againstBot=false,squad=Loadout(artifact=Artifact.SAPPER))
        val enemy=Device(DeviceKind.MINE,800f,900f,2)
        val ally=Device(DeviceKind.MINE,900f,900f,1)
        val spider=Device(DeviceKind.SPIDER,1000f,900f,3)
        game.devices.addAll(listOf(enemy,ally,spider,Device(DeviceKind.TURRET,1100f,900f,2)))
        assertEquals(listOf(enemy,spider),game.mineWarnings())
        val bytes=ByteArrayOutputStream();game.save(bytes)
        val restored=Game.load(ByteArrayInputStream(bytes.toByteArray()))
        assertEquals(2,restored.mineWarnings().size)
        assertTrue(restored.switchFighter(1));assertTrue(restored.mineWarnings().isEmpty())
        restored.devices.first {it.kind==DeviceKind.MINE && it.owner==2}.hp=0f
        restored.fighters[1].loadout=Loadout(artifact=Artifact.SAPPER)
        assertEquals(1,restored.mineWarnings().size)
    }
    @Test fun fileSaveAndLoadBatchIoWithoutClosingCallerStreams() {
        val bytes=ByteArrayOutputStream()
        var writes=0;var closed=false
        val output=object:OutputStream() {
            override fun write(b:Int) {writes++;bytes.write(b)}
            override fun write(b:ByteArray,off:Int,len:Int) {writes++;bytes.write(b,off,len)}
            override fun close() {closed=true}
        }
        val game=Game()
        game.save(output)
        assertFalse(closed)
        assertTrue("Terrain must not cause hundreds of thousands of file writes",writes<32)
        val source=ByteArrayInputStream(bytes.toByteArray())
        var reads=0
        val input=object:InputStream() {
            override fun read():Int {reads++;return source.read()}
            override fun read(b:ByteArray,off:Int,len:Int):Int {reads++;return source.read(b,off,len)}
            override fun close() {closed=true}
        }
        val restored=Game.load(input)
        assertTrue("Terrain reads must also be batched",reads<32)
        assertFalse(closed)
        assertArrayEquals(game.terrain.solid,restored.terrain.solid)
    }
    private fun arenaGame():Game {
        val g=Game(againstBot=false)
        g.active.x=300f;g.active.y=300f
        g.fighters[2].x=460f;g.fighters[2].y=300f
        g.angle=0f
        return g
    }
    @Test fun expandedCatalogHasSixtyDistinctWeaponsAndTools() {
        assertEquals(60,Weapon.entries.size)
        assertEquals(60,Weapon.entries.map {it.title}.distinct().size)
        assertEquals(7,Race.entries.size);assertEquals(8,Arena.entries.size);assertEquals(4,Boss.entries.size)
    }
    @Test fun allWeaponsExecuteTheirActionWithoutCrashingOrUnboundedFlight() {
        for(w in Weapon.entries) {
            val g=arenaGame();g.select(w)
            if(w==Weapon.HEAL) g.active.hp=50f
            if(w.kind==Kind.MELEE) g.fighters[2].x=370f
            if(w==Weapon.ROPE) {g.active.x=600f;g.active.y=750f;g.angle=-90f}
            val tx=if(w==Weapon.TELEKINESIS) 460f else 1000f
            assertTrue("Action must work: $w",g.fire(if(w.targeted) tx else null,if(w.targeted) 300f else null))
            if(w==Weapon.TELEKINESIS) assertTrue(g.fire(900f,300f))
            if(w==Weapon.SHOTGUN) assertTrue(g.fire())
            if(w==Weapon.SHEEP) g.fire()
            repeat(1500) {g.update()}
            assertTrue("Bounded shots: $w",g.shots.size<=8)
            assertTrue("No stuck flight: $w",g.phase!=Phase.FLIGHT)
            assertTrue(g.fighters.all {it.x.isFinite() && it.hp.isFinite() && it.hp>=0 && it.hp<=it.maxHp})
        }
    }
    @Test fun requestedGunsDamageTargetsWithDistinctBehaviors() {
        for(w in listOf(Weapon.AK47,Weapon.MINIGUN,Weapon.UZI,Weapon.LASER,Weapon.SNIPER)) {
            val g=arenaGame();val before=g.fighters[2].hp;g.select(w);assertTrue(g.fire())
            assertTrue("$w hits",g.fighters[2].hp<before);assertTrue(g.beams.isNotEmpty())
        }
        val g=arenaGame();g.select(Weapon.REPULSOR);g.fire();assertTrue(g.fighters[2].vx>500)
    }
    @Test fun shotgunAllowsExactlyTwoShotsAndOneStockPerPair() {
        val g=arenaGame();g.select(Weapon.SHOTGUN);val ammo=g.active.ammo.getValue(Weapon.SHOTGUN)
        assertTrue(g.fire());assertEquals(Phase.AIM,g.phase)
        g.select(Weapon.ROCKET);assertEquals(Weapon.SHOTGUN,g.weapon)
        assertTrue(g.fire());assertEquals(Phase.RETREAT,g.phase);assertFalse(g.fire())
        assertEquals(ammo-1,g.active.ammo[Weapon.SHOTGUN])
    }
    @Test fun gasHealsMutantsAndDoesNotHarmCyborgsOrDemonsFire() {
        val g=arenaGame();val f=g.fighters[2];f.race=Race.MUTANT;f.hp=50f
        g.hurt(f,15f,DamageType.POISON);assertEquals(60f,f.hp,0f)
        f.race=Race.CYBORG;g.hurt(f,15f,DamageType.POISON);assertEquals(60f,f.hp,0f)
        f.race=Race.DEMON;g.hurt(f,30f,DamageType.FIRE);assertEquals(60f,f.hp,0f)
    }
    @Test fun cryoPreservesTerrainButAppliesFreeze() {
        val g=arenaGame();val before=g.terrain.solid.copyOf()
        val f=g.fighters[2];g.explode(Shot(f.x,f.y,0f,0f,Weapon.CRYO,0,0f))
        assertArrayEquals(before,g.terrain.solid);assertTrue(f.effects[Effect.FREEZE.ordinal]>0)
    }
    @Test fun gasAndFireLeavePersistentHazards() {
        val g=arenaGame();g.explode(Shot(800f,950f,0f,0f,Weapon.GAS,0,0f));g.explode(Shot(1000f,950f,0f,0f,Weapon.MOLOTOV,0,0f))
        assertEquals(setOf(Effect.POISON,Effect.BURN),g.hazards.map {it.effect}.toSet())
        g.skip();assertTrue(g.hazards.isNotEmpty())
    }
    @Test fun generatorsActuallyProtectGeneralAndCanBeDestroyed() {
        val g=Game(Arena.SCRAP,true,42,GameMode.BOSS,Boss.GENERAL)
        val b=g.fighters[2];g.hurt(b,100f);assertEquals(1300f,b.hp,0f)
        g.devices.filter {it.kind==DeviceKind.GENERATOR}.forEach {it.hp=0f}
        g.update();g.hurt(b,100f);assertTrue(b.hp<1300f)
    }
    @Test fun fourBossesHavePhasesAndRealAttacks() {
        for(b in Boss.entries) {
            val g=Game(b.arena,true,2,GameMode.BOSS,b)
            assertEquals(b.hp.toFloat(),g.fighters[2].hp,0f)
            g.fighters[2].hp=g.fighters[2].maxHp*.25f
            g.skip();assertEquals(3,g.bossPhase)
            repeat(110) {g.update()}
            assertTrue("Boss action: $b",g.bossActionCount>0)
            assertTrue(g.shots.isNotEmpty() || g.beams.isNotEmpty() || g.phase!=Phase.AIM)
            assertTrue(g.bossWarning.contains("фаза 3"))
        }
    }
    @Test fun allRacesAndEquipmentAffectActualMatch() {
        for(r in Race.entries) {
            val g=Game(squad=Loadout(r,Hat.HELMET,Artifact.GRAVITY,Upgrade.TRIPLE))
            assertEquals(r,g.active.race);assertEquals(r.hp+15f,g.active.maxHp,0f)
            g.active.grounded=true;g.jump();assertTrue(g.active.vy< -300)
            g.ability();assertTrue(g.active.abilityUsed)
        }
        val g=Game(squad=Loadout(upgrade=Upgrade.TRIPLE));g.fire();assertEquals(3,g.shots.size)
    }
    @Test fun floodAndEarthquakeChangeWorldState() {
        val g=arenaGame();g.select(Weapon.FLOOD);g.fire();assertEquals(WATER_Y-90,g.waterLevel,0f)
        val q=arenaGame();q.select(Weapon.QUAKE);q.fire();q.update();assertTrue(q.quakeTime>3 && q.shake>0)
    }
    @Test fun ropeRaceRequiresAllCheckpointsAndRope() {
        val g=Game(mode=GameMode.ROPE_RACE)
        g.select(Weapon.ROCKET);assertFalse(g.fire())
        g.select(Weapon.ROPE);g.active.x=600f;g.active.y=750f;g.angle=-90f;assertTrue(g.fire())
        val first=g.raceTargets.first();g.active.x=first.first;g.active.y=first.second;g.update();assertEquals(1,g.checkpoints)
        g.active.hp=0f;g.update();assertEquals(Phase.ENDED,g.phase);assertEquals(1,g.winner)
    }
    @Test fun expandedSaveRestoresBossHazardsDevicesAndStatuses() {
        val g=Game(Arena.SCRAP,true,15,GameMode.BOSS,Boss.GENERAL,Loadout(Race.PHANTOM,Hat.MASK,Artifact.BLOOD,Upgrade.PLASMA))
        g.fighters[0].effects[Effect.BURN.ordinal]=2;g.hazards+=Hazard(450f,900f,80f,Effect.POISON,4,0);g.waterLevel-=90
        val out=ByteArrayOutputStream();g.save(out);val b=Game.load(ByteArrayInputStream(out.toByteArray()))
        assertEquals(g.mode,b.mode);assertEquals(g.boss,b.boss);assertEquals(g.squad,b.squad)
        assertEquals(g.devices,b.devices);assertEquals(g.hazards,b.hazards);assertArrayEquals(g.fighters[0].effects,b.fighters[0].effects)
        assertEquals(g.waterLevel,b.waterLevel,0f);assertEquals(g.fighters[2].maxHp,b.fighters[2].maxHp,0f)
    }
    @Test fun targetedActionsRejectOutOfWorldAndNanWithoutSpendingAmmo() {
        for(w in Weapon.entries.filter {it.targeted}) {
            val g=arenaGame();g.select(w);val before=g.active.ammo[w]
            assertFalse(g.fire(Float.NaN,300f));assertFalse(g.fire(WORLD_W+10f,300f));assertEquals(before,g.active.ammo[w])
        }
    }
    @Test fun switchingFightersPreservesTimerAndBlocksEnemyAndDeadSelections() {
        val g=Game(againstBot=false);repeat(100) {g.update()};val time=g.timeLeft
        assertFalse(g.switchFighter(2));assertTrue(g.switchFighter(1));assertEquals(time,g.timeLeft,0f)
        assertEquals(1,g.active.id);assertFalse(g.switchFighter(0))
        g.select(Weapon.SWITCH);val stock=g.active.ammo.getValue(Weapon.SWITCH);assertTrue(g.fire());assertEquals(0,g.active.id)
        assertEquals(stock-1,g.fighters[1].ammo[Weapon.SWITCH]);assertEquals(time,g.timeLeft,0f)
        g.select(Weapon.ROCKET);g.fire();assertFalse(g.switchFighter(1))
    }
    @Test fun defenderAbsorbsDamageAndDoesNotRemoveOtherShieldOnExpiry() {
        val g=arenaGame();g.active.shield=30f;g.select(Weapon.DEFENDER);assertTrue(g.fire());assertEquals(70f,g.active.shield,0f)
        g.hurt(g.active,40f,DamageType.MELEE);val after=g.active.shield
        assertTrue(after<70 && after>=30)
        g.skip();g.skip();assertEquals(30f,g.fighters[0].shield,.1f)
    }
    @Test fun drillActuallyCutsTerrainAndMovesFighterThroughRock() {
        val g=Game(againstBot=false);repeat(100) {g.update()}
        val x=g.active.x;val before=g.terrain.solid.count {it}
        g.select(Weapon.DIGGER);assertTrue(g.fire());g.angle=25f;g.move=1f
        repeat(150) {g.update()};g.move=0f
        assertTrue("Drill advances",g.active.x>x+120)
        assertTrue("Drill removes rock",g.terrain.solid.count {it}<before)
        assertTrue(g.active.drillFuel in 60f..80f);assertEquals(Phase.AIM,g.phase)
        val out=ByteArrayOutputStream();g.save(out);val restored=Game.load(ByteArrayInputStream(out.toByteArray()))
        assertEquals(g.active.drillFuel,restored.active.drillFuel,0f)
    }
    @Test fun steelGirderSurvivesFirstExplosionAndBreaksOnSecond() {
        val t=Terrain(Arena.ORBIT,6);t.addGirder(400f,300f,0f)
        t.carve(400f,300f,40f);assertTrue(t.at(400f,300f))
        t.carve(400f,300f,40f);assertFalse(t.at(400f,300f))
    }
    @Test fun giantBossHasFullHeightHitArea() {
        val g=Game(Arena.VOLCANO,true,1,GameMode.BOSS)
        val boss=g.fighters[2]
        assertTrue(g.hitsFighter(boss,boss.x,boss.y-200))
        assertFalse(g.hitsFighter(boss,boss.x+200,boss.y-200))
        val before=boss.hp;g.explode(Shot(boss.x,boss.y-190,0f,0f,Weapon.ROCKET,0,0f))
        assertTrue(boss.hp<before)
    }
    @Test fun undeadGravestoneCanBeDestroyedBeforeResurrection() {
        val g=Game(againstBot=false,squad=Loadout(Race.UNDEAD))
        g.hurt(g.active,1000f);assertEquals(2,g.active.reviveIn);assertEquals(30f,g.active.shield,0f)
        g.hurt(g.active,31f);assertEquals(0,g.active.reviveIn);assertEquals(0f,g.active.hp,0f)
        g.skip();g.skip();assertFalse(g.fighters[0].alive)
        val other=Game(againstBot=false,squad=Loadout(Race.UNDEAD));other.hurt(other.active,1000f);other.skip();other.skip()
        assertEquals(30f,other.fighters[0].hp,0f);assertEquals(0f,other.fighters[0].shield,0f)
    }
    @Test fun legacyVersionOneSaveMigratesToLargerWorldAndKeepsInventory() {
        val out=ByteArrayOutputStream();val d=DataOutputStream(out)
        d.writeInt(0x52494654);d.writeInt(1);d.writeInt(0);d.writeBoolean(false);d.writeInt(42);d.writeInt(14)
        d.writeInt(0);d.writeInt(1);d.writeInt(0)
        listOf(30f,0f,-38f,.65f).forEach {d.writeFloat(it)};d.writeInt(0)
        d.writeFloat(0f);d.writeFloat(0f);d.writeInt(0);d.writeInt(-1);d.writeInt(-2);d.writeUTF("Legacy");d.writeBoolean(false)
        for((i,x) in listOf(170f,380f,1040f,1260f).withIndex()) {
            val hp=if(i in 1..2) 110f else if(i==3) 95f else 100f
            listOf(x,500f,0f,0f,hp).forEach {d.writeFloat(it)};d.writeBoolean(true);d.writeFloat(0f);d.writeBoolean(false)
            listOf(-1,4,2,2,3,1,2).forEach {d.writeInt(it)}
        }
        d.writeInt(0)
        for(i in 0 until 1440*810 step 8) d.writeByte(if(i/1440>=550) 255 else 0)
        val restored=Game.load(ByteArrayInputStream(out.toByteArray()))
        assertEquals(340f,restored.active.x,0f);assertEquals(4,restored.active.ammo[Weapon.GRENADE])
        assertTrue(restored.terrain.at(400f,1200f));assertFalse(restored.terrain.at(400f,200f))
        assertTrue(restored.terrain.armor.all {it==0.toByte()})
    }
}
