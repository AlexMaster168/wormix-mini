using System.Linq;
using UnityEngine;

namespace Rift {
    public partial class RiftGame {
        bool heldLeft,heldRight,heldUp,heldDown;
        Vector2 previousPointer;
        int windowWidth=1280,windowHeight=720;
        Vector2 Pointer {
            get {
                float scale=Mathf.Min(Screen.width/1440f,Screen.height/810f);
                return new Vector2((Input.mousePosition.x-(Screen.width-1440*scale)/2)/scale,
                    (Screen.height-Input.mousePosition.y-(Screen.height-810*scale)/2)/scale);
            }
        }
        void ToggleFullscreen() {
            if(Screen.fullScreen) Screen.SetResolution(windowWidth,windowHeight,FullScreenMode.Windowed);
            else {windowWidth=Screen.width;windowHeight=Screen.height;Screen.SetResolution(Screen.currentResolution.width,Screen.currentResolution.height,FullScreenMode.FullScreenWindow);}
        }
        void UpdatePointer() {
            Vector2 p=Pointer;
            mouseMove=mouseReel=0;
            if(battle && !Modal && state.control && Application.isFocused) {
                if(Input.GetMouseButton(0)) {
                    mouseMove=(new Rect(95,710,65,78).Contains(p)?1:0)-(new Rect(20,710,65,78).Contains(p)?1:0);
                    mouseReel=(new Rect(470,710,45,78).Contains(p)?1:0)-(new Rect(420,710,45,78).Contains(p)?1:0);
                }
                bool field=p.x>=0 && p.x<1440 && p.y>215 && p.y<680 && !new Rect(1160,220,255,50).Contains(p);
                if(field && Input.GetMouseButtonDown(0) && new Rect(1100,580,316,95).Contains(p)) {
                    overview=false;manual=true;cameraX=(p.x-1100)/316*2880;cameraY=(p.y-580)/95*1440;
                } else if(field && Input.GetMouseButton(0) && GUIUtility.hotControl==0) {
                    float wx=cameraX+(p.x-720)/zoom,wy=cameraY+(p.y-411)/zoom;
                    if(!catalog.weapons[state.weapon].targeted) {angle=Mathf.Atan2(wy-(Active.y-20),wx-Active.x)*Mathf.Rad2Deg;CommitAim();}
                }
                if(field && Input.GetMouseButton(1) && !Input.GetMouseButtonDown(1)) {
                    manual=true;overview=false;Vector2 delta=p-previousPointer;
                    cameraX=Mathf.Clamp(cameraX-delta.x/zoom,0,2880);cameraY=Mathf.Clamp(cameraY-delta.y/zoom,0,1440);
                }
            }
            previousPointer=p;
        }
        bool HandleFieldClick(Vector2 p) {
            if(!battle || Modal || !state.control || GUIUtility.hotControl!=0 || p.x<0 || p.x>=1440 || p.y<=215 || p.y>=680 || new Rect(1160,220,255,50).Contains(p) || new Rect(1100,580,316,95).Contains(p)) return false;
            float wx=cameraX+(p.x-720)/zoom,wy=cameraY+(p.y-411)/zoom;
            if(catalog.weapons[state.weapon].targeted) Act("fire",",\"x\":"+F(wx)+",\"y\":"+F(wy));
            else {angle=Mathf.Atan2(wy-(Active.y-20),wx-Active.x)*Mathf.Rad2Deg;CommitAim();}
            return true;
        }
        void HandleKey(Event e) {
            if(e.type==EventType.MouseDown && e.button==0 && HandleFieldClick(Pointer)) {e.Use();return;}
            if(e.type!=EventType.KeyDown && e.type!=EventType.KeyUp) return;
            bool down=e.type==EventType.KeyDown;
            if(!down && e.keyCode==KeyCode.None) heldLeft=heldRight=heldUp=heldDown=false;
            if(down && (e.keyCode==KeyCode.F11 || (e.keyCode==KeyCode.Return && e.alt))) {ToggleFullscreen();e.Use();return;}
            char key=char.ToLowerInvariant(e.character);
            bool left=e.keyCode==KeyCode.A||e.keyCode==KeyCode.LeftArrow||key=='ф';
            bool right=e.keyCode==KeyCode.D||e.keyCode==KeyCode.RightArrow||key=='в';
            bool up=e.keyCode==KeyCode.W||key=='ц',back=e.keyCode==KeyCode.S||key=='ы';
            // KeyUp can omit character; physical KeyCode still releases the corresponding key.
            if(left) heldLeft=down;if(right) heldRight=down;if(up) heldUp=down;if(back) heldDown=down;
            if(!down || Modal) return;
            if(!battle) {if(e.keyCode==KeyCode.Return||e.keyCode==KeyCode.KeypadEnter) {NewBattle();e.Use();}return;}
            if(!state.control || state.phase==3) return;
            if(e.keyCode==KeyCode.Space) {Act("jump");e.Use();}
            else if(e.keyCode==KeyCode.F||key=='а') {Act("interact");e.Use();}
            else if(e.keyCode==KeyCode.Q||key=='й') {Act("rope");e.Use();}
            else if(e.keyCode==KeyCode.E||key=='у') {Act("ability");e.Use();}
            else if(e.keyCode==KeyCode.Tab) {var ally=state.fighters.FirstOrDefault(f=>f.alive && f.team==Active.team && f.id!=state.active);if(ally!=null) Act("switch",",\"id\":"+ally.id);e.Use();}
            else if((e.keyCode==KeyCode.R||key=='к') && state.phase==0 && state.human) {arsenal=true;category=catalog.weapons[state.weapon].category;page=0;search="";e.Use();}
            else if(e.keyCode==KeyCode.Return||e.keyCode==KeyCode.KeypadEnter) {
                CommitAim();if(catalog.weapons[state.weapon].targeted) {Vector2 p=Pointer;Act("fire",",\"x\":"+F(cameraX+(p.x-720)/zoom)+",\"y\":"+F(cameraY+(p.y-411)/zoom));}else Act("fire");e.Use();
            }
        }
    }
}
