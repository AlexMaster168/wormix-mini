package com.riftartillery.desktop
import com.riftartillery.core.*
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class BridgeTest {
    private fun bridge()=Bridge(Files.createTempDirectory("rift-desktop-test").resolve("battle.bin").toFile())
    @Test fun catalogAndTerrainUseTheSharedCore() {
        val b=bridge();val json=b.request("{\"cmd\":\"catalog\"}")
        assertTrue(json.contains("Арбузобомба"));assertTrue(json.contains("Кристальные пещеры"));assertTrue(json.contains("Последний рубеж"))
        val initial=b.request("{\"cmd\":\"new\",\"arena\":11,\"bot\":false}")
        assertEquals(Arena.CRYSTAL,b.game.arena);assertTrue(initial.contains("\"png\":\"iVBOR"))
        assertTrue(b.request("{\"cmd\":\"state\"}").contains("\"patch\":null"))
    }
    @Test fun desktopMedicineAndTeleportKeepTheAttackAvailable() {
        val b=bridge();b.game.active.hp=30f
        b.request("{\"cmd\":\"act\",\"action\":\"select\",\"weapon\":5}")
        assertTrue(b.request("{\"cmd\":\"act\",\"action\":\"fire\"}").contains("\"ok\":true"))
        assertEquals(Phase.AIM,b.game.phase);assertEquals(80f,b.game.active.hp,.01f)
        b.request("{\"cmd\":\"act\",\"action\":\"select\",\"weapon\":6}")
        b.request("{\"cmd\":\"act\",\"action\":\"fire\",\"x\":900,\"y\":700}")
        assertEquals(900f,b.game.active.x,0f);assertEquals(Phase.AIM,b.game.phase)
    }
    @Test fun saveFileIsReadableByAndroidCoreAndKeepsJetboard() {
        val path=Files.createTempDirectory("rift-desktop-save").resolve("battle.bin").toFile();val b=Bridge(path)
        b.request("{\"cmd\":\"act\",\"action\":\"select\",\"weapon\":"+Weapon.JETBOARD.ordinal+"}")
        b.request("{\"cmd\":\"act\",\"action\":\"fire\"}");b.request("{\"cmd\":\"act\",\"action\":\"save\"}")
        val g=path.inputStream().use {Game.load(it)};assertTrue(g.active.jetboard);assertEquals(160f,g.active.fuel,0f)
        b.request("{\"cmd\":\"act\",\"action\":\"load\"}");assertEquals(g.active.ammo,b.game.active.ammo)
    }
    @Test fun invalidRequestsAreRejectedAndSessionSurvives() {
        val b=bridge()
        for(json in listOf("{}","{\"cmd\":\"new\",\"arena\":999}","{\"cmd\":\"tick\",\"angle\":NaN}","{\"cmd\":\"nope\"}")) assertTrue(b.request(json).contains("\"ok\":false"))
        assertTrue(b.request("{\"cmd\":\"state\"}").contains("\"ok\":true"))
    }
    @Test fun protocolEscapesUnicodeQuotesAndControlCharacters() {
        val data=mapOf("cmd" to "каталог \"тест\"\n","number" to 42,"bool" to true)
        val encoded=Json.write(data);val restored=Json.read(encoded)
        assertEquals(data["cmd"],restored["cmd"]);assertEquals(42.0,restored["number"]);assertEquals(true,restored["bool"])
    }
    @Test fun realProcessWritesUtf8EvenWhenWindowsDefaultEncodingIsCyrillic() {
        val javaExecutable=java.io.File(System.getProperty("java.home"),"bin/java.exe").absolutePath
        val cp=listOf(Bridge::class.java,Game::class.java,kotlin.Unit::class.java).map {java.io.File(it.protectionDomain.codeSource.location.toURI()).absolutePath}.distinct().joinToString(java.io.File.pathSeparator)
        val process=ProcessBuilder(javaExecutable,"-Dfile.encoding=windows-1251","-cp",cp,"com.riftartillery.desktop.BridgeKt",Files.createTempDirectory("rift-utf8").toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        try {
            process.outputStream.bufferedWriter(Charsets.UTF_8).use {it.write("{\"cmd\":\"catalog\"}\n")}
            val response=process.inputStream.bufferedReader(Charsets.UTF_8).readLine()
            assertTrue(response.contains("Арбузобомба"));assertTrue(response.contains("Реактивная доска"))
            assertTrue(process.waitFor(10,java.util.concurrent.TimeUnit.SECONDS));assertEquals(0,process.exitValue())
        } finally {if(process.isAlive) process.destroyForcibly()}
    }
}
