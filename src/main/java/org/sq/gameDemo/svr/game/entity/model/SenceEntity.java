package org.sq.gameDemo.svr.game.entity.model;

import lombok.Data;
import org.sq.gameDemo.common.proto.EntityProto;

//场景实体
@Data
public class SenceEntity {
    private Integer id;
    private Integer typeId;
    private int num;
    private int state;

//    public SenceEntity(Integer id, Integer typeId, int state) {
//        this.id = id;
//        this.typeId = typeId;
//        this.state = state;
//    }
}