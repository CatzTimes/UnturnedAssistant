package Models;

import Configurations.LanguageManager;

/**
 * 资产大类（输出分组顺序即枚举声明顺序）。
 * 对应游戏 EAssetType 的 legacy ID 作用域，并按官方 UnturnedNexus 注册表细分。
 */
public enum AssetCategory {
    ITEM("unturned.item"),
    VEHICLE("unturned.vehicle"),
    OBJECT("unturned.objects"),
    ANIMAL("unturned.animals"),
    RESOURCE("unturned.resource"),
    NPC("unturned.npc"),
    DIALOGUE("unturned.dialog"),
    QUEST("unturned.quest"),
    VENDOR("unturned.vendor"),
    EFFECT("unturned.effect"),
    SKIN("unturned.skin"),
    MYTHIC("unturned.mythic"),
    SPAWN("unturned.spawn"),
    REDIRECTOR("unturned.redirector"),
    OTHER("unturned.other");

    private final String i18nKey;

    AssetCategory(String i18nKey) {
        this.i18nKey = i18nKey;
    }

    public String getName() {
        return LanguageManager.getI18nText(i18nKey);
    }
}
