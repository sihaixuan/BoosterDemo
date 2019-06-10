package com.sihaixuan.booster.demo;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Generated;

/**
 * 项目名称：BoosterDemo
 * 类描述：
 * 创建人：toney
 * 创建时间：2019/6/2 13:09
 * 邮箱：xiyangfeisa@foxmail.com
 * 备注：
 *
 * @version 1.0
 */
@Entity
public class Room {
    @Id
    public Long id;

    @Generated(hash = 1436527432)
    public Room(Long id) {
        this.id = id;
    }

    @Generated(hash = 703125385)
    public Room() {
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
