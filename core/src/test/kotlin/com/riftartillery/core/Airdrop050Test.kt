package com.riftartillery.core

import java.io.*
import org.junit.Assert.*
import org.junit.Test

class Airdrop050Test {
    private fun game():Game {
        val g=Game(againstBot=false);g.devices.clear();g.terrain.solid.fill(false);g.terrain.armor.fill(0)
        for(y in 1000 until WORLD_H) g.terrain.solid.fill(true,y*WORLD_W,(y+1)*WORLD_W)
        g.fighters.forEach {it.x=300f+it.id*650;it.y=982f;it.grounded=true};return g
    }
    private fun landed(g:Game,kind:DeviceKind):Device {val d=Device(kind,330f,978f,-1,55f,0f,Weapon.SUN_BOMB.ordinal,true);g.devices+=d;return d}
    @Test fun newCatalogHas140UniqueItemsAndEveryAddedItemExecutes() {
        assertEquals(140,Weapon.entries.size);assertEquals(140,Weapon.entries.map {it.title}.distinct().size)
        for(w in Weapon.entries.drop(120)) {
            val g=game();g.select(w);val stock=g.active.ammo.getValue(w)
            assertTrue(w.name,g.fire(if(w.targeted) 1100f else null,if(w.targeted) 700f else null));assertEquals(stock-1,g.active.ammo.getValue(w))
            repeat(180) {g.update()};assertTrue(g.fighters.all {it.x.isFinite() && it.y.isFinite()})
        }
    }
    @Test fun dropWaitsFallsLandsAndResumesFallingIfSupportIsDestroyed() {
        val g=game();assertTrue(g.spawnAirdrop(DeviceKind.AIRDROP_AMMO,800f));val d=g.devices.single();val y=d.y
        repeat(120) {g.update()};assertEquals(y,d.y,0f);assertFalse(d.triggered)
        repeat(600) {g.update()};assertTrue(d.triggered);assertTrue(d.y in 975f..981f)
        g.terrain.carve(d.x,1000f,100f);g.update();assertFalse(d.triggered)
    }
    @Test fun fourDropLimitAndFailedBeaconPreserveAmmo() {
        val g=game();repeat(4) {assertTrue(g.spawnAirdrop(requestedX=600f))};assertFalse(g.spawnAirdrop())
        g.select(Weapon.AIRDROP_BEACON);assertFalse(g.fire(800f,700f));assertEquals(2,g.active.ammo[Weapon.AIRDROP_BEACON])
    }
    @Test fun fallingDropCannotBeCollected() {
        val g=game();val d=landed(g,DeviceKind.AIRDROP_AMMO);d.triggered=false
        assertFalse(g.interact());assertTrue(g.devices.contains(d))
    }
    @Test fun ammunitionDropIsSingleUseAndKeepsAttack() {
        val g=game();landed(g,DeviceKind.AIRDROP_AMMO);g.select(Weapon.PHOTON_RIFLE);g.active.ammo[Weapon.PHOTON_RIFLE]=0;val time=g.timeLeft
        assertTrue(g.interact());assertEquals(2,g.active.ammo[Weapon.PHOTON_RIFLE]);assertFalse(g.interact());assertEquals(Phase.AIM,g.phase);assertEquals(time,g.timeLeft,0f)
    }
    @Test fun rareDropGrantsAndSelectsItsSavedWeapon() {
        val g=game();landed(g,DeviceKind.AIRDROP_RARE);g.active.ammo[Weapon.SUN_BOMB]=0
        val out=ByteArrayOutputStream();g.save(out);val restored=Game.load(ByteArrayInputStream(out.toByteArray()))
        assertTrue(restored.interact());assertEquals(Weapon.SUN_BOMB,restored.weapon);assertEquals(3,restored.active.ammo[Weapon.SUN_BOMB])
    }
    @Test fun rareDropCannotInterruptShotgunsSecondAttack() {
        val g=game();landed(g,DeviceKind.AIRDROP_RARE);g.select(Weapon.SHOTGUN);assertTrue(g.fire())
        assertTrue(g.interact());assertEquals(Weapon.SHOTGUN,g.weapon);assertTrue(g.active.ammo.getValue(Weapon.SUN_BOMB)>Weapon.SUN_BOMB.stock)
        assertTrue(g.fire())
    }
    @Test fun medicalDropChecksNeedAndClearsStatuses() {
        val g=game();val d=landed(g,DeviceKind.AIRDROP_MED);assertFalse(g.interact());assertTrue(g.devices.contains(d))
        g.active.hp=40f;g.active.effects[Effect.POISON.ordinal]=2;assertTrue(g.interact());assertEquals(85f,g.active.hp,0f);assertTrue(g.active.effects.all {it==0})
    }
    @Test fun armorDropCapsShieldAndLeavesFullPlayerDropUntouched() {
        val g=game();val d=landed(g,DeviceKind.AIRDROP_ARMOR);g.active.shield=120f;assertFalse(g.interact());assertTrue(g.devices.contains(d))
        g.active.shield=110f;assertTrue(g.interact());assertEquals(120f,g.active.shield,0f)
    }
    @Test fun fallingStateSurvivesSaveWithoutChangingLootOrTrajectory() {
        val g=game();g.spawnAirdrop(DeviceKind.AIRDROP_RARE,800f);repeat(240) {g.update()}
        val out=ByteArrayOutputStream();g.save(out);val restored=Game.load(ByteArrayInputStream(out.toByteArray()))
        repeat(60) {g.update();restored.update()};val a=g.devices.single();val b=restored.devices.single()
        assertEquals(a.x,b.x,.01f);assertEquals(a.y,b.y,.01f);assertEquals(a.turns,b.turns);assertEquals(a.triggered,b.triggered)
    }
    @Test fun destroyedAndSubmergedDropsDisappearAndRaceHasNoDrops() {
        val g=game();val d=landed(g,DeviceKind.AIRDROP_MED);d.hp=0f;g.update();assertTrue(g.devices.isEmpty())
        g.spawnAirdrop();g.devices.single().y=g.waterLevel-10;g.update();assertTrue(g.devices.isEmpty())
        assertFalse(Game(mode=GameMode.ROPE_RACE).spawnAirdrop())
    }
    @Test fun scheduledSupplyDoesNotDuplicateOnRepeatedTurnProcessing() {
        val g=game();g.skip();g.skip();assertEquals(3,g.turn);assertEquals(1,g.devices.count {it.kind.airdrop});g.contentTurn();assertEquals(1,g.devices.count {it.kind.airdrop})
    }
    @Test fun newPayloadsAndLaserPatternsAreDistinct() {
        for((w,n) in listOf(Weapon.SHOCK_CLUSTER to 6,Weapon.METAL_STORM to 12)) {val g=game();g.explode(Shot(1000f,500f,0f,0f,w,0,0f));assertEquals(n,g.shots.size)}
        for((w,n) in listOf(Weapon.TRI_LASER to 3,Weapon.FLECHETTE to 24,Weapon.SCATTER_PISTOL to 6,Weapon.RAIL_BURST to 2)) {val g=game();g.select(w);assertTrue(g.fire());assertEquals(n,g.beams.size)}
        val g=game();g.select(Weapon.SATELLITE);assertTrue(g.fire(1100f,500f));assertEquals(3,g.beams.size)
    }
}
