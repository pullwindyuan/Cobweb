package com.xiongbeer.cobweb.service.local;

/**
 * Created by shaoxiong on 17-4-26.
 */
public interface Action {

    boolean run(String urlFilePath, int progress);

    int report();

    void reportResult(int result);
}
