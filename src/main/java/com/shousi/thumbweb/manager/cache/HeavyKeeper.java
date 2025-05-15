package com.shousi.thumbweb.manager.cache;

import cn.hutool.core.util.HashUtil;
import lombok.Data;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class HeavyKeeper implements TopK {
    private static final int LOOKUP_TABLE_SIZE = 256;
    /**
     * 热点 Key 的数量
     */
    private final int k;
    /**
     * 大桶的宽度
     */
    private final int width;
    /**
     * 大桶中小桶的数量
     */
    private final int depth;
    private final double[] lookupTable;
    private final Bucket[][] buckets;
    /**
     * 最小堆，用于存储前 k 个元素
     */
    private final PriorityQueue<Node> minHeap;
    /**
     * 阻塞队列，用于存储被淘汰的 Key
     */
    private final BlockingQueue<Item> expelledQueue;
    private final Random random;
    private long total;
    private final int minCount;

    public HeavyKeeper(int k, int width, int depth, double decay, int minCount) {
        this.k = k;
        this.width = width;
        this.depth = depth;
        this.minCount = minCount;

        this.lookupTable = new double[LOOKUP_TABLE_SIZE];
        // 初始化 lookup table
        for (int i = 0; i < LOOKUP_TABLE_SIZE; i++) {
            lookupTable[i] = Math.pow(decay, i);
        }

        this.buckets = new Bucket[depth][width];
        for (int i = 0; i < depth; i++) {
            for (int j = 0; j < width; j++) {
                buckets[i][j] = new Bucket();
            }
        }

        this.minHeap = new PriorityQueue<>(Comparator.comparingInt(n -> n.count));
        this.expelledQueue = new LinkedBlockingQueue<>();
        this.random = new Random();
        this.total = 0;
    }

    @Override
    public AddResult add(String key, int increment) {
        byte[] keyBytes = key.getBytes();
        // 计算 hash 值
        long itemFingerprint = hash(keyBytes);
        int maxCount = 0;

        for (int i = 0; i < depth; i++) {
            // 计算桶的编号
            int bucketNumber = Math.abs(hash(keyBytes)) % width;
            // 获取桶
            Bucket bucket = buckets[i][bucketNumber];

            synchronized (bucket) {
                if (bucket.count == 0) {
                    // 如果桶为空，则初始化桶
                    bucket.fingerprint = itemFingerprint;
                    bucket.count = increment;
                    maxCount = Math.max(maxCount, increment);
                } else if (bucket.fingerprint == itemFingerprint) {
                    // 如果桶中已经存在该 key，则更新该桶的计数器
                    bucket.count += increment;
                    maxCount = Math.max(maxCount, bucket.count);
                } else {
                    // 如果桶中已经存在该 key，计数衰减机制
                    for (int j = 0; j < increment; j++) {
                        // 计算衰减概率
                        double decay = bucket.count < LOOKUP_TABLE_SIZE ?
                                lookupTable[bucket.count] :
                                lookupTable[LOOKUP_TABLE_SIZE - 1];
                        if (random.nextDouble() < decay) {
                            // 如果命中，则计数器减一
                            bucket.count--;
                            // 如果计数器为零，则进行替换，更新桶信息
                            if (bucket.count == 0) {
                                bucket.fingerprint = itemFingerprint;
                                bucket.count = increment - j;
                                maxCount = Math.max(maxCount, bucket.count);
                                break;
                            }
                        }
                    }
                }
            }
        }

        total += increment;

        if (maxCount < minCount) {
            return new AddResult(null, false, null);
        }

        synchronized (minHeap) {
            boolean isHot = false;
            String expelled = null;
            // 判断是否为热点 Key
            Optional<Node> existing = minHeap.stream()
                    .filter(n -> n.key.equals(key))
                    .findFirst();

            if (existing.isPresent()) {
                // 如果存在，则更新
                minHeap.remove(existing.get());
                minHeap.add(new Node(key, maxCount));
                isHot = true;
            } else {
                // 如果不存在，则判断是否为热点 Key
                // 如果当前热点 Key 的数量小于 k，或者当前 Key 的计数大于等于最小堆中的最小 Key 的计数，则将当前 Key 添加到最小堆中
                // peek 返回队列头部的元素 如果队列为空，则返回null
                if (minHeap.size() < k || maxCount >= Objects.requireNonNull(minHeap.peek()).count) {
                    Node newNode = new Node(key, maxCount);
                    // 如果最小堆的数量大于等于 k，则将最小堆中的最小 Key 移除，并将当前 Key 添加到最小堆中
                    if (minHeap.size() >= k) {
                        // 获取最小堆中的最小 Key
                        expelled = minHeap.poll().key;
                        // 将最小堆中的最小 Key 添加到阻塞队列中
                        // offer 添加一个元素并返回true 如果队列已满，则返回false
                        expelledQueue.offer(new Item(expelled, maxCount));
                    }
                    minHeap.add(newNode);
                    isHot = true;
                }
            }

            return new AddResult(expelled, isHot, key);
        }
    }


    @Override
    public List<Item> list() {
        synchronized (minHeap) {
            List<Item> result = new ArrayList<>(minHeap.size());
            for (Node node : minHeap) {
                result.add(new Item(node.key, node.count));
            }
            result.sort((a, b) -> Integer.compare(b.count(), a.count()));
            return result;
        }
    }

    @Override
    public BlockingQueue<Item> expelled() {
        return expelledQueue;
    }

    @Override
    public void fading() {
        for (Bucket[] row : buckets) {
            for (Bucket bucket : row) {
                synchronized (bucket) {
                    bucket.count = bucket.count >> 1;
                }
            }
        }

        synchronized (minHeap) {
            PriorityQueue<Node> newHeap = new PriorityQueue<>(Comparator.comparingInt(n -> n.count));
            for (Node node : minHeap) {
                newHeap.add(new Node(node.key, node.count >> 1));
            }
            minHeap.clear();
            minHeap.addAll(newHeap);
        }

        total = total >> 1;
    }

    @Override
    public long total() {
        return total;
    }

    private static class Bucket {
        /**
         * 小桶的指纹
         */
        long fingerprint;
        /**
         * 小桶的计数器
         */
        int count;
    }

    private static class Node {
        final String key;
        final int count;

        Node(String key, int count) {
            this.key = key;
            this.count = count;
        }
    }

    private static int hash(byte[] data) {
        return HashUtil.murmur32(data);
    }
}

// 新增返回结果类
@Data
class AddResult {
    // 被挤出的 key
    private final String expelledKey;
    // 当前 key 是否进入 TopK
    private final boolean isHotKey;
    // 当前操作的 key
    private final String currentKey;

    public AddResult(String expelledKey, boolean isHotKey, String currentKey) {
        this.expelledKey = expelledKey;
        this.isHotKey = isHotKey;
        this.currentKey = currentKey;
    }

}
