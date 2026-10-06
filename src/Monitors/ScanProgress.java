package Monitors;

/** 扫描进度监控契约：progress/cancelled 由后台扫描线程调用，实现方负责线程安全。 */
public interface ScanProgress {

    /**
     * 进度回报。
     *
     * @param done       已处理目录数（total 为 0 时表示目录收集阶段的目录提示）
     * @param total      目录总数
     * @param currentDir 当前正在收集的目录（仅收集阶段提供）
     */
    void progress(int done, int total, String currentDir);

    /** 扫描线程周期性轮询；返回 true 时扫描尽快收敛退出。 */
    boolean cancelled();
}
