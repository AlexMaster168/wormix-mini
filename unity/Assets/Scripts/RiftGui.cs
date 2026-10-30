using System;
using System.Linq;
using UnityEngine;

namespace Rift {
    public partial class RiftGame {
        void Styles() {
            if(textStyle!=null) return;
            Font font=Font.CreateDynamicFontFromOSFont(new[]{"Arial","Segoe UI","DejaVu Sans"},24);
            textStyle=new GUIStyle(GUI.skin.label) {font=font,fontSize=24,wordWrap=false};
            smallStyle=new GUIStyle(textStyle) {fontSize=21,wordWrap=true};titleStyle=new GUIStyle(textStyle) {fontSize=43,fontStyle=FontStyle.Bold};
            buttonStyle=new GUIStyle(GUI.skin.button) {font=font,fontSize=25,wordWrap=true,alignment=TextAnchor.MiddleCenter,padding=new RectOffset(7,7,3,3)};
            buttonStyle.normal.background=buttonStyle.hover.background=buttonStyle.active.background=buttonStyle.focused.background=Texture2D.whiteTexture;
            buttonStyle.normal.textColor=buttonStyle.hover.textColor=buttonStyle.active.textColor=buttonStyle.focused.textColor=Color.white;
            buttonStyle.border=new RectOffset(0,0,0,0);
        }
        void Fill(Rect r,Color color) {GUI.color=color;GUI.DrawTexture(r,Texture2D.whiteTexture);GUI.color=Color.white;}
        void Text(string s,float x,float y,float w=1200,Color? color=null,GUIStyle style=null) {var st=style??textStyle;st.normal.textColor=color??Color.white;GUI.Label(new Rect(x,y,w,60),s,st);}
        bool B(string label,float x,float y,float w,float h=52,bool selected=false) {
            GUI.backgroundColor=selected?Lime:Panel;GUI.contentColor=selected?Bg:Color.white;
            bool hit=GUI.Button(new Rect(x,y,w,h),label,buttonStyle);GUI.backgroundColor=GUI.contentColor=Color.white;
            if(hit && soundEnabled && effects!=null) effects.PlayOneShot(beep);return hit;
        }
        void Hold(string label,float x,float y,float w,float h=78) {GUI.backgroundColor=Panel;GUI.contentColor=Color.white;GUI.Box(new Rect(x,y,w,h),label,buttonStyle);GUI.backgroundColor=Color.white;}
        void Circle(Vector2 p,float radius,Color color) {GUI.color=color;GUI.DrawTexture(new Rect(p.x-radius,p.y-radius,radius*2,radius*2),dot);GUI.color=Color.white;}
        void Line(Vector2 a,Vector2 b,Color color,float width=2) {var matrix=GUI.matrix;Vector2 d=b-a;GUIUtility.RotateAroundPivot(Mathf.Atan2(d.y,d.x)*Mathf.Rad2Deg,a);Fill(new Rect(a.x,a.y,d.magnitude,width),color);GUI.matrix=matrix;}
        void OnGUI() {
            Styles();float scale=Mathf.Min(Screen.width/1440f,Screen.height/810f);
            GUI.matrix=Matrix4x4.TRS(new Vector3((Screen.width-1440*scale)/2,(Screen.height-810*scale)/2,0),Quaternion.identity,new Vector3(scale,scale,1));
            Fill(new Rect(0,0,1440,810),Bg);
            if(!string.IsNullOrEmpty(fatal)) {Text("Не удалось запустить игру",80,150,1250,Coral,titleStyle);Text(fatal,80,240,1250,Color.white,smallStyle);if(B("Выход",80,500,300)) Application.Quit();return;}
            if(catalog==null || state==null) {Text("Загрузка…",80,200);return;}
            HandleKey(Event.current);
            GUI.enabled=!Modal;
            if(battle) DrawBattle();else DrawMenu();
            GUI.enabled=true;
            if(musicPanel) DrawMusic();else if(help) DrawHelp();else if(aimPanel) DrawAim();else if(arsenal) DrawArsenal();else if(forge) DrawForge();else if(paused) DrawPause();
            if(!string.IsNullOrEmpty(toast)) Text(toast,30,780,1250,Coral,smallStyle);
        }
        Color ArenaColor(int id) {Color[] colors={new Color(.05f,.11f,.18f),new Color(.22f,.09f,.13f),new Color(.08f,.09f,.22f),new Color(.07f,.2f,.13f),new Color(.1f,.23f,.32f),new Color(.2f,.15f,.1f),new Color(.33f,.15f,.13f),new Color(.05f,.2f,.26f),new Color(.03f,.22f,.13f),new Color(.24f,.19f,.22f),new Color(.1f,.13f,.24f),new Color(.2f,.1f,.3f)};return colors[id%colors.Length];}
        void Backdrop(int id) {
            Color color=ArenaColor(id);Fill(new Rect(0,105,1440,585),color);
            for(int i=0;i<38;i++) Circle(new Vector2(i*193%1440,150+i*73%430),i%5==0?2:1,new Color(.6f,.8f,.85f,.3f));
            for(int i=0;i<15;i++) {float x=i*110,top=350+(i*61+id*31)%160;Color dark=Color.Lerp(color,Bg,.32f);Fill(new Rect(x,top,75,690-top),dark);if(id==8) Circle(new Vector2(x+35,top),70,Color.Lerp(color,Color.green,.12f));if(id==7 || id==10) Line(new Vector2(x+35,top),new Vector2(x+140,top+40),Muted,5);if(id==11) Line(new Vector2(x,top+100),new Vector2(x+30,top),Cyan,2);}
        }
        void Hero(Rect rect,int id,int team=0) {if(heroes==null) {Fill(rect,Lime);return;}GUI.DrawTextureWithTexCoords(rect,heroes,new Rect(id/7f,team==0?.5f:0,1/7f,.5f),true);}
        void DrawMenu() {
            Backdrop(arena);Text("RIFT / ARTILLERY",55,42,800,Lime,titleStyle);Text("WINDOWS · 0.3.1 · 12 КАРТ · 100 ПРЕДМЕТОВ",58,105,900,Muted);
            if(B(musicEnabled?"Музыка: вкл":"Музыка: выкл",1020,35,180,46)) {musicEnabled=!musicEnabled;PlayerPrefs.SetInt("music",musicEnabled?1:0);}
            if(B("Треки",1212,35,165,46)) musicPanel=true;
            if(B(Screen.fullScreen?"Оконный / F11":"На весь экран / F11",1020,92,357,46)) ToggleFullscreen();
            Text("Маленький отряд. Большой разлом.",58,171,1100,Color.white,titleStyle);
            Text("A / D — ходьба · пробел — прыжок · мышь — прицел · Enter — огонь",60,230,1200,Muted);
            for(int i=0;i<catalog.arenas.Length;i++) if(B(catalog.arenas[i].name,58+i%4*236,290+i/4*77,220,65,arena==i)) arena=i;
            Hero(new Rect(1060,220,235,300),race);
            if(B(bot?"Дуэль / ИИ":"Дуэль / вдвоём",58,545,270,58,mode==0)) {if(mode==0) bot=!bot;mode=0;}
            if(B("Охота на босса",344,545,250,58,mode==1)) mode=1;
            if(B("Канатная гонка",610,545,250,58,mode==2)) mode=2;
            if(B("Босс: "+catalog.bosses[boss].name,58,620,800,58,mode==1)) {boss=(boss+1)%catalog.bosses.Length;mode=1;}
            if(B("Отряд / кузница",1020,550,350,60)) forge=true;
            if(B("НАЧАТЬ БОЙ",58,710,380,65,true)) NewBattle();
            if(state.saveExists && B("ПРОДОЛЖИТЬ",454,710,350,65)) {Act("load");battle=true;resultRecorded=false;lastTurn=-1;}
            if(B("Правила",1030,640,160,50)) help=true;if(B("Выход",1210,640,160,50)) Application.Quit();
            Text("59 секунд на ход",880,705,400,Cyan);Text("Побед: "+PlayerPrefs.GetInt("wins")+" · Материалы: "+PlayerPrefs.GetInt("scrap",150),880,755,500,Lime,smallStyle);
        }
        Vector2 W(float x,float y) {return new Vector2(720+(x-cameraX)*zoom,411+(y-cameraY)*zoom-205);}
        void DrawBattle() {
            Backdrop(state.arena);GUI.BeginGroup(new Rect(0,205,1440,483));
            Vector2 corner=W(0,0);if(terrain!=null) GUI.DrawTexture(new Rect(corner.x,corner.y,2880*zoom,1440*zoom),terrain,ScaleMode.StretchToFill,true);
            Vector2 water=W(0,state.water);Fill(new Rect(0,water.y,1440,Mathf.Max(0,483-water.y)),new Color(.08f,.32f,.4f,.8f));
            foreach(var h in state.hazards) Circle(W(h.x,h.y),h.radius*zoom,new Color(h.effect==1?.5f:1,h.effect==0?.3f:.65f,h.effect==2?1:.2f,.22f));
            foreach(var warning in state.mineWarnings) Circle(W(warning.x,warning.y),warning.radius*zoom,new Color(1,.3f,.2f,.16f));
            foreach(var point in state.trajectory) Circle(W(point.x,point.y),2,Lime);
            foreach(var d in state.devices) {Vector2 p=W(d.x,d.y);if(d.kind==3) Circle(p,135*zoom,new Color(.3f,.9f,1,.12f));Fill(new Rect(p.x-17*zoom,p.y-24*zoom,34*zoom,36*zoom),d.kind==5?Cyan:Muted);}
            if(state.mode==2) {int i=0;foreach(var target in state.targets) {var p=W(target.x,target.y);Circle(p,45*zoom,new Color(.5f,1,.3f,.2f));Text((++i).ToString(),p.x-10,p.y-15,50,Lime);}}
            foreach(var beam in state.beams) Line(W(beam.x,beam.y),W(beam.x2,beam.y2),Cyan,3);
            foreach(var shot in state.shots) {Vector2 p=W(shot.x,shot.y);Circle(p,6*zoom,shot.fragment?Coral:Lime);if(shot.fuse>0) Text(Mathf.CeilToInt(shot.fuse).ToString(),p.x+8,p.y-30,40,Color.white,smallStyle);}
            if(state.rope.Length>0) {Vector2 from=W(Active.x,Active.y-24);for(int i=state.rope.Length-1;i>=0;i--) {var p=W(state.rope[i].x,state.rope[i].y);Line(from,p,Color.white,2);from=p;}}
            labelCount=0;
            foreach(var f in state.fighters.Where(f=>f.alive)) {
                Vector2 p=W(f.x,f.y);bool big=state.mode==1 && f.id==2;float width=big?210:72,height=big?245:95;
                Rect rect=new Rect(p.x-width*zoom/2,p.y-(height-17)*zoom,width*zoom,height*zoom);
                if(rect.xMax<0 || rect.xMin>1440 || rect.yMax<0 || rect.yMin>483) continue;
                if(big && bossArt!=null) GUI.DrawTextureWithTexCoords(rect,bossArt,new Rect(state.boss/4f,0,.25f,1),true);else Hero(rect,f.race,f.team);
                if(f.shield>0) Circle(new Vector2(p.x,p.y-35*zoom),50*zoom,new Color(.3f,.9f,1,.12f));
                if(f.jetboard && f.fuel>0) Fill(new Rect(p.x-35*zoom,p.y,70*zoom,8*zoom),Cyan);
                if(f.id==state.active) Fill(new Rect(p.x-25*zoom,p.y+4,50*zoom,4),Lime);
                float labelY=Mathf.Clamp(p.y-height*zoom-45,38,407);float labelX=Mathf.Clamp(p.x-110,8,1212);
                for(int attempt=0;attempt<4;attempt++) {Rect proposed=new Rect(labelX,labelY,220,65);if(!placedLabels.Take(labelCount).Any(r=>r.Overlaps(proposed))) break;labelY=labelY>110?labelY-75:labelY+150;}
                placedLabels[labelCount++]=new Rect(labelX,labelY,220,65);Fill(new Rect(labelX,labelY,220,38),new Color(.03f,.05f,.08f,.9f));
                Text(f.name+" · "+Mathf.CeilToInt(f.hp),labelX+6,labelY+3,220,f.team==0?Lime:Coral,smallStyle);
                Fill(new Rect(labelX+15,labelY+43,190,6),Bg);Fill(new Rect(labelX+15,labelY+43,190*f.hp/f.maxHp,6),f.team==0?Lime:Coral);
            }
            if(state.control && state.phase==0 && !catalog.weapons[state.weapon].targeted) {Vector2 p=W(Active.x,Active.y-20);float r=angle*Mathf.Deg2Rad;Line(p,p+new Vector2(Mathf.Cos(r),Mathf.Sin(r))*70*zoom,Lime,3);Circle(p+new Vector2(Mathf.Cos(r),Mathf.Sin(r))*90*zoom,4,Color.white);}
            GUI.EndGroup();
            Fill(new Rect(0,0,1440,105),Bg);Text(catalog.arenas[state.arena].name,25,12,720,Muted);Text("ХОД "+state.turn+" / "+Active.name,25,51,600,Color.white);
            Text("Искра "+Mathf.CeilToInt(state.fighters.Where(f=>f.team==0).Sum(f=>f.hp)),580,30,220,Lime);Text("Фантом "+Mathf.CeilToInt(state.fighters.Where(f=>f.team==1).Sum(f=>f.hp)),810,30,230,Coral);
            Text(state.phase==0?Mathf.CeilToInt(state.time)+" сек":"Отход "+Mathf.CeilToInt(state.retreat),1050,25,200,Cyan);
            if(B("Пауза",1260,22,155,60)) paused=true;
            Fill(new Rect(20,115,1400,42),Panel);Text(state.message,30,120,1380,Color.white,smallStyle);
            if(B(overview||manual?"К бойцу":"Обзор",20,165,145,40)) {if(overview||manual) {overview=manual=false;zoom=1.05f;} else overview=true;}
            if(B("−",180,165,50,40)) {overview=false;zoom=Mathf.Max(.42f,zoom-.15f);}if(B("+",240,165,50,40)) {overview=false;zoom=Mathf.Min(1.6f,zoom+.15f);}
            if(B("Сменить бойца",310,165,240,40)) {var ally=state.fighters.FirstOrDefault(f=>f.alive && f.team==Active.team && f.id!=state.active);if(ally!=null) Act("switch",",\"id\":"+ally.id);}
            if(state.mode==1) Text(state.bossWarning,570,170,830,Coral,smallStyle);
            var enemy=state.fighters.Where(f=>f.alive && f.team!=Active.team).OrderBy(f=>Mathf.Abs(f.x-Active.x)).FirstOrDefault();
            if(!overview && enemy!=null && Mathf.Abs((enemy.x-cameraX)*zoom)>690 && B("Противник →",1160,220,255,50)) {manual=true;overview=false;cameraX=enemy.x;cameraY=enemy.y-110;}
            if(terrain!=null) GUI.DrawTexture(new Rect(1100,580,316,95),terrain,ScaleMode.StretchToFill,true);
            foreach(var f in state.fighters.Where(f=>f.alive)) Circle(new Vector2(1100+f.x/2880*316,580+f.y/1440*95),3,f.team==0?Lime:Coral);
            Fill(new Rect(0,690,1440,120),Bg);Hold("→",95,710,65);Hold("←",20,710,65);Hold("↓",470,710,45);Hold("↑",420,710,45);
            if(B("Прыжок",175,710,110,78)) Act("jump");if(B(state.rope.Length>0?"Отцепить":"Канат",295,710,115,78)) Act("rope");
            if(B("Прицел "+Mathf.RoundToInt(angle)+"° / "+Mathf.RoundToInt(power*100)+"%",530,710,360,78) && state.control) aimPanel=true;
            if(B(catalog.weapons[state.weapon].name,905,710,185,78) && state.phase==0 && state.human) {arsenal=true;category=catalog.weapons[state.weapon].category;page=0;search="";}
            if(B("Навык",1100,710,100,78)) Act("ability");if(B(catalog.weapons[state.weapon].targeted?"Цель на карте":"ОГОНЬ",1210,710,205,78,true) && !catalog.weapons[state.weapon].targeted) Act("fire");
            if(state.phase==3) {PanelBox("БОЙ ЗАВЕРШЁН");Text(state.winner==0?"Победа Искры":state.winner==1?"Победа Фантома":"Ничья",260,270,900,Lime,titleStyle);if(B("Реванш",260,410,920,65,true)) NewBattle();if(B("В меню",260,505,920,60)) SaveMenu();}
        }
        void PanelBox(string title) {Fill(new Rect(0,0,1440,810),new Color(0,.02f,.05f,.88f));Fill(new Rect(160,90,1120,650),Panel);Text(title,200,120,1040,Lime,titleStyle);}
        void DrawArsenal() {
            PanelBox("АРСЕНАЛ / "+catalog.weapons.Length);
            for(int i=0;i<catalog.categories.Length;i++) if(B(catalog.categories[i].name,200+i%5*210,185+i/5*48,198,40,category==i)) {category=i;page=0;search="";}
            Text("Поиск",1050,230,200,Muted,smallStyle);search=GUI.TextField(new Rect(1040,265,205,36),search,60);
            Entry[] list=catalog.weapons.Where(w=>(string.IsNullOrWhiteSpace(search)?w.category==category:w.name.IndexOf(search,StringComparison.OrdinalIgnoreCase)>=0)).ToArray();int pages=Math.Max(1,(list.Length+5)/6);page=Mathf.Clamp(page,0,pages-1);
            for(int n=0;n<6 && page*6+n<list.Length;n++) {Entry w=list[page*6+n];float x=200+n%2*540,y=315+n/2*100;int ammo=Active.ammo[w.id];if(B(w.name+"  ×"+(ammo<0?"∞":ammo.ToString()),x,y,510,55,state.weapon==w.id)) {Act("select",",\"weapon\":"+w.id);arsenal=false;}Text(w.description,x+4,y+60,500,Muted,smallStyle);}
            if(B("←",200,650,70)) page=(page-1+pages)%pages;Text((page+1)+" / "+pages,285,660,100);
            if(B("→",380,650,70)) page=(page+1)%pages;if(B("Таймер "+state.grenadeTimer+" с",470,650,230)) Act("fuse");if(B(state.hardBounce?"Жёсткий отскок":"Мягкий отскок",715,650,260)) Act("bounce");if(B("Закрыть",990,650,255)) arsenal=false;
        }
        void DrawAim() {
            PanelBox("ТОЧНЫЙ ПРИЦЕЛ");Text("Угол "+Mathf.RoundToInt(angle)+"°",210,225,500,Cyan);angle=GUI.HorizontalSlider(new Rect(320,285,800,30),angle,-180,180);
            if(B("−5°",210,335,100,60)) angle=Mathf.Max(-180,angle-5);if(B("−1°",325,335,100,60)) angle=Mathf.Max(-180,angle-1);
            if(B("Развернуть",440,335,260,60)) angle=angle<=0?-180-angle:180-angle;if(B("+1°",715,335,100,60)) angle=Mathf.Min(180,angle+1);if(B("+5°",830,335,100,60)) angle=Mathf.Min(180,angle+5);if(B("Вверх 45°",945,335,260,60)) angle=Mathf.Cos(angle*Mathf.Deg2Rad)>=0?-45:-135;
            Text("Сила "+Mathf.RoundToInt(power*100)+"%",210,440,500,Lime);power=GUI.HorizontalSlider(new Rect(320,500,800,30),power,0,1);
            if(B("−5%",210,475,95)) power=Mathf.Max(0,power-.05f);if(B("+5%",1140,475,95)) power=Mathf.Min(1,power+.05f);
            Text("Мышь на поле — направление, колесо — сила.",210,565,990,Muted);if(B("Готово",940,650,280,60,true)) {CommitAim();aimPanel=false;}
        }
        void DrawPause() {PanelBox("ПАУЗА");if(B(Screen.fullScreen?"Оконный / F11":"На весь экран / F11",880,120,360,45)) ToggleFullscreen();if(B("Продолжить бой",420,250,600,60,true)) paused=false;if(B("Пропустить ход",420,325,600,55)) {Act("skip");paused=false;}if(B("Сохранить и в меню",420,395,600,55)) SaveMenu();if(B("Выбор музыки",420,465,600,55)) musicPanel=true;if(B(soundEnabled?"Эффекты: вкл":"Эффекты: выкл",420,535,600,55)) {soundEnabled=!soundEnabled;PlayerPrefs.SetInt("sound",soundEnabled?1:0);}if(B("Правила",420,605,600,55)) help=true;}
        void DrawMusic() {PanelBox("МУЗЫКА / "+catalog.tracks.Length);if(B("По локации",200,180,1040,50,track<0)) {ChooseMusic(-1);}for(int i=0;i<catalog.tracks.Length;i++) if(B(catalog.tracks[i].name,200+i%4*265,245+i/4*82,250,65,track==i)) {ChooseMusic(i);}if(B("Предыдущий",200,605,300)) {ChooseMusic(((track<0?arena:track)-1+catalog.tracks.Length)%catalog.tracks.Length);}if(B("Следующий",520,605,300)) {ChooseMusic(((track<0?arena:track)+1)%catalog.tracks.Length);}Text("Громкость "+Mathf.RoundToInt(music.volume*100)+"%",200,660,300,Muted,smallStyle);float volume=GUI.HorizontalSlider(new Rect(200,705,590,25),music.volume,0,1);if(Mathf.Abs(volume-music.volume)>.001f) {music.volume=volume;PlayerPrefs.SetFloat("musicVolume",volume);}if(B(musicEnabled?"Выкл. музыку":"Вкл. музыку",610,650,270,45)) {musicEnabled=!musicEnabled;PlayerPrefs.SetInt("music",musicEnabled?1:0);}if(B("Готово",940,665,300,55,true)) {PlayerPrefs.Save();musicPanel=false;}}
        bool Buy(string key,int index,int price) {int bits=PlayerPrefs.GetInt(key,1);if((bits&(1<<index))!=0) return true;int scrap=PlayerPrefs.GetInt("scrap",150);if(scrap<price) {toast="Не хватает материалов";return false;}PlayerPrefs.SetInt("scrap",scrap-price);PlayerPrefs.SetInt(key,bits|(1<<index));return true;}
        void DrawForge() {
            PanelBox("ОТРЯД / КУЗНИЦА");Text("Материалы: "+PlayerPrefs.GetInt("scrap",150),900,130,340,Muted);
            for(int i=0;i<catalog.races.Length;i++) {Hero(new Rect(200+i*150,190,130,160),i);if(B(catalog.races[i].name,200+i*150,350,140,48,race==i)) race=i;}
            for(int i=0;i<catalog.hats.Length;i++) if(B(catalog.hats[i].name,200+i*263,425,248,50,hat==i) && Buy("ownedHats",i,30)) hat=i;
            for(int i=0;i<catalog.artifacts.Length;i++) if(B(catalog.artifacts[i].name,200+i*263,500,248,50,artifact==i) && Buy("ownedArtifacts",i,40)) artifact=i;
            for(int i=0;i<catalog.upgrades.Length;i++) if(B(catalog.upgrades[i].name,200+i*350,575,335,50,upgrade==i) && Buy("ownedUpgrades",i,60)) upgrade=i;
            if(B("Сохранить отряд",200,660,1040,55,true)) {PlayerPrefs.SetInt("race",race);PlayerPrefs.SetInt("hat",hat);PlayerPrefs.SetInt("artifact",artifact);PlayerPrefs.SetInt("upgrade",upgrade);PlayerPrefs.Save();forge=false;}
        }
        void DrawHelp() {PanelBox("УПРАВЛЕНИЕ");string[] lines={"A / D, стрелки ← → или кнопки — движение; пробел — прыжок.","Мышь / ↑ ↓ — прицел; колесо / + − — сила; Enter — огонь.","Кнопка «Прицел» — длинные ползунки, шаг 1° и 5°.","Q — канат; W / S — длина троса и тяга леталки; E — навык.","R — арсенал; Tab — союзник; правая кнопка мыши — камера.","Телепорт и аптечка расходуют запас, но оставляют атаку.","На ход 59 секунд. После атаки — три секунды на отход.","F11 / Alt+Enter — полный экран; Esc — пауза. Shift+колесо — масштаб."};for(int i=0;i<lines.Length;i++) Text(lines[i],200,210+i*53,1040,Color.white);if(B("Понятно",200,665,1040,55,true)) help=false;}
    }
}
