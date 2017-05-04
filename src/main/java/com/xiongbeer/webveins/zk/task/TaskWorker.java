package com.xiongbeer.webveins.zk.task;

import com.xiongbeer.webveins.utils.Tracker;
import com.xiongbeer.webveins.ZnodeInfo;
import org.apache.zookeeper.*;

import java.util.*;

/**
 * Created by shaoxiong on 17-4-10.
 */
public class TaskWorker extends Task{

    //TODO 构建优先级队列时候可以用到
    private static LinkedList<String> waitingList = new LinkedList<String>();

    public TaskWorker(ZooKeeper zk) {
        super(zk);
    }

    /**
     * 接管任务
     */
    public boolean takeTask(){
        Tracker tracker = new Tracker();
        String task = null;
        checkTasks(tracker);
        while(tracker.getStatus() == Tracker.WAITING){
            /* 等待checkTasks任务完成 */
        }

        /* 抢夺未被领取的任务 */
        Iterator iterator = tasksInfo.entrySet().iterator();
        while(iterator.hasNext()){
            Map.Entry entry = (Map.Entry)iterator.next();
            String key = (String) entry.getKey();
            Epoch value = (Epoch) entry.getValue();
            if(value.getStatus().equals(WAITING)){
                if(setRunningTask(ZnodeInfo.TASKS_PATH + "/" + key,
                        value.getDataVersion())) {
                    task = key;
                    break;
                }
            }
        }

        /* 如果task不为null就说明拿到了任务 */
        if(task != null){
            return true;
        }

        return false;
    }

    /**
     * 执行失败，放弃任务
     *
     * @param taskPath
     *
     */
    public void DiscardTask(String taskPath){
        try {
            client.setData(taskPath, WAITING.getBytes(), -1);
        } catch (KeeperException.ConnectionLossException e) {
            DiscardTask(taskPath);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (KeeperException e) {
            e.printStackTrace();
        }
    }

    /**
     * 完成任务
     *
     * @param taskPath
     */
    public void FinishTask(String taskPath){
        try {
            client.setData(taskPath, FINISHED.getBytes(), -1);
        } catch (KeeperException.ConnectionLossException e) {
            DiscardTask(taskPath);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (KeeperException e) {
            e.printStackTrace();
        }
    }

    /**
     * 尝试将一个task节点置为running状态
     * 若成功即拿到了该任务
     *
     * @param path
     * @param version
     * @return
     */
    private boolean setRunningTask(String path, int version){
        boolean result = false;
        try {
            client.setData(path, RUNNING.getBytes(), version);
            result = true;
        } catch (KeeperException.ConnectionLossException e) {
            setRunningTask(path, version);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (KeeperException e) {
            e.printStackTrace();
        }
        return result;
    }
}