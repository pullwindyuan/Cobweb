namespace java com.xiongbeer.webveins.service.rpc

struct ProcessData {
1: optional i32 type;
2: optional i32 crcCode;
3: optional i32 length;
4: optional i64 sessionId;
}

enum CrawlerStatus {
    NULL;
    WAITING;
    RUNNING;
    FINNISHED;
    READY;
}

service LocalWorkerCrawlerService {
    /*
        worker主动放弃某个任务

        @return true:分布式锁已经被重置
        @return false：分布式锁重置失败，直到此worker终止运行之前manager都无法检测出异常并重新设置锁
     */
    bool giveUpTask(1:string taskId, 2:string reason);

    /*
        获取某个任务最后一次保存的进度
     */
    string getLastProgressRate(1:string taskId);

    /*
        worker完成任务，报告状态

        @return true:分布式锁状态已经被设置为FINISHED
        @return false：分布式锁状态设置失败失败，worker终止运行会导致manager错误地检测出异常并重新设置锁，导致任务被重新领取
     */
    bool finishTask(1:string taskId);

    /*
        获取新的任务
        如果队列是空的，那么这个操作会阻塞到有任务为止

        @return false：正在进行的任务数如果等于预设的最大队列长度，领取新任务的操作会被wvWorker进程拒绝
     */
    bool getNewTask(1:string taskFilePath, 2:string lastProgressRate);

    /*
        更新某个任务的进度

        @return false：失败的原因主要为网络原因或者zk Server压力过大，服务降级导致此功能被暂时关闭
     */
    bool updateTaskProgressRate(1:string taskId, 2:string newProgressRate);

    /*
        获取所有workers的状态信息
     */
    string getWorkersStatus();

    /*
        获取所有filters的状态信息
     */
    string getFiltersStatus();

    /*
        获取所有managers的状态信息
     */
    string getManagersStatus();
}