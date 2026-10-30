package com.riftartillery.core

enum class ShopGroup(val title:String) { AMMO("Боеприпасы"), SUPPORT("Снабжение"), AURA("Ауры") }
data class Offer(val id:Int,val title:String,val description:String,val price:Int,val group:ShopGroup,val ammo:Map<Weapon,Int> = emptyMap(),val aura:Int= -1)
data class Purchase(val balance:Int,val inventory:String)

/** Shared, bounded shop rules. Platforms commit wallet and inventory in one preference transaction. */
object Market {
    val auraNames=listOf("Лайм","Ледяной свет","Пламя","Аметист","Золото","Розовый неон")
    val auraColors=listOf(0xffb1f070.toInt(),0xff65d9ff.toInt(),0xffff8a50.toInt(),0xffbd8aff.toInt(),0xffffda65.toInt(),0xffff81c9.toInt())
    val offers:List<Offer> = buildList {
        fun pack(name:String,price:Int,group:ShopGroup,vararg items:Pair<Weapon,Int>) {
            add(Offer(size,name,items.joinToString(" · ") {"${it.first.title} +${it.second}"},price,group,items.toMap()))
        }
        pack("Штурмовой комплект",30,ShopGroup.AMMO,Weapon.AK47 to 3,Weapon.BURST_RIFLE to 3)
        pack("Ракетный ящик",45,ShopGroup.AMMO,Weapon.HE_ROCKET to 2,Weapon.BOLT_ROCKET to 3)
        pack("Снайперский комплект",45,ShopGroup.AMMO,Weapon.SNIPER to 2,Weapon.HEAVY_SNIPER to 2)
        pack("Дробь и осколки",35,ShopGroup.AMMO,Weapon.AUTO_SHOTGUN to 3,Weapon.SHRAPNEL_CANNON to 2)
        pack("Пиротехника",40,ShopGroup.AMMO,Weapon.MOLOTOV to 3,Weapon.FLAME_ROCKET to 2)
        pack("Крио-комплект",40,ShopGroup.AMMO,Weapon.ICE_SHARDS to 2,Weapon.FROST_THROWER to 2)
        pack("Энергетический ящик",55,ShopGroup.AMMO,Weapon.ARC_THROWER to 2,Weapon.LASER_CUTTER to 2)
        pack("Гравитационный комплект",60,ShopGroup.AMMO,Weapon.BLACK_HOLE to 2,Weapon.RICOCHET_ORB to 2)
        pack("Фруктовый залп",50,ShopGroup.AMMO,Weapon.WATERMELON to 2,Weapon.PINEAPPLE to 2)
        pack("Дроны и кислота",55,ShopGroup.AMMO,Weapon.DRONE_STRIKE to 2,Weapon.ACID_ROCKET to 3)
        pack("Полевая медицина",35,ShopGroup.SUPPORT,Weapon.HEAL to 2,Weapon.HEAL_GRENADE to 2)
        pack("Инженерный набор",45,ShopGroup.SUPPORT,Weapon.HEAL_TOTEM to 1,Weapon.ARC_TURRET to 1,Weapon.BARRIER to 2)
        pack("Манёвренность",40,ShopGroup.SUPPORT,Weapon.TELEPORT to 2,Weapon.JETBOARD to 1)
        pack("Защита отряда",35,ShopGroup.SUPPORT,Weapon.DEFENDER to 2,Weapon.SHIELD_MINE to 2)
        for(i in 1..5) add(Offer(size,"Аура: ${auraNames[i]}","Постоянный цвет кольца под бойцами. Без бонусов к урону.",30+i*10,ShopGroup.AURA,aura=i))
    }
    class Stock(val packs:IntArray=IntArray(offers.size),var owned:Int=1,var selected:Int=0) {
        fun encode()="1;$owned;$selected;"+packs.joinToString(",")
    }
    fun decode(text:String):Stock {
        if(text.isEmpty()) return Stock()
        return try {
        require(text.length<=1024)
        val parts=text.split(';');require(parts.size==4 && parts[0]=="1")
        val owned=parts[1].toInt().also {require(it in 1..63 && it and 1==1)}
        val selected=parts[2].toInt().also {require(it in 0..5 && owned and (1 shl it)!=0)}
        val counts=parts[3].split(',');require(counts.size==offers.size)
        Stock(IntArray(offers.size) {counts[it].toInt().also {n->require(n in 0..9)}},owned,selected)
        } catch(_:Exception) {Stock()}
    }
    fun buy(balance:Int,text:String,id:Int):Purchase? {
        val offer=offers.getOrNull(id) ?: return null;val stock=decode(text)
        if(balance<offer.price || balance<0) return null
        if(offer.aura>=0) {
            if(stock.owned and (1 shl offer.aura)!=0) return null
            stock.owned=stock.owned or (1 shl offer.aura);stock.selected=offer.aura
        } else {if(stock.packs[id]>=9) return null;stock.packs[id]++}
        return Purchase(balance-offer.price,stock.encode())
    }
    fun equip(text:String,aura:Int):String? {
        val stock=decode(text);if(aura !in 0..5 || stock.owned and (1 shl aura)==0) return null
        stock.selected=aura;return stock.encode()
    }
    fun apply(game:Game,text:String):String {
        val stock=decode(text)
        if(game.mode==GameMode.ROPE_RACE) return stock.encode()
        for(offer in offers.filter {it.aura<0 && stock.packs[it.id]>0}) {
            for(f in game.fighters.filter {it.alive && (!game.againstBot || it.team==0)}) for((w,n) in offer.ammo) {
                if(f.ammo.getValue(w)>=0) f.ammo[w]=(f.ammo.getValue(w)+n).coerceAtMost(99)
            }
            stock.packs[offer.id]--
        }
        return stock.encode()
    }
    fun reward(mode:GameMode,winner:Int?)=when(winner) {0->if(mode==GameMode.BOSS) 180 else 60;1->20;else->30}
}
