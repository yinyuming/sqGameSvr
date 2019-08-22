package org.sq.gameDemo.svr.game.mail.service;


import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.sq.gameDemo.svr.common.Constant;
import org.sq.gameDemo.svr.common.customException.CustomException;
import org.sq.gameDemo.svr.game.bag.model.Bag;
import org.sq.gameDemo.svr.game.bag.model.Item;
import org.sq.gameDemo.svr.game.bag.service.BagService;
import org.sq.gameDemo.svr.game.characterEntity.model.Player;
import org.sq.gameDemo.svr.game.characterEntity.service.EntityService;
import org.sq.gameDemo.svr.game.mail.dao.MailCache;
import org.sq.gameDemo.svr.game.mail.dao.MailMapper;
import org.sq.gameDemo.svr.game.mail.model.Mail;
import org.sq.gameDemo.svr.game.mail.model.MailExample;
import org.sq.gameDemo.svr.game.scene.service.SenceService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MailService {

    @Autowired
    private MailMapper mailMapper;
    @Autowired
    private SenceService senceService;
    @Autowired
    private EntityService entityService;
    @Autowired
    private BagService bagService;
    @Autowired
    private MailCache mailCache;


    /**
     * 发送带道具附件的邮件
     */
    public boolean sendMail(Player sender, Mail mail) throws CustomException.SystemSendMailErrException {

        if(mail == null) {
            return false;
        }

        Player reciever = entityService.getPlayer(mail.getRecevierUnId());
        int id = mailMapper.insertSelective(mail);
        if(id >= 0) {
            mail.setId(id);
            if(reciever == null) {
                senceService.notifyPlayerByDefault(sender, "玩家不在线，离线发送成功...");
            } else {
                mailCache.put(mail);
                senceService.notifyPlayerByDefault(sender, "发送成功");
                senceService.notifyPlayerByDefault(reciever, sender.getName()
                        + "给你发送了一封邮件(id=" + mail.getId() + "), 使用 showMail 查看邮件吧");
            }
            return true;
        } else {
            senceService.notifyPlayerByDefault(sender, "发送失败，系统错误");
            return false;
        }

    }

    /**
     * 创建邮件
     */
    public Mail createMail(Player sender, String recevierName, String title, String content, List<Item>
            rewardItems) throws CustomException.SystemSendMailErrException {
        Integer recevierUnId = entityService.getUnIdByName(recevierName);

        if(recevierUnId == null ) {
            if(!sender.getUnId().equals(Constant.SYSTEM_UNID)) {
                senceService.notifyPlayerByDefault(sender, "邮件对象不存在，请检查id");
            } else {
                log.info("系统创建邮件失败, unid=" + recevierUnId + "不存在");
            }
            return null;
        }

        if(sender.getName().equals(Constant.SYSTEM_MANAGER)) {
            log.info("系统发送邮件");
//            return new Mail(Constant.SYSTEM_UNID, Constant.SYSTEM_MANAGER, recevierUnId, title, content, rewardItems);
        }

        if(rewardItems == null) {
            return new Mail(sender.getUnId(), sender.getName(), recevierUnId, title, content, new ArrayList<>());
        } else {
            return new Mail(sender.getUnId(), sender.getName(), recevierUnId, title, content, rewardItems);
        }
    }

    /*
    * 邮件列表
    * */
    public List<Mail> getMailList(Player player) {
        MailExample mailExample = new MailExample();
        mailExample.createCriteria().andRecevierUnIdEqualTo(player.getUnId());
        List<Mail> mailList = mailMapper.selectByExample(mailExample);
        if(mailList != null && mailList.size() != 0) {
            mailList.forEach(mail -> {

            });
            return mailList;
        } else {
            senceService.notifyPlayerByDefault(player, "邮箱为空");
            return null;
        }
    }

    /**
     * 获取邮件
     */
    public Mail getMail(Player player, Integer mailId) {
        Mail mail = null;
        mail = mailCache.get(mailId);
        if(mail != null) {
            return mail;
        }
        mail = mailMapper.selectByPrimaryKey(mailId);
        if(mail != null) {
            return mail;
        } else {
            senceService.notifyPlayerByDefault(player, "该邮件不存在了");
            return null;
        }
    }


    /**
     * 一键收取邮件
     */
    public void getAllMailItem(Player player) {
        List<Mail> mailList = getMailListInCache(player);
        if(mailList != null) {
            mailList.forEach(mail -> {
                getMailItem(player, mail);
            });
        }
    }

    /**
     * 获取玩家邮件列表
     * @param player
     * @return
     */
    private List<Mail> getMailListInCache(Player player) {
        return mailCache.asMap().values().stream().filter(mail -> mail.getRecevierUnId().equals(player.getUnId())).collect(Collectors.toList());
    }

    /**
     * 收取缓存中单个邮件
     * @param player
     * @param mail
     */
    public void getMailItem(Player player, Mail mail) {
        if(mail != null) {
            mail.getRewardItems().forEach(item -> bagService.addItemInBag(player, item));
            mail.setIsRead(true);
            mail.setKeepTime(-1L);
            mailCache.remove(mail);
            mailMapper.updateByPrimaryKey(mail);
        } else {
            senceService.notifyPlayerByDefault(player, "id="+ mail.getId() + " 的邮件不存在");
        }

    }

    /**
     * 退还邮件
     * @param mail
     */
    public void returnItems(Mail mail) {
        synchronized(mail) {
            mailCache.remove(mail);


            Mail maildb = mailMapper.selectByPrimaryKey(mail.getId());

            if(maildb != null) {
                if(mail.getSenderUnId().equals(Constant.SYSTEM_UNID)
                        || maildb.getRewardItems() == null
                        || maildb.getRewardItems().size()
                        == 0) {
                    maildb.setIsRead(true);
                    maildb.setKeepTime(-1L);
                    mailMapper.updateByPrimaryKey(maildb);
                    mailCache.remove(mail);
                } else {
                    maildb.setKeepTime(-1L);
                    maildb.setSenderName(Constant.SYSTEM_MANAGER);
                    maildb.setRecevierUnId(maildb.getSenderUnId());
                    maildb.setSenderUnId(Constant.SYSTEM_UNID);
                    try {
                        sendMail(entityService.getPlayer(Constant.SYSTEM_UNID), maildb);
                    } catch (CustomException.SystemSendMailErrException e) {
                        e.printStackTrace();
                    }

                }
            }
        }


    }

    /**
     * 加载邮件资源
     * @param player
     */
    public void loadMail(Player player) {
        MailExample mailExample = new MailExample();
        mailExample.createCriteria().andRecevierUnIdEqualTo(player.getUnId()).andKeepTimeGreaterThan(0L).andIsReadEqualTo(false);
        List<Mail> mailList = mailMapper.selectByExample(mailExample);
        for (Mail mail : mailList) {
            mailCache.put(mail);
        }
        log.info("玩家上线，邮件资源加载成功");
    }


    /**
     * 清除邮件缓存
     */
    public void clearCache(Player player) {
        mailCache.asMap()
                .values()
                .stream()
                .filter(mail -> mail.getRecevierUnId().equals(player.getUnId()))
                .forEach(mail -> mailCache.remove(mail));
    }
}
