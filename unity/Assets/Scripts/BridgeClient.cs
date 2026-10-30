using System;
using System.Diagnostics;
using System.IO;
using System.Text;
using UnityEngine;

namespace Rift {
    [Serializable] public class Entry { public int id,category,stock; public string name,description,asset; public float damage,gravity; public bool targeted; }
    [Serializable] public class Catalog { public bool ok; public string version,error; public Entry[] weapons,arenas,categories,races,bosses,hats,artifacts,upgrades,tracks; }
    [Serializable] public class Patch { public int x,y,width,height; public string png; }
    [Serializable] public class Fighter { public int id,team,race; public string name; public float x,y,vx,vy,hp,maxHp,shield,fuel,drill; public bool alive,grounded,jetboard,parachute,abilityUsed; public int[] effects,ammo; }
    [Serializable] public class Point { public float x,y,x2,y2,radius,fuse,hp; public int weapon,effect,kind,owner; public bool fragment; }
    [Serializable] public class State {
        public bool ok,human,control,hardBounce,saveExists; public string error,message,bossWarning; public long frame;
        public int arena,mode,boss,bossPhase,phase,turn,active,weapon,winner,checkpoints;
        public float time,retreat,angle,power,wind,water,raceElapsed,grenadeTimer;
        public Fighter[] fighters; public Point[] shots,hazards,devices,beams,rope,targets,trajectory,mineWarnings; public Patch patch;
    }
    public sealed class BridgeClient : IDisposable {
        private Process process;
        private readonly StringBuilder diagnostics=new StringBuilder();
        public BridgeClient(string saveDirectory) {
            string jar=Path.Combine(Application.streamingAssetsPath,"Engine","rift-sim.jar");
            string runtime=Path.GetFullPath(Path.Combine(Application.dataPath,"..","Engine","runtime","bin","java.exe"));
            if(!File.Exists(runtime)) runtime=Path.Combine(Environment.GetEnvironmentVariable("JAVA_HOME") ?? "", "bin", "java.exe");
            if(!File.Exists(runtime)) throw new FileNotFoundException("Не найден комплект Java. Распакуйте всю папку игры, включая Engine/runtime.",runtime);
            if(!File.Exists(jar)) throw new FileNotFoundException("Не найден игровой движок. Запустите tools/Build-Unity.ps1.",jar);
            Directory.CreateDirectory(saveDirectory);
            process=new Process {StartInfo=new ProcessStartInfo(runtime,"-Xmx512m -Dfile.encoding=UTF-8 -Djava.awt.headless=true -jar "+Quote(jar)+" "+Quote(saveDirectory)) {UseShellExecute=false,RedirectStandardInput=true,RedirectStandardOutput=true,RedirectStandardError=true,CreateNoWindow=true,StandardInputEncoding=new UTF8Encoding(false),StandardOutputEncoding=Encoding.UTF8,StandardErrorEncoding=Encoding.UTF8}};
            // Bundled Java must not inherit developer agents or incompatible machine-wide JVM flags.
            foreach(string key in new[]{"JAVA_TOOL_OPTIONS","JDK_JAVA_OPTIONS","_JAVA_OPTIONS"}) process.StartInfo.EnvironmentVariables.Remove(key);
            process.ErrorDataReceived+=(s,e)=>{if(e.Data!=null) lock(diagnostics) {if(diagnostics.Length<16000) diagnostics.AppendLine(e.Data);}};
            process.Start();process.BeginErrorReadLine();
        }
        private static string Quote(string value) {return "\""+value.Replace("\"","\\\"")+"\"";}
        public string Request(string json) {
            if(process==null || process.HasExited) throw new IOException("Игровая симуляция остановилась. "+diagnostics);
            process.StandardInput.WriteLine(json);process.StandardInput.Flush();
            var read=process.StandardOutput.ReadLineAsync();
            if(!read.Wait(15000)) throw new TimeoutException("Движок не ответил за 15 секунд.");
            if(read.Result==null) throw new IOException("Движок завершил работу. "+diagnostics);
            return read.Result;
        }
        public Catalog GetCatalog() {return JsonUtility.FromJson<Catalog>(Request("{\"cmd\":\"catalog\"}"));}
        public State Send(string json) {return JsonUtility.FromJson<State>(Request(json));}
        public void Dispose() {
            if(process==null) return;
            try {process.StandardInput.Close();if(!process.WaitForExit(1500)) process.Kill();} catch(InvalidOperationException) {}
            process.Dispose();process=null;
        }
    }
}
