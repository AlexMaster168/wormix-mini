package com.riftartillery.core

enum class Arena(val title:String,val subtitle:String,val gravity:Float) {
    NEON("Неоновый мегаполис", "Крыши, мосты и трансформаторы",290f),
    VOLCANO("Храм древних драконов", "Обсидиан, лава и гейзеры",310f),
    ORBIT("Станция «Титан»", "Астероиды и низкая гравитация",180f),
    CHEMICAL("Завод «Химпром»", "Цистерны, трубы и кислота",290f),
    ARCTIC("База «Полюс»", "Ледяные гроты и скользкие склоны",290f),
    SCRAP("Кладбище мехов", "Остовы машин и магнитный кран",290f),
    CANYON("Песчаный каньон", "Красные скалы и каменные уступы",300f),
    HARBOR("Затопленный порт", "Причалы, контейнеры и мостки",290f)
}
enum class Race(val title:String,val hp:Int,val resist:Float) {
    DRAGON("Драконид",100,.10f), CYBORG("Киборг",110,.15f), MUTANT("Мутант",95,0f),
    DEMON("Демон",100,0f), MERCENARY("Наёмник",100,.05f), UNDEAD("Нежить",100,0f), PHANTOM("Фантом",90,0f)
}
enum class Category(val title:String) { BALLISTIC("Ракеты"), GRENADE("Гранаты"), ELEMENTAL("Стихии"), GUN("Огнестрел"), ENGINEER("Инженерия"), CREATURE("Существа"), MELEE("Ближний бой"), GLOBAL("Авиация"), GADGET("Гаджеты") }
enum class Kind { IMPACT, BOUNCE, BEAM, DEPLOY, MELEE, GLOBAL, UTILITY, GUIDED, WALKER }
enum class DamageType { EXPLOSION, BULLET, FIRE, POISON, ELECTRIC, ACID, MELEE }
enum class Effect { BURN, POISON, FREEZE, BLEED, STUN, BLIND, CORROSION }
enum class Weapon(val title:String,val damage:Float,val radius:Float,val stock:Int,val speed:Float,val fuse:Float,
                  val category:Category,val kind:Kind,val description:String) {
    ROCKET("Базука",50f,70f,-1,800f,0f,Category.BALLISTIC,Kind.IMPACT,"Баллистика, ветер и кратер"),
    GRENADE("Граната",55f,75f,5,650f,3f,Category.GRENADE,Kind.BOUNCE,"Таймер 1–5 с; регулируемый отскок"),
    CLUSTER("Кассетная",25f,60f,2,620f,2.5f,Category.GRENADE,Kind.BOUNCE,"Пять самостоятельных осколков"),
    DYNAMITE("Динамит",75f,120f,2,0f,5f,Category.ENGINEER,Kind.BOUNCE,"Установка рядом; успей отойти"),
    SNIPER("Снайперская винтовка",55f,0f,3,0f,0f,Category.GUN,Kind.BEAM,"Пробивает двух; выстрел в голову 70"),
    HEAL("Аптечка / антидот",0f,0f,2,0f,0f,Category.GADGET,Kind.UTILITY,"+50 HP, снимает огонь и яд"),
    TELEPORT("Телепорт",0f,0f,2,0f,0f,Category.GADGET,Kind.UTILITY,"Выбери свободную точку"),
    CRYO("Крио-базука",35f,90f,3,780f,0f,Category.BALLISTIC,Kind.IMPACT,"Замораживает, сохраняет землю"),
    HOMING("Самонаводящаяся",45f,65f,2,600f,0f,Category.BALLISTIC,Kind.GUIDED,"Отметь цель; ракета доворачивает"),
    MORTAR("Мортира",30f,55f,3,730f,0f,Category.BALLISTIC,Kind.IMPACT,"Разбрасывает четыре осколка"),
    BUNKER("Бункер-бастер",55f,80f,2,0f,0f,Category.BALLISTIC,Kind.GLOBAL,"Бурит породу под выбранной точкой"),
    SHURIKEN("Сюрикены",18f,0f,4,900f,0f,Category.BALLISTIC,Kind.BEAM,"Три клинка; кровотечение"),
    STICKY("Липкая граната",60f,80f,3,620f,3f,Category.GRENADE,Kind.BOUNCE,"Прилипает к земле или бойцу"),
    HOLY("Святая граната",90f,150f,1,530f,3f,Category.GRENADE,Kind.BOUNCE,"Таймер запускается после остановки"),
    GRAVITY("Грави-граната",40f,90f,2,610f,3f,Category.GRENADE,Kind.BOUNCE,"Две секунды стягивает, затем взрыв"),
    FLASH("Светошумовая",15f,120f,2,650f,2f,Category.GRENADE,Kind.BOUNCE,"Скрывает прицел и меняет управление"),
    FLAME("Огнемёт «Инферно»",65f,50f,3,0f,0f,Category.ELEMENTAL,Kind.BEAM,"Конус пламени и горящая земля"),
    MOLOTOV("Молотов",20f,85f,3,640f,0f,Category.ELEMENTAL,Kind.IMPACT,"Разливает огонь на три хода"),
    GAS("Газовая граната",0f,120f,3,630f,3f,Category.ELEMENTAL,Kind.BOUNCE,"Облако держится четыре хода"),
    ACID("Кислотная бомба",45f,75f,3,630f,0f,Category.ELEMENTAL,Kind.IMPACT,"Проедает землю, разъедает броню"),
    ION("Орбитальный луч",70f,25f,1,0f,0f,Category.ELEMENTAL,Kind.GLOBAL,"Вертикальный разрез острова"),
    SHOTGUN("Дробовик",25f,0f,4,0f,0f,Category.GUN,Kind.BEAM,"Два выстрела за ход и отбрасывание"),
    UZI("Узи",50f,0f,4,0f,0f,Category.GUN,Kind.BEAM,"15 пуль с разбросом"),
    MINIGUN("Миниган",75f,0f,2,0f,0f,Category.GUN,Kind.BEAM,"30 пуль; пробивает тоннель"),
    TESLA("Пушка Тесла",45f,0f,2,0f,0f,Category.GUN,Kind.BEAM,"Молния перескакивает на соседей"),
    DISC("Дискомёт",25f,50f,3,720f,10f,Category.GUN,Kind.BOUNCE,"Пять рикошетов, затем взрыв"),
    MINE("Мина",45f,75f,3,0f,0f,Category.ENGINEER,Kind.DEPLOY,"Взвод 2 с, предупреждение 1,5 с"),
    SPIDER("Мина-паук",50f,80f,2,0f,0f,Category.ENGINEER,Kind.DEPLOY,"Преследует врага в радиусе 350"),
    TURRET("Турель",20f,0f,2,0f,0f,Category.ENGINEER,Kind.DEPLOY,"60 HP; стреляет по видимой цели"),
    GIRDER("Стальная балка",0f,0f,4,0f,0f,Category.ENGINEER,Kind.DEPLOY,"Укажи место; угол задаёт наклон"),
    DOME("Силовой купол",0f,135f,2,0f,0f,Category.ENGINEER,Kind.DEPLOY,"Отражает внешние снаряды два хода"),
    DIGGER("Бур / паяльная лампа",30f,45f,4,0f,0f,Category.ENGINEER,Kind.BEAM,"Удерживай движение: тоннели и шахты"),
    SHEEP("Супер-овца",75f,110f,2,320f,9f,Category.CREATURE,Kind.WALKER,"Бежит; повторное нажатие — полёт"),
    BOAR("Бешеный кабан",45f,80f,2,380f,5f,Category.CREATURE,Kind.WALKER,"Таранит землю и отбрасывает врагов"),
    BANANA("Банановая бомба",30f,90f,1,600f,3f,Category.CREATURE,Kind.BOUNCE,"Прыгучесть 0,9; шесть крупных долек"),
    MOLE("Крот-подрывник",50f,80f,2,200f,6f,Category.CREATURE,Kind.WALKER,"Роет породу; взрывается в пустоте"),
    SKUNK("Скунс",15f,70f,2,160f,6f,Category.CREATURE,Kind.WALKER,"Оставляет ядовитый шлейф"),
    HAMMER("Кувалда",35f,40f,-1,0f,0f,Category.MELEE,Kind.MELEE,"Вбивает бойца и пробивает опору"),
    BAT("Бейсбольная бита",30f,0f,-1,0f,0f,Category.MELEE,Kind.MELEE,"Сильное направленное отбрасывание"),
    TASER("Шокер",40f,0f,3,0f,0f,Category.MELEE,Kind.MELEE,"Парализует на следующий ход"),
    KAMIKAZE("Камикадзе",80f,140f,1,0f,0f,Category.MELEE,Kind.MELEE,"Прорыв и взрыв ценой жизни"),
    AIRSTRIKE("Авиаудар",30f,70f,2,0f,0f,Category.GLOBAL,Kind.GLOBAL,"Шесть бомб по выбранному сектору"),
    NAPALM("Напалмовый удар",20f,85f,1,0f,0f,Category.GLOBAL,Kind.GLOBAL,"Шесть канистр с огнём"),
    METEOR("Метеоритный дождь",45f,110f,1,0f,0f,Category.GLOBAL,Kind.GLOBAL,"Восемь падающих метеоритов"),
    QUAKE("Землетрясение",0f,0f,1,0f,0f,Category.GLOBAL,Kind.GLOBAL,"Четыре секунды трясёт бойцов"),
    FLOOD("Потоп",0f,0f,1,0f,0f,Category.GLOBAL,Kind.GLOBAL,"Поднимает опасную границу на 90"),
    ROPE("Ниндзя-канат",0f,0f,-1,0f,0f,Category.GADGET,Kind.UTILITY,"Автоподтяжка; ↑ быстрее, ↓ выпуск троса"),
    JETPACK("Реактивный ранец",0f,0f,2,0f,0f,Category.GADGET,Kind.UTILITY,"100 топлива; ↑ тяга, ← → движение"),
    BOOTS("Гравитационные ботинки",0f,0f,2,0f,0f,Category.GADGET,Kind.UTILITY,"Тройной прыжок до конца хода"),
    TELEKINESIS("Телекинез",0f,0f,2,0f,0f,Category.GADGET,Kind.UTILITY,"Выбери объект, затем место переноса"),
    AK47("АК-47",56f,0f,5,0f,0f,Category.GUN,Kind.BEAM,"Очередь из семи пуль с отдачей"),
    REPULSOR("Репульсор",20f,180f,3,0f,0f,Category.GUN,Kind.BEAM,"Силовой конус отбрасывает бойцов"),
    LASER("Плазменный лазер",60f,0f,3,0f,0f,Category.GUN,Kind.BEAM,"Луч прорезает тонкие преграды"),
    DEFENDER("Защитник",0f,65f,2,0f,0f,Category.GADGET,Kind.UTILITY,"Дрон: поглощает 40 урона, 2 хода"),
    SWITCH("Смена персонажа",0f,0f,3,0f,0f,Category.GADGET,Kind.UTILITY,"Дополнительная смена бойца без выстрела"),
    PARACHUTE("Парашют",0f,0f,3,0f,0f,Category.GADGET,Kind.UTILITY,"Безопасное падение до конца хода"),
    AIM_SIGHT("Усиленный прицел",0f,0f,3,0f,0f,Category.GADGET,Kind.UTILITY,"Удлинённый прогноз траектории"),
    PLASMA_RIFLE("Плазменная винтовка",42f,48f,4,1050f,0f,Category.GUN,Kind.IMPACT,"Быстрый заряд; электрический взрыв"),
    CLUSTER_ROCKET("Кассетная ракета",24f,62f,2,760f,0f,Category.BALLISTIC,Kind.IMPACT,"При ударе выпускает шесть суббоеприпасов"),
    VORTEX("Граната «Сингулярность»",38f,180f,2,610f,2.5f,Category.GRENADE,Kind.BOUNCE,"Взрыв стягивает бойцов к центру");
    val targeted get()=this in setOf(TELEPORT,HOMING,BUNKER,ION,AIRSTRIKE,NAPALM,GIRDER,TELEKINESIS)
    val damageType get()=when(this) {FLAME,MOLOTOV,NAPALM->DamageType.FIRE;GAS,SKUNK->DamageType.POISON;TESLA,ION,STICKY,LASER,REPULSOR,PLASMA_RIFLE,VORTEX->DamageType.ELECTRIC;ACID->DamageType.ACID;else->if(kind==Kind.BEAM) DamageType.BULLET else if(kind==Kind.MELEE) DamageType.MELEE else DamageType.EXPLOSION}
}
enum class GameMode(val title:String) { DUEL("Дуэль"), BOSS("Охота на босса"), ROPE_RACE("Испытание каната") }
enum class Boss(val title:String,val hp:Int,val arena:Arena,val race:Race,val briefing:String) {
    INFERNO("Архидемон Инферно",1500,Arena.VOLCANO,Race.DEMON,"Укройся от метеоров; огонь ему не вредит."),
    ALCHEMIST("Безумный алхимик",1100,Arena.CHEMICAL,Race.MUTANT,"Не оставляй алхимика в газе: он лечится."),
    FAFNIR("Древний дракон Фафнир",1800,Arena.VOLCANO,Race.DRAGON,"Остерегайся дыхания и порывов ветра."),
    GENERAL("Генерал киборгов",1300,Arena.SCRAP,Race.CYBORG,"Сначала уничтожь два генератора щита.");
}
enum class Hat(val title:String,val hp:Int,val armor:Float) { NONE("Без шлема",0,0f), HELMET("Тактический",15,.03f), MASK("Противогаз",5,0f), CROWN("Корона пламени",5,0f) }
enum class Artifact(val title:String) { NONE("Без артефакта"), GRAVITY("Грави-реактор"), BLOOD("Кровавый амулет"), SAPPER("Пояс сапёра") }
enum class Upgrade(val title:String) { STANDARD("Стандарт"), PLASMA("Плазменная"), TRIPLE("Тройная") }
data class Loadout(val race:Race=Race.DRAGON,val hat:Hat=Hat.NONE,val artifact:Artifact=Artifact.NONE,val upgrade:Upgrade=Upgrade.STANDARD)
data class Hazard(var x:Float,var y:Float,val radius:Float,val effect:Effect,var turns:Int,val owner:Int)
enum class DeviceKind { MINE, SPIDER, TURRET, DOME, BARREL, GENERATOR, GEYSER, MAGNET, DEFENDER }
data class Device(val kind:DeviceKind,var x:Float,var y:Float,val owner:Int,var hp:Float=60f,var timer:Float=2f,var turns:Int=4,var triggered:Boolean=false)
data class Beam(val x:Float,val y:Float,val x2:Float,val y2:Float,var life:Float=.4f)
