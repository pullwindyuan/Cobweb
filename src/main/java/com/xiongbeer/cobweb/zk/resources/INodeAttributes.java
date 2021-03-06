package com.xiongbeer.cobweb.zk.resources;

/**
 * Created by shaoxiong on 17-11-5.
 */
public interface INodeAttributes {
    boolean isDirectory();

    String getPath();

    void lock();

    void unlock();

    boolean isLocked();

    int getMarkup();

    String getGroup();
}
