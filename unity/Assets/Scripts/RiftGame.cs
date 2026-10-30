using System;
using System.Collections;
using System.Globalization;
using System.IO;
using System.Linq;
using UnityEngine;

namespace Rift {
    public partial class RiftGame:MonoBehaviour {
        BridgeClient engine; Catalog catalog; State state;
        Texture2D terrain,heroes,bossArt,dot; AudioSource music,effects; AudioClip beep; string playingTrack="";
        string fatal="",toast="",search=""; bool battle,paused,arsenal,forge,help,aimPanel,musicPanel,overview,manual,resultRecorded;
        bool bot=true,musicEnabled=true,soundEnabled=true; int arena,mode,boss,race,hat,artifact,upgrade,category,page,track=-1;
        float cameraX=500,cameraY=850,zoom=1.05f,angle=-38,power=.65f,accumulator; int lastTurn=-1;
        float mouseMove,mouseReel;
        Vector2 panStart; GUIStyle textStyle,buttonStyle,titleStyle,smallStyle; Rect[] placedLabels=new Rect[4]; int labelCount;
        static readonly Color Bg=new Color(.04f,.07f,.12f),Panel=new Color(.08f,.13f,.20f),Lime=new Color(.69f,.94f,.44f),Cyan=new Color(.4f,.85f,.9f),Coral=new Color(1,.51f,.44f),Muted=new Color(.52f,.60f,.69f);
        static string F(float n) {return n.ToString("0.###",CultureInfo.InvariantCulture);}
        bool Modal {get{return paused||arsenal||forge||help||aimPanel||musicPanel;}}
        Fighter Active {get{return state.fighters.First(f=>f.id==state.active);}}
        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)] static void Boot() {
            if(FindFirstObjectByType<RiftGame>()!=null) return;
            var obj=new GameObject("RIFT Artillery");DontDestroyOnLoad(obj);obj.AddComponent<RiftGame>();
        }
        void Awake() {
            Application.runInBackground=true;Application.targetFrameRate=60;
            if(Camera.main==null) {var c=new GameObject("Camera").AddComponent<Camera>();c.tag="MainCamera";c.backgroundColor=Bg;c.clearFlags=CameraClearFlags.SolidColor;}
            if(FindFirstObjectByType<AudioListener>()==null) Camera.main.gameObject.AddComponent<AudioListener>();
            AudioListener.pause=false;AudioListener.volume=1;
            heroes=Resources.Load<Texture2D>("Art/heroes-atlas");bossArt=Resources.Load<Texture2D>("Art/bosses-atlas");
            dot=new Texture2D(32,32,TextureFormat.RGBA32,false);var pixels=new Color32[1024];
            for(int y=0;y<32;y++) for(int x=0;x<32;x++) pixels[y*32+x]=new Color32(255,255,255,(byte)((x-15.5f)*(x-15.5f)+(y-15.5f)*(y-15.5f)<240?255:0));
            dot.SetPixels32(pixels);dot.Apply();
            music=gameObject.AddComponent<AudioSource>();music.loop=true;music.volume=Mathf.Clamp01(PlayerPrefs.GetFloat("musicVolume",.35f));music.spatialBlend=0;
            effects=gameObject.AddComponent<AudioSource>();beep=AudioClip.Create("button",2400,1,24000,false);
            float[] samples=new float[2400];for(int i=0;i<samples.Length;i++) samples[i]=Mathf.Sin(i*2*Mathf.PI*660/24000)*.12f*(1-i/2400f);beep.SetData(samples,0);
            race=PlayerPrefs.GetInt("race");hat=PlayerPrefs.GetInt("hat");artifact=PlayerPrefs.GetInt("artifact");upgrade=PlayerPrefs.GetInt("upgrade");
            track=PlayerPrefs.GetInt("musicTrack",-1);musicEnabled=PlayerPrefs.GetInt("music",1)==1;soundEnabled=PlayerPrefs.GetInt("sound",1)==1;
            string saveDirectory=Environment.GetCommandLineArgs().Contains("--rift-smoke")?Path.Combine(Application.temporaryCachePath,"RiftSmoke-"+Guid.NewGuid().ToString("N")):Application.persistentDataPath;
            try {engine=new BridgeClient(saveDirectory);catalog=engine.GetCatalog();if(!catalog.ok) throw new Exception(catalog.error);state=engine.Send("{\"cmd\":\"state\"}");}
            catch(Exception e) {fatal=e.Message;Debug.LogException(e);}
            if(Environment.GetCommandLineArgs().Contains("--rift-smoke")) StartCoroutine(Smoke());
        }
        void Apply(State next) {
            if(next==null) throw new IOException("Пустой ответ игрового движка.");
            if(!next.ok && !string.IsNullOrEmpty(next.error)) toast=next.error;
            state=next;angle=state.angle;power=state.power;
            if(next.patch!=null && !string.IsNullOrEmpty(next.patch.png)) {
                if(terrain==null || next.patch.width==2880 && next.patch.height==1440) {if(terrain!=null) Destroy(terrain);terrain=new Texture2D(2880,1440,TextureFormat.RGBA32,false);terrain.filterMode=FilterMode.Point;terrain.wrapMode=TextureWrapMode.Clamp;}
                var patch=new Texture2D(2,2,TextureFormat.RGBA32,false);patch.LoadImage(Convert.FromBase64String(next.patch.png));
                terrain.SetPixels32(next.patch.x,1440-next.patch.y-next.patch.height,next.patch.width,next.patch.height,patch.GetPixels32());terrain.Apply(false);Destroy(patch);
            }
            if(state.phase==3 && !resultRecorded && battle) {
                resultRecorded=true;PlayerPrefs.SetInt("matches",PlayerPrefs.GetInt("matches")+1);
                if(state.winner==0) {PlayerPrefs.SetInt("wins",PlayerPrefs.GetInt("wins")+1);PlayerPrefs.SetInt("scrap",PlayerPrefs.GetInt("scrap",150)+(state.mode==1?180:60));}
                if(state.mode==2 && state.winner==0) PlayerPrefs.SetFloat("ropeBest",Mathf.Min(PlayerPrefs.GetFloat("ropeBest",9999),state.raceElapsed));
                PlayerPrefs.Save();
                engine.Request("{\"cmd\":\"act\",\"action\":\"clearSave\"}");state.saveExists=false;
            }
        }
        State Send(string request) {try {var next=engine.Send(request);Apply(next);return next;} catch(Exception e) {fatal=e.Message;Debug.LogException(e);return state;}}
        void Act(string action,string fields="") {Send("{\"cmd\":\"act\",\"action\":\""+action+"\""+fields+"}");}
        void NewBattle() {
            int seed=PlayerPrefs.GetInt("seed",41)+1;PlayerPrefs.SetInt("seed",seed);
            Send("{\"cmd\":\"new\",\"arena\":"+arena+",\"bot\":"+(bot?"true":"false")+",\"mode\":"+mode+",\"boss\":"+boss+",\"race\":"+race+",\"hat\":"+hat+",\"artifact\":"+artifact+",\"upgrade\":"+upgrade+",\"seed\":"+seed+"}");
            battle=true;paused=arsenal=forge=help=aimPanel=false;overview=manual=false;zoom=1.05f;lastTurn=-1;resultRecorded=false;accumulator=0;Act("save");
        }
        void SaveMenu() {if(state.phase!=3) Act("save");battle=false;paused=arsenal=aimPanel=false;}
        void CommitAim() {Send("{\"cmd\":\"tick\",\"steps\":0,\"angle\":"+F(angle)+",\"power\":"+F(power)+"}");}
        void Update() {
            if(engine==null || catalog==null || !string.IsNullOrEmpty(fatal)) return;
            UpdatePointer();
            if(Input.GetKeyDown(KeyCode.Escape)) {if(musicPanel) musicPanel=false;else if(aimPanel) {CommitAim();aimPanel=false;} else if(arsenal) arsenal=false;else if(forge) forge=false;else if(help) help=false;else if(battle) paused=!paused;else help=true;}
            if(battle && !Modal && state.phase!=3) {
                if(state.control) {
                    // Discrete keys are handled by IMGUI events, including Russian layout characters.
                    float wheel=Input.mouseScrollDelta.y;if(Input.GetKey(KeyCode.LeftShift)) zoom=Mathf.Clamp(zoom+wheel*.1f,.42f,1.6f);else power=Mathf.Clamp01(power+wheel*.025f);
                }
                float move=state.control?Mathf.Clamp(mouseMove+(Input.GetKey(KeyCode.RightArrow)||heldRight?1:0)-(Input.GetKey(KeyCode.LeftArrow)||heldLeft?1:0),-1,1):0;
                float reel=state.control?Mathf.Clamp(mouseReel+(heldDown?1:0)-(heldUp?1:0),-1,1):0;
                if(state.control) {angle=Mathf.Clamp(angle+((Input.GetKey(KeyCode.DownArrow)?1:0)-(Input.GetKey(KeyCode.UpArrow)?1:0))*45*Time.unscaledDeltaTime,-180,180);power=Mathf.Clamp01(power+((Input.GetKey(KeyCode.Equals)||Input.GetKey(KeyCode.KeypadPlus)?1:0)-(Input.GetKey(KeyCode.Minus)||Input.GetKey(KeyCode.KeypadMinus)?1:0))*Time.unscaledDeltaTime*.4f);}
                accumulator+=Mathf.Min(Time.unscaledDeltaTime,.1f);int steps=Mathf.Min(6,(int)(accumulator*60));
                if(steps>0) {accumulator-=steps/60f;Send("{\"cmd\":\"tick\",\"steps\":"+steps+",\"move\":"+F(move)+",\"reel\":"+F(reel)+",\"angle\":"+F(angle)+",\"power\":"+F(power)+"}");}
                if(state.turn!=lastTurn) {lastTurn=state.turn;manual=false;cameraX=Active.x;cameraY=Active.y-110;Act("save");}
                if(overview) {zoom=.42f;cameraX=1440;cameraY=720;}
                else if(!manual) {cameraX=Mathf.Lerp(cameraX,Active.x,10*Time.unscaledDeltaTime);if(Mathf.Abs(Active.y-110-cameraY)>90) cameraY=Mathf.Lerp(cameraY,Active.y-110,6*Time.unscaledDeltaTime);}
            } else accumulator=0;
            UpdateMusic();
        }
        void UpdateMusic() {
            int selected=track<0?(battle?state.arena:arena):track;selected=Mathf.Clamp(selected,0,catalog.tracks.Length-1);
            string desired=catalog.tracks[selected].asset;
            if(playingTrack!=desired) {music.Stop();music.clip=Resources.Load<AudioClip>("Music/"+desired);playingTrack=desired;if(musicEnabled && music.clip!=null) music.Play();}
            if(!musicEnabled || battle && paused && !musicPanel) {if(music.isPlaying) music.Pause();}
            else if(!music.isPlaying && music.clip!=null) {music.UnPause();if(!music.isPlaying) music.Play();}
        }
        void ChooseMusic(int selection) {track=Mathf.Clamp(selection,-1,catalog.tracks.Length-1);PlayerPrefs.SetInt("musicTrack",track);PlayerPrefs.Save();}
        void OnApplicationFocus(bool focused) {if(!focused) {heldLeft=heldRight=heldUp=heldDown=false;mouseMove=mouseReel=0;}}
        void OnApplicationPause(bool value) {if(value && battle) {paused=true;Act("save");} if(value && music!=null) music.Pause();}
        void OnApplicationQuit() {if(engine!=null) {try {if(battle && state.phase!=3) Act("save");} catch(Exception) {} engine.Dispose();engine=null;}}
    }
}
