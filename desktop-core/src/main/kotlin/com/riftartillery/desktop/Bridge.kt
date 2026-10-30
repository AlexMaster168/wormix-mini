package com.riftartillery.desktop

import com.riftartillery.core.*
import java.awt.image.BufferedImage
import java.io.*
import java.util.Base64
import javax.imageio.ImageIO

/** Local standard-input protocol: the Unity client uses the exact Android simulation. */
class Bridge(private val saveFile:File) {
    var game=Game()
        private set
    private var terrainRevision=-1
    private var frame=0L
    private fun Map<String,Any?>.number(key:String,default:Float=0f)=(get(key) as? Number)?.toFloat()?.also {require(it.isFinite())} ?: default
    private fun Map<String,Any?>.index(key:String,size:Int,default:Int=0)=number(key,default.toFloat()).toInt().also {require(it in 0 until size)}
    fun request(line:String):String=try {Json.write(handle(Json.read(line)))} catch(e:Exception) {Json.write(mapOf("ok" to false,"error" to (e.message ?: e.javaClass.simpleName)))}
    private fun handle(r:Map<String,Any?>):Map<String,Any?> {
        when(r["cmd"]) {
            "catalog"->return catalog()
            "new"->{
                game=Game(Arena.entries[r.index("arena",Arena.entries.size)],r["bot"] as? Boolean ?: true,r.number("seed",41f).toInt(),GameMode.entries[r.index("mode",GameMode.entries.size)],Boss.entries[r.index("boss",Boss.entries.size)],Loadout(Race.entries[r.index("race",Race.entries.size)],Hat.entries[r.index("hat",Hat.entries.size)],Artifact.entries[r.index("artifact",Artifact.entries.size)],Upgrade.entries[r.index("upgrade",Upgrade.entries.size)]))
                terrainRevision=-1;frame=0
            }
            "tick"->{
                game.move=r.number("move").coerceIn(-1f,1f);game.reel=r.number("reel").coerceIn(-1f,1f)
                if(r.containsKey("angle") && game.canControl) game.angle=r.number("angle").coerceIn(-180f,180f)
                if(r.containsKey("power") && game.canControl) game.power=r.number("power",.65f).coerceIn(0f,1f)
                repeat(r.number("steps",1f).toInt().coerceIn(0,6)) {game.update();frame++}
            }
            "act"->{
                val action=r["action"] as? String ?: error("Missing action")
                if(action !in setOf("save","load","clearSave") && !game.humanTurn) return snapshot(false,"Ход противника")
                val success=when(action) {
                    "jump"->{game.jump();true};"rope"->game.rope();"ability"->{game.ability();true}
                    "select"->{game.select(Weapon.entries[r.index("weapon",Weapon.entries.size)]);true}
                    "fire"->game.fire(if(r.containsKey("x")) r.number("x") else null,if(r.containsKey("y")) r.number("y") else null)
                    "switch"->game.switchFighter(r.index("id",4));"skip"->{game.skip();true}
                    "fuse"->{game.grenadeTimer=if(game.grenadeTimer>=5) 1f else game.grenadeTimer+1;true}
                    "bounce"->{game.hardBounce=!game.hardBounce;true}
                    "clearSave"->{saveFile.delete();true}
                    "save"->{saveFile.parentFile.mkdirs();val temp=File(saveFile.parentFile,saveFile.name+".new");temp.outputStream().use {game.save(it)};java.nio.file.Files.move(temp.toPath(),saveFile.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);true}
                    "load"->{saveFile.inputStream().use {game=Game.load(it)};terrainRevision=-1;true}
                    else->error("Unknown action")
                }
                return snapshot(success)
            }
            "state"->if(r["terrain"]==true) terrainRevision=-1
            else->error("Unknown command")
        }
        return snapshot(true)
    }
    private fun catalog()=mapOf("ok" to true,"version" to "0.3.0","weapons" to Weapon.entries.map {mapOf("id" to it.ordinal,"name" to it.title,"category" to it.category.ordinal,"stock" to it.stock,"damage" to it.damage,"description" to it.description,"targeted" to it.targeted)},"arenas" to Arena.entries.map {mapOf("id" to it.ordinal,"name" to it.title,"description" to it.subtitle,"gravity" to it.gravity)},"categories" to Category.entries.map {mapOf("id" to it.ordinal,"name" to it.title)},"races" to Race.entries.map {mapOf("id" to it.ordinal,"name" to it.title)},"bosses" to Boss.entries.map {mapOf("id" to it.ordinal,"name" to it.title,"description" to it.briefing)},"hats" to Hat.entries.map {mapOf("id" to it.ordinal,"name" to it.title)},"artifacts" to Artifact.entries.map {mapOf("id" to it.ordinal,"name" to it.title)},"upgrades" to Upgrade.entries.map {mapOf("id" to it.ordinal,"name" to it.title)},"tracks" to MusicTrack.entries.map {mapOf("id" to it.ordinal,"name" to it.title,"asset" to it.asset)})
    private fun snapshot(ok:Boolean,error:String=""):Map<String,Any?> {
        val g=game
        val patch=if(terrainRevision!=g.terrain.revision) terrainPatch() else null
        return mapOf("ok" to ok,"error" to error,"frame" to frame,"arena" to g.arena.ordinal,"mode" to g.mode.ordinal,"boss" to g.boss.ordinal,"bossPhase" to g.bossPhase,"bossWarning" to g.bossWarning,"phase" to g.phase.ordinal,"turn" to g.turn,"time" to g.timeLeft,"retreat" to g.retreatLeft,"active" to g.active.id,"human" to g.humanTurn,"control" to g.canControl,"weapon" to g.weapon.ordinal,"angle" to g.angle,"power" to g.power,"wind" to g.wind,"water" to g.waterLevel,"winner" to (g.winner ?: -2),"message" to g.message,"checkpoints" to g.checkpoints,"raceElapsed" to g.raceElapsed,"grenadeTimer" to g.grenadeTimer,"hardBounce" to g.hardBounce,"patch" to patch,
            "fighters" to g.fighters.map {f->mapOf("id" to f.id,"team" to f.team,"race" to f.race.ordinal,"name" to f.name,"x" to f.x,"y" to f.y,"vx" to f.vx,"vy" to f.vy,"hp" to f.hp,"maxHp" to f.maxHp,"shield" to f.shield,"alive" to f.alive,"grounded" to f.grounded,"fuel" to f.fuel,"jetboard" to f.jetboard,"drill" to f.drillFuel,"parachute" to f.parachute,"abilityUsed" to f.abilityUsed,"effects" to f.effects.asIterable(),"ammo" to Weapon.entries.map {f.ammo.getValue(it)})},
            "shots" to g.shots.map {mapOf("x" to it.x,"y" to it.y,"weapon" to it.weapon.ordinal,"fuse" to it.fuse,"fragment" to it.fragment)},
            "hazards" to g.hazards.map {mapOf("x" to it.x,"y" to it.y,"radius" to it.radius,"effect" to it.effect.ordinal)},
            "devices" to g.devices.map {mapOf("x" to it.x,"y" to it.y,"kind" to it.kind.ordinal,"hp" to it.hp,"owner" to it.owner)},
            "beams" to g.beams.map {mapOf("x" to it.x,"y" to it.y,"x2" to it.x2,"y2" to it.y2)},
            "rope" to (g.anchor?.let {listOf(it)+g.ropeNodes} ?: emptyList()).map {mapOf("x" to it.x,"y" to it.y)},
            "targets" to g.raceTargets.map {mapOf("x" to it.first,"y" to it.second)},
            "trajectory" to (if(g.phase==Phase.AIM && g.humanTurn && !g.weapon.targeted) g.trajectory() else emptyList()).map {mapOf("x" to it.first,"y" to it.second)},
            "mineWarnings" to g.mineWarnings().map {mapOf("x" to it.x,"y" to it.y,"radius" to Weapon.MINE.radius)},"saveExists" to saveFile.exists())
    }
    private fun terrainPatch():Map<String,Any?>? {
        val t=game.terrain;val full=terrainRevision<0
        val left=if(full) 0 else t.dirtyLeft;val top=if(full) 0 else t.dirtyTop
        val right=if(full) WORLD_W-1 else t.dirtyRight;val bottom=if(full) WORLD_H-1 else t.dirtyBottom
        terrainRevision=t.revision
        if(right<left || bottom<top) return null
        val image=BufferedImage(right-left+1,bottom-top+1,BufferedImage.TYPE_INT_ARGB)
        val colors=intArrayOf(0x303f49,0x452d2b,0x33354e,0x314230,0x60849f,0x4f4034,0x875233,0x345358,0x39572e,0x685e4d,0x3b4359,0x5c3e7a)
        for(y in top..bottom) for(x in left..right) if(t.solid[y*WORLD_W+x]) {
            val color=if(y<3 || !t.solid[(y-3)*WORLD_W+x]) 0x66d8e6 else if(y%48<2 && x%125<120) 0x1e262c else colors[game.arena.ordinal]
            image.setRGB(x-left,y-top,color or (0xff shl 24))
        }
        val bytes=ByteArrayOutputStream();ImageIO.write(image,"png",bytes);t.cleanDirty()
        return mapOf("x" to left,"y" to top,"width" to image.width,"height" to image.height,"png" to Base64.getEncoder().encodeToString(bytes.toByteArray()))
    }
}
fun main(args:Array<String>) {
    System.setOut(PrintStream(FileOutputStream(FileDescriptor.out),true,Charsets.UTF_8))
    val saveDir=File(args.firstOrNull() ?: ".")
    val bridge=Bridge(File(saveDir,"battle.bin"))
    System.`in`.bufferedReader(Charsets.UTF_8).useLines {lines->lines.forEach {line->if(line.length<=2_000_000) {println(bridge.request(line));System.out.flush()}}}
}
