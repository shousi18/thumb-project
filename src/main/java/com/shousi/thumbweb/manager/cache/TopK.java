package com.shousi.thumbweb.manager.cache;

import java.util.List;
import java.util.concurrent.BlockingQueue;

public interface TopK {

    /**
     * 添加元素，并更新 TopK 结构
     * @param key
     * @param increment
     * @return
     */
    AddResult add(String key, int increment);

    /**
     * 返回当前 TopK 元素的列表
     *
     * @return
     */
    List<Item> list();

    /**
     * 获取被挤出的 TopK 的元素的队列
     *
     * @return
     */
    BlockingQueue<Item> expelled();

    /**
     * 对所有计数进行衰减
     */
    void fading();

    long total();
}
