package org.sq.gameDemo.svr.game.store.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.sq.gameDemo.svr.common.Constant;
import org.sq.gameDemo.svr.common.customException.CustomException;
import org.sq.gameDemo.svr.eventManage.EventBus;
import org.sq.gameDemo.svr.eventManage.event.FirstStoreBuyEvent;
import org.sq.gameDemo.svr.game.bag.model.Item;
import org.sq.gameDemo.svr.game.bag.model.ItemInfo;
import org.sq.gameDemo.svr.game.bag.service.BagService;
import org.sq.gameDemo.svr.game.characterEntity.model.Player;
import org.sq.gameDemo.svr.game.characterEntity.service.EntityService;
import org.sq.gameDemo.svr.game.mail.service.MailService;
import org.sq.gameDemo.svr.game.scene.service.SenceService;
import org.sq.gameDemo.svr.game.store.dao.StoreCache;
import org.sq.gameDemo.svr.game.store.model.Store;

import java.util.ArrayList;

@Service
public class StoreService {

    @Autowired
    private StoreCache storeCache;
    @Autowired
    private SenceService senceService;
    @Autowired
    private BagService bagService;
    @Autowired
    private MailService mailService;
    @Autowired
    private EntityService entityService;

    public Store showStore(Integer storeId) {
        return storeCache.get(storeId);
    }

    /**
     * 使用元宝购买物品
     * @param player
     * @param storeId
     */
    public void buyGood(Player player, Integer itemInfoId, Integer count, Integer storeId) throws CustomException.SystemSendMailErrException {
        Store store = storeCache.get(storeId);
        if(store == null ) {
            senceService.notifyPlayerByDefault(player, "该商店(id="+ storeId +")编号不存在");
            return;
        }
        ItemInfo itemInfo = store.getGoodsMap().get(itemInfoId);
        if(itemInfo == null) {
            senceService.notifyPlayerByDefault(player, "该商店货物(id="+ itemInfoId +") 不存在");
            return;
        }

        int needMoney = itemInfo.getPrice() * count;
        senceService.notifyPlayerByDefault(player, "该商店货物(id="+ itemInfo.getId() +", name= "+ itemInfo.getName() +" ) * " + count + "需要 元宝 * " +
                needMoney);
        Item money = bagService.findItem(player, Constant.YUAN_BAO, needMoney);
        if(money == null) {
            return;
        }
        Item item = bagService.createItem(itemInfo.getId(), count, itemInfo.getLevel());
        storeBuyMail(player, item);
        bagService.removeItem(player, money.getId(), needMoney);
        EventBus.publish(new FirstStoreBuyEvent(player));
    }

    public void storeBuyMail(Player player, Item item) throws CustomException.SystemSendMailErrException {
        ArrayList<Item> items = new ArrayList<>();
        items.add(item);
        Player system = entityService.getPlayer(Constant.SYSTEM_UNID);
        mailService.sendMail(system, player.getUnId(), "商店购买物品", "这是你在商店购买的物品", items);
//        Mail mail = mailService.createMail(system, player.getUnId(), "商店购买物品", "这是你在商店购买的物品", items);
//        mailService.sendMail(system, mail);
    }

}
