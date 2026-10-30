using System;
using System.Linq;
using UnityEngine;

namespace Rift {
    public partial class RiftGame {
        bool shop;int shopGroup,shopPage;
        [Serializable] class ShopSync {public string cmd="market",action="sync",stock;public int wallet;}
        void SyncShop() {Send(JsonUtility.ToJson(new ShopSync {wallet=PlayerPrefs.GetInt("scrap",150),stock=PlayerPrefs.GetString("shopStock","")}));if(state.ok) toast="";}
        void SaveShop() {PlayerPrefs.SetInt("scrap",state.shopWallet);PlayerPrefs.SetString("shopStock",state.shopStock??"");PlayerPrefs.Save();}
        void OpenShop() {SyncShop();shop=true;toast="";shopPage=0;}
        void DrawShop() {
            PanelBox("МАГАЗИН / СНАРЯЖЕНИЕ");Text("МАТЕРИАЛЫ: "+state.shopWallet,900,128,350,Lime,smallStyle);
            string[] groups={"Боеприпасы","Снабжение","Ауры"};
            for(int i=0;i<3;i++) if(B(groups[i],200+i*350,185,330,45,shopGroup==i)) {shopGroup=i;shopPage=0;toast="";}
            bool supplies=PlayerPrefs.GetInt("useSupplies",1)==1;
            if(B("Наборы на новый бой: "+(supplies?"ВКЛ":"ВЫКЛ"),200,242,735,40)) {PlayerPrefs.SetInt("useSupplies",supplies?0:1);PlayerPrefs.Save();}
            if(B("Аура: Лайм",960,242,285,40)) {Send("{\"cmd\":\"market\",\"action\":\"equip\",\"aura\":0}");if(state.ok) SaveShop();}
            Entry[] list=catalog.offers.Where(o=>o.group==shopGroup).ToArray();int pages=Math.Max(1,(list.Length+5)/6);shopPage=Mathf.Clamp(shopPage,0,pages-1);
            for(int i=0;i<6 && shopPage*6+i<list.Length;i++) {
                Entry o=list[shopPage*6+i];float x=200+i%2*535,y=305+i/2*110;
                Fill(new Rect(x,y,510,102),Bg);Text(o.name,x+12,y+7,485,Color.white,smallStyle);
                Text(o.description,x+12,y+35,485,Muted,new GUIStyle(smallStyle){fontSize=16,wordWrap=true});
                bool owned=o.aura>=0 && state.shopOwned[o.id]>0;
                Text(o.aura>=0?(state.selectedAura==o.aura?"Выбрана":"Аура"):"Склад: "+state.shopOwned[o.id]+" / 9",x+12,y+72,250,Cyan,new GUIStyle(smallStyle){fontSize=18});
                if(o.aura>=0) {uint color=unchecked((uint)catalog.auraColors[o.aura]);Circle(new Vector2(x+280,y+83),10,new Color32((byte)(color>>16),(byte)(color>>8),(byte)color,255));}
                if(B(owned?(state.selectedAura==o.aura?"Выбрано":"Надеть"):"Купить / "+o.price,x+330,y+68,165,32)) {
                    Send("{\"cmd\":\"market\",\"action\":\""+(owned?"equip":"buy")+"\",\""+(owned?"aura":"offer")+"\":"+(owned?o.aura:o.id)+"}");
                    if(state.ok) {SaveShop();toast=owned?"Аура выбрана":"Куплено: "+o.name;}
                }
            }
            Text("По одному набору каждого типа на новый бой. Продолжение не тратит склад.",200,642,1040,Muted,new GUIStyle(smallStyle){fontSize=19});
            if(B("←",200,685,90,45)) shopPage=(shopPage-1+pages)%pages;Text((shopPage+1)+" / "+pages,315,690,130);
            if(B("→",460,685,90,45)) shopPage=(shopPage+1)%pages;if(B("Готово",945,685,300,45,true)) shop=false;
        }
        Color AuraColor() {
            int selected=state.selectedAura;
            uint color=unchecked((uint)catalog.auraColors[Mathf.Clamp(selected,0,catalog.auraColors.Length-1)]);
            return new Color32((byte)(color>>16),(byte)(color>>8),(byte)color,255);
        }
    }
}
