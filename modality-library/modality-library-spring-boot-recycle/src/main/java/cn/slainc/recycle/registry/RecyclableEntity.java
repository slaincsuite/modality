package cn.slainc.recycle.registry;

import cn.slainc.mybatis.entity.MybatisBaseEntity;

// 标记父类：MybatisBaseEntity 已携带 @TableLogic 逻辑删除与审计字段，继承即可被回收站识别
@SuppressWarnings("serial")
public abstract class RecyclableEntity extends MybatisBaseEntity {
}
