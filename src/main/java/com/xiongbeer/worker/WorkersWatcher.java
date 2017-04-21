package com.xiongbeer.worker;

import com.xiongbeer.Async;
import com.xiongbeer.Tracker;
import com.xiongbeer.ZnodeInfo;
import org.apache.zookeeper.*;
import org.apache.zookeeper.AsyncCallback.*;
import org.apache.zookeeper.KeeperException.Code;

import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.*;


/**
 * 监视workers下运行客户端的连接状态
 * Created by shaoxiong on 17-4-6.
 */
public class WorkersWatcher implements Watcher{
    private ZooKeeper client;
    private HashMap<String, String> workersList = new HashMap<String, String>();
    private Logger logger = LoggerFactory.getLogger(WorkersWatcher.class);
    private ChildrenCallback workersGetChildrenCallback = new ChildrenCallback() {
        public void processResult(int rc, String path, Object ctx, List<String> children) {
            switch (Code.get(rc)){
                case CONNECTIONLOSS:
                    getWorkers((Tracker) ctx);
                    break;
                case OK:
                    logger.info("Succesfully got a list of workers: "
                                        + children.size()
                                        + " workers");

                    /* 首先检查上一次保存的worker中有没有消失的 */
                    Iterator iterator = workersList.entrySet().iterator();
                    while(iterator.hasNext()){
                        Map.Entry entry = (Map.Entry) iterator.next();
                        if(!children.contains(entry.getKey())){
                            workersList.remove(entry.getKey());
                        }
                    }

                    /* 检查是否有新的worker */
                    for(String name:children){
                        if(!workersList.containsKey(name)){
                            workersList.put(name, null);
                        }
                    }
                    if(ctx != null) {
                        ((Tracker) ctx).setStatus(Tracker.SUCCESS);
                    }
                    break;
                default:
                   logger.warn("getChildren failed");
            }
        }
    };

    public WorkersWatcher(ZooKeeper zk){
        this.client = zk;
    }

    /**
     * 获得(刷新)worker列表
     * @param tracker
     */
    @Async
    public void getWorkers(Tracker tracker){
        client.getChildren(
                ZnodeInfo.WORKERS_PATH,
                this,
                workersGetChildrenCallback,
                tracker
        );
    }

    /**
     * 刷新所有worker列表中
     * worker目前的状态
     */
    public void reflushWorkerStatus(){
        Iterator iterator = workersList.entrySet().iterator();
        while(iterator.hasNext()){
            Map.Entry entry = (Map.Entry) iterator.next();
            getWorkerStatus((String) entry.getKey());
        }
    }

    private void getWorkerStatus(String workerName){
        try {
            byte[] data = client.getData(
                            ZnodeInfo.WORKERS_PATH + "/" + workerName,
                            false,
                            null
                            );
            workersList.put(workerName, new String(data));
        } catch (KeeperException.ConnectionLossException e) {
            getWorkerStatus(workerName);
        } catch (KeeperException.NoNodeException e){
            workersList.remove(workerName);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (KeeperException e) {
            e.printStackTrace();
        }
    }

    public void process(WatchedEvent watchedEvent) {
        if(watchedEvent.getType() == Event.EventType.NodeChildrenChanged){
            assert ZnodeInfo.WORKERS_PATH.equals( watchedEvent.getPath() );
            getWorkers(null);
        }
    }

    public HashMap<String, String> getWorkersList(){
        return workersList;
    }

    /**
     * 提取path中的Data的name
     *
     * @param path
     * @return
     */
    private String getDataName(String path){
        String[] items = path.split("/");
        if(items == null){
            return null;
        }
        return items[items.length-1];
    }
}
