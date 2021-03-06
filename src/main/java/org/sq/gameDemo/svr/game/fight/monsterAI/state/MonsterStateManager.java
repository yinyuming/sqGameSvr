package org.sq.gameDemo.svr.game.fight.monsterAI.state;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.sq.gameDemo.svr.common.UserCache;
import org.sq.gameDemo.svr.common.customException.CustomException;
import org.sq.gameDemo.svr.game.characterEntity.dao.PlayerCache;
import org.sq.gameDemo.svr.game.characterEntity.dao.SenceEntityCache;
import org.sq.gameDemo.svr.game.characterEntity.model.*;
import org.sq.gameDemo.svr.game.fight.monsterAI.MonsterAIService;
import org.sq.gameDemo.svr.game.scene.service.SenceService;
import org.sq.gameDemo.svr.game.skills.service.SkillCache;

import java.util.HashMap;
import java.util.Optional;

/**
 * 怪物状态管理器
 */
@Slf4j
@Component
public class MonsterStateManager {

    @Autowired
    private SenceEntityCache senceEntityCache;
    @Autowired
    private PlayerCache playerCache;
    @Autowired
    private MonsterAIService monsterAIService;
    @Autowired
    private SenceService senceService;


    HashMap<CharacterState, MonsterStateHandle> stateHandleMap = new HashMap<>();

    {
        stateHandleMap.put(CharacterState.ATTACKING, this::attack);
        stateHandleMap.put(CharacterState.IS_REFRESH, this::refresh);
    }

    /**
     * 刷新怪物状态
     * @param monster
     */
    public void refreshMonsterState(Monster monster) {
            Optional.ofNullable(stateHandleMap.get(CharacterState.getStateByCode(monster.getState()))).ifPresent(
                    stateHandler -> {
                        try {
                           // Thread.currentThread().sleep(3000);
                            stateHandler.handle(monster);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
            );

    }


    /**
     * 怪物刷新状态
     * @param monster
     */
    private void refresh(Monster monster) {

        if(monster.getRefreshTime() > 0) {
            long dieTime = monster.getDeadTime() + monster.getRefreshTime();
            long now = System.currentTimeMillis();
            if(dieTime <= now) {
                SenceEntity senceEntity = senceEntityCache.get(monster.getEntityTypeId());
                monster.setHp(senceEntity.getHp() + monster.getLevel() * 10);
                monster.setState(CharacterState.LIVE.getCode());
                senceService.notifySenceByDefault(monster.getSenceId(), monster.getName() + "(id=" + monster.getId() +")已复活");

            }
        }

    }

    /**
     * 怪物攻击状态
     * @param monster
     */
    private void attack(Monster monster)  {
        synchronized (monster.getState()) {
            if(monster.getState().equals(CharacterState.ATTACKING.getCode())) {
                try {
                    monsterAIService.monsterAttacking(monster);
                } catch (CustomException.PlayerAlreadyDeadException e) {
                    e.printStackTrace();
                }
            }
        }
    }




}
