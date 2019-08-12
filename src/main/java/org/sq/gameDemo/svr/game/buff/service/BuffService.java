package org.sq.gameDemo.svr.game.buff.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.sq.gameDemo.svr.common.TimeTaskManager;
import org.sq.gameDemo.svr.game.buff.dao.BuffCache;
import org.sq.gameDemo.svr.game.buff.model.Buff;
import org.sq.gameDemo.svr.game.buff.model.BuffState;
import org.sq.gameDemo.svr.game.characterEntity.model.Character;
import org.sq.gameDemo.svr.game.characterEntity.model.Player;
import org.sq.gameDemo.svr.game.characterEntity.service.EntityService;
import org.sq.gameDemo.svr.game.scene.service.SenceService;

import javax.sound.midi.Track;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BuffService {

    @Autowired
    private BuffCache buffCache;
    @Autowired
    private EntityService entityService;
    @Autowired
    private SenceService senceService;

    /**
     * buff开始作用在场景实体上
     * @param affecter
     * @param affectedBuff
     */
    public void buffAffecting(Character affecter, Buff affectedBuff) {
        List<Buff> bufferList = affecter.getBufferList();
        //如果存在净化buff，不进行作用
        if( !affectedBuff.getId().equals(BuffState.PURIFY.getEffectState())
                && bufferList.stream().filter(buff1 -> buff1.getId().equals(BuffState.PURIFY.getEffectState())).findAny().isPresent()) {
            return;
        }

        Buff buff = new Buff();
        BeanUtils.copyProperties(affectedBuff, buff);
        //buff开始时间
        buff.setStartTime(System.currentTimeMillis());
        bufferList.add(buff);

        //瞬间作用的buff 治疗等等
        if(buff.getDuration() == 0 && usingBuff(affecter, buff)) {
            return;
        }
        Future future = null;
        try {
            if(buff.getIntervalTime() <= 0) {
                future = TimeTaskManager.threadPoolSchedule(0, () -> {
                    usingBuff(affecter, buff);
                });
            } else {
                future = TimeTaskManager.scheduleAtFixedRate(0, buff.getIntervalTime() <= 0 ? 1000 : buff.getIntervalTime(), () -> {
                    usingBuff(affecter, buff);
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        //如果不是永久性buff, 持续时间结束后停止
        if(affectedBuff.getDuration() != -1 && future != null) {
            buff.setFuture(future);

            try {
                TimeTaskManager.threadPoolSchedule(buff.getDuration(),
                        () -> {
                            Future buffFuture = buff.getFuture();
                            Character character = buff.getCharacter();

                            if(buff != null && buffFuture != null && character != null) {
                                //作用时间完毕，移除buff
                                buffFuture.cancel(true);
                                character.getBufferList().remove(buff);
                            }
                            // 如果是玩家，进行通知
                            if (character instanceof Player) {
                                senceService.notifyPlayerByDefault(affecter, buff.getName()
                                        + "取消作用，在" + affecter.getName() + "身上");
//                                // 检测玩家是否死亡
//                                if(entityService.playerIsDead((Player) affecter,null)) {
//                                    removeAllBuff(affecter);
//                                }
                            }
                        }
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * buff进行作用
     * @param affecter
     * @param buff
     * @return
     */
    private boolean usingBuff(Character affecter, Buff buff) {
        if(affecter.getHp() >= 0) {
            //净化buff作用
            if(buff.getId().equals(BuffState.PURIFY.getEffectState())) {
                return removeBadBuff(affecter);
            }
            if(buff.getHp() != 0) {
                affecter.setHp(affecter.getHp() + buff.getHp());
            }
            if(buff.getMp() != 0) {
                affecter.setMp(affecter.getMp() + buff.getMp());
            }
            // 如果是玩家，进行通知
            if (affecter instanceof Player && !buff.getId().equals(105) && !buff.getId().equals(106)) {
                senceService.notifyPlayerByDefault(affecter,
                        buff.getName() + "正作用在" + affecter.getName() + "身上");
                // 检测玩家是否死亡
                if(entityService.playerIsDead((Player) affecter,null)) {
                    //结束buff
                    removeAllBuff(affecter);
                }
            }
            return true;
        }
        return false;
    }

    /**
     * 移除减益效果的buff
     * @param affecter
     * @return
     */
    public synchronized boolean removeBadBuff(Character affecter) {
        boolean result = false;
        if(affecter != null) {
            try {
                List<Buff> buffList = affecter.getBufferList();
                if(buffList != null && buffList.size() > 0) {
                    //获取减益效果的buff
                    List<Buff> collect = buffList.stream()
                            .filter(buff -> Arrays.stream(BuffState.values())
                                            .allMatch( state -> buff.getId().equals(state.getEffectState()) && !state.getGain() ))
                            .collect(Collectors.toCollection(CopyOnWriteArrayList::new));
                    //取消作用
                    collect.forEach(buff -> {
                        if(buffList.contains(buff)) {
                            buff.getFuture().cancel(true);
                        }
                    });
                    buffList.removeAll(collect);
                }
                result = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return result;
    }

    public synchronized boolean removeAllBuff(Character affecter) {
        boolean result = false;
        if(affecter != null) {
            try {
                List<Buff> buffList = affecter.getBufferList();
                if(buffList.size() > 0) {
                    buffList.forEach(buff -> {
                        if(buffList.contains(buff)) {
                            buff.getFuture().cancel(true);
                        }
                    });
                    buffList.clear();
                }
                result = true;
                senceService.notifyPlayerByDefault(affecter, "玩家死亡， 所有buff移除");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return result;
    }

    public Buff getBuff(Integer type) {
        return buffCache.get(type);
    }
}
