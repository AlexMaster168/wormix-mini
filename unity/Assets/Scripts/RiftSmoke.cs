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
                check(FindObjectsByType<AudioListener>(FindObjectsSortMode.None).Count(l=>l.enabled && l.gameObject.activeInHierarchy)==1,"Exactly one active AudioListener routes sound to output");
                check(!AudioListener.pause && AudioListener.volume>0 && music.spatialBlend==0,"Audio output is enabled and music is not positional");
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
                File.WriteAllText(report,"PASS: "+checks+" Unity Windows integration checks\nUnity="+Application.unityVersion+"\nClientVersion="+Application.version+"\nCoreVersion="+catalog.version+"\nViewport="+Screen.width+"x"+Screen.height+"\nTests complete UTC="+DateTime.UtcNow.ToString("o")+"\n");
            } catch(Exception e) {failure=e.ToString();File.WriteAllText(report,"FAIL after "+checks+" checks\n"+failure);Debug.LogException(e);}
            if(failure!=null) {Application.Quit(1);yield break;}
            // Unlike isPlaying, listener samples prove that the audio reaches the final mixer.
            battle=false;paused=false;musicEnabled=true;ChooseMusic(0);UpdateMusic();
            float peak=0;float[] output=new float[2048];
            for(int sample=0;sample<30;sample++) {yield return new WaitForSecondsRealtime(.1f);AudioListener.GetOutputData(output,0);peak=Mathf.Max(peak,output.Max(v=>Mathf.Abs(v)));}
            Debug.Log("RIFT_AUDIO peak="+peak+" volume="+music.volume+" playing="+music.isPlaying+" samples="+music.timeSamples+" focus="+Application.isFocused+" dsp="+AudioSettings.dspTime);
            try {check(peak>.00001f,"Final audio mixer produces nonzero music samples");}
            catch(Exception e) {File.WriteAllText(report,"FAIL after "+checks+" checks\n"+e);Application.Quit(1);yield break;}
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
            // Exercise the same key-event handler used by the player, with the live Update loop.
            mode=0;arena=8;bot=false;NewBattle();paused=false;
            yield return new WaitForSecondsRealtime(.6f);
            try {
                check(HandleFieldClick(new Vector2(900,300)) && Mathf.Abs(state.angle+38)>1,"Mouse field click updates committed aim");
                paused=true;float before=angle;check(!HandleFieldClick(new Vector2(600,450)) && angle==before,"Pause blocks mouse aim underneath");paused=false;
                check(!HandleFieldClick(new Vector2(1200,240)),"Enemy locator button is not intercepted by aiming");
            } catch(Exception e) {failure=e.ToString();}
            float startX=Active.x;
            HandleKey(new Event {type=EventType.KeyDown,keyCode=KeyCode.A,character='a'});
            yield return new WaitForSecondsRealtime(.25f);
            HandleKey(new Event {type=EventType.KeyUp,keyCode=KeyCode.A});
            try {check(Active.x<startX-5,"Keyboard A moves the fighter through live Update");check(!heldLeft,"Key release stops left movement");}
            catch(Exception e) {failure=e.ToString();}
            startX=Active.x;HandleKey(new Event {type=EventType.KeyDown,keyCode=KeyCode.None,character='в'});
            yield return new WaitForSecondsRealtime(.25f);
            HandleKey(new Event {type=EventType.KeyUp,keyCode=KeyCode.None});
            try {
                check(Active.x>startX+5,"Russian layout character moves the fighter");
                HandleKey(new Event {type=EventType.KeyDown,keyCode=KeyCode.R});check(arsenal,"R opens arsenal");arsenal=false;
                HandleKey(new Event {type=EventType.KeyDown,keyCode=KeyCode.None,character='к'});check(arsenal,"Russian layout opens arsenal");arsenal=false;
                int actor=state.active;HandleKey(new Event {type=EventType.KeyDown,keyCode=KeyCode.Tab});check(state.active!=actor,"Tab switches allied fighter");
            } catch(Exception e) {failure=e.ToString();}
            yield return new WaitForSecondsRealtime(.6f);
            float jumpY=Active.y;HandleKey(new Event {type=EventType.KeyDown,keyCode=KeyCode.Space});
            yield return new WaitForSecondsRealtime(.12f);
            try {check(Active.y<jumpY-5,"Space jumps in the live simulation");HandleKey(new Event {type=EventType.KeyDown,keyCode=KeyCode.Return});check(state.phase!=0,"Enter fires selected weapon");}
            catch(Exception e) {failure=e.ToString();}
            bool wasFull=Screen.fullScreen;ToggleFullscreen();yield return new WaitForSecondsRealtime(.6f);
            try {check(Screen.fullScreen!=wasFull,"Fullscreen switch changes actual player mode");}
            catch(Exception e) {failure=e.ToString();}
            ToggleFullscreen();yield return new WaitForSecondsRealtime(.6f);
            try {check(Screen.fullScreen==wasFull,"Fullscreen switch restores windowed mode");}
            catch(Exception e) {failure=e.ToString();}
            if(failure!=null) {File.WriteAllText(report,"FAIL after "+checks+" checks\n"+failure);Application.Quit(1);yield break;}
            NewBattle();cameraX=Active.x;cameraY=Active.y-110;
            File.WriteAllText(report,"PASS: "+checks+" Unity Windows integration checks\nUnity="+Application.unityVersion+"\nClientVersion="+Application.version+"\nCoreVersion="+catalog.version+"\nViewport="+Screen.width+"x"+Screen.height+"\nTests complete UTC="+DateTime.UtcNow.ToString("o")+"\n");
            battle=false;paused=false;yield return new WaitForEndOfFrame();ScreenCapture.CaptureScreenshot(Path.Combine(folder,"pc-01-menu.png"));yield return new WaitForSecondsRealtime(.7f);
            battle=true;paused=false;yield return new WaitForEndOfFrame();ScreenCapture.CaptureScreenshot(Path.Combine(folder,"pc-02-battle.png"));yield return new WaitForSecondsRealtime(.7f);
            aimPanel=true;yield return new WaitForEndOfFrame();ScreenCapture.CaptureScreenshot(Path.Combine(folder,"pc-03-aim.png"));yield return new WaitForSecondsRealtime(.7f);aimPanel=false;
            arsenal=true;category=catalog.weapons[63].category;search="Арбуз";yield return new WaitForEndOfFrame();ScreenCapture.CaptureScreenshot(Path.Combine(folder,"pc-04-watermelon.png"));yield return new WaitForSecondsRealtime(.7f);arsenal=false;
            musicPanel=true;yield return new WaitForEndOfFrame();ScreenCapture.CaptureScreenshot(Path.Combine(folder,"pc-05-music.png"));yield return new WaitForSecondsRealtime(.7f);
            Application.Quit(0);
        }
    }
}
