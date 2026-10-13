package cn.slainc.workorder.number;


public interface WorkOrderSequenceStore {

    long next(String key);
}
