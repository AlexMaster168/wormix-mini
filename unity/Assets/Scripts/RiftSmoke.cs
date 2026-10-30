using System;
using System.Collections;
using System.IO;
using System.Linq;
using UnityEngine;

namespace Rift {
    public partial class RiftGame {
        IEnumerator Smoke() {
            yield return null;
            string[] args=Environment.GetCommandLineArgs();int index=Array.IndexOf(args,"--rift-report");
            string report=index>=0 && index+1<args.Length?args[index+1]:Path.Combine(Application.temporaryCachePath,"rift-unity-smoke.txt");
            string folder=Path.GetDirectoryName(report);Directory.CreateDirectory(folder);int checks=0;string failure=null;
            Action<bool,string> check=(ok,label)=>{if(!ok) throw new Exception(label);checks++;Debug.Log("RIFT_SMOKE PASS "+checks+": "+label);};
            try {
                check(string.IsNullOrEmpty(fatal) && catalog!=null,"Bridge starts in Windows player");
                check(catalog.weapons.Length==100 && catalog.arenas.Length==12,"100 weapons and 12 arenas");
                check(heroes!=null && bossArt!=null,"Hero and boss atlases load");
                foreach(var entry in catalog.tracks) check(Resources.Load<AudioClip>("Music/"+entry.asset)!=null,"Music resource: "+entry.asset);
                paused=true;bot=false;
                for(int i=0;i<catalog.arenas.Length;i++) {arena=i;NewBattle();paused=true;check(state.arena==i && state.fighters.All(f=>f.alive),"Arena starts: "+catalog.arenas[i].name);}
                mode=0;arena=0;NewBattle();paused=true;
                foreach(var weapon in catalog.weapons) {Act("select",",\"weapon\":"+weapon.id);check(state.weapon==weapon.id,"Weapon selection: "+weapon.name);}
                Act("select",",\"weapon\":6");Act("fire",",\"x\":900,\"y\":800");check(state.ok && state.phase==0 && Mathf.Abs(Active.x-900)<.1f,"Teleport keeps turn");
                int board=Array.FindIndex(catalog.weapons,w=>w.name=="Реактивная доска");Act("select",",\"weapon\":"+board);Act("fire");check(Active.jetboard && Active.fuel==160 && state.phase==0,"Jetboard keeps attack");
                float y=Active.y;
                for(int i=0;i<8;i++) Send("{\"cmd\":\"tick\",\"steps\":6,\"move\":1,\"reel\":-1}");
                check(Active.y<y-70 && Active.fuel<160,"Jetboard moves and consumes fuel");
                float fuel=Active.fuel;Act("save");Act("load");check(Active.jetboard && Mathf.Abs(Active.fuel-fuel)<.01f,"Portable save restores board and inventory");
                for(int i=0;i<catalog.bosses.Length;i++) {mode=1;boss=i;NewBattle();paused=true;check(state.mode==1 && state.fighters[2].maxHp>=1000,"Boss starts: "+catalog.bosses[i].name);}
                mode=2;NewBattle();paused=true;check(state.targets.Length==5 && state.fighters.Count(f=>f.alive)==1,"Rope race has five checkpoints");
                mode=0;arena=8;bot=false;NewBattle();paused=true;overview=false;manual=false;cameraX=Active.x;cameraY=Active.y-110;
                check(terrain!=null && terrain.width==2880 && terrain.height==1440,"Shared terrain texture rendered");
                File.WriteAllText(report,"PASS: "+checks+" Unity Windows integration checks\nUnity="+Application.unityVersion+"\nVersion="+catalog.version+"\nViewport="+Screen.width+"x"+Screen.height+"\nTests complete UTC="+DateTime.UtcNow.ToString("o")+"\n");
            } catch(Exception e) {failure=e.ToString();File.WriteAllText(report,"FAIL after "+checks+" checks\n"+failure);Debug.LogException(e);}
            if(failure!=null) {Application.Quit(1);yield break;}
            int originalTrack=PlayerPrefs.GetInt("musicTrack",-1);battle=false;paused=false;musicPanel=false;musicEnabled=true;
            for(int i=0;i<catalog.tracks.Length;i++) {
                ChooseMusic(i);UpdateMusic();yield return new WaitForSecondsRealtime(.2f);
                try {check(music.clip!=null && music.clip.name==catalog.tracks[i].asset && music.isPlaying && music.timeSamples>0,"Music actually plays: "+catalog.tracks[i].asset);}
                catch(Exception e) {failure=e.ToString();break;}
            }
            try {
                ChooseMusic(0);arena=3;UpdateMusic();check(music.clip.name=="neon","Manual track survives map change");
                ChooseMusic(-1);UpdateMusic();check(music.clip.name=="chemical","Automatic arena music returns");
                musicEnabled=false;UpdateMusic();check(!music.isPlaying,"Music mute pauses playback");musicEnabled=true;UpdateMusic();check(music.isPlaying,"Music resumes after mute");
                ChooseMusic(14);check(PlayerPrefs.GetInt("musicTrack")==14,"Music selection persists");
            } catch(Exception e) {failure=e.ToString();}
            ChooseMusic(originalTrack);arena=8;
            if(failure!=null) {File.WriteAllText(report,"FAIL after "+checks+" checks\n"+failure);Application.Quit(1);yield break;}
            File.WriteAllText(report,"PASS: "+checks+" Unity Windows integration checks\nUnity="+Application.unityVersion+"\nVersion="+catalog.version+"\nViewport="+Screen.width+"x"+Screen.height+"\nTests complete UTC="+DateTime.UtcNow.ToString("o")+"\n");
            battle=false;paused=false;yield return new WaitForEndOfFrame();ScreenCapture.CaptureScreenshot(Path.Combine(folder,"pc-01-menu.png"));yield return new WaitForSecondsRealtime(.7f);
            battle=true;paused=false;yield return new WaitForEndOfFrame();ScreenCapture.CaptureScreenshot(Path.Combine(folder,"pc-02-battle.png"));yield return new WaitForSecondsRealtime(.7f);
            aimPanel=true;yield return new WaitForEndOfFrame();ScreenCapture.CaptureScreenshot(Path.Combine(folder,"pc-03-aim.png"));yield return new WaitForSecondsRealtime(.7f);aimPanel=false;
            arsenal=true;category=catalog.weapons[63].category;search="Арбуз";yield return new WaitForEndOfFrame();ScreenCapture.CaptureScreenshot(Path.Combine(folder,"pc-04-watermelon.png"));yield return new WaitForSecondsRealtime(.7f);arsenal=false;
            musicPanel=true;yield return new WaitForEndOfFrame();ScreenCapture.CaptureScreenshot(Path.Combine(folder,"pc-05-music.png"));yield return new WaitForSecondsRealtime(.7f);
            Application.Quit(0);
        }
    }
}
