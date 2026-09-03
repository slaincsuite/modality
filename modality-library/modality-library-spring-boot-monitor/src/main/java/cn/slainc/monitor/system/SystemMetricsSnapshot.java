package cn.slainc.monitor.system;

import java.util.ArrayList;
import java.util.List;

public class SystemMetricsSnapshot {

    private CpuMetrics cpu = CpuMetrics.empty();

    private MemoryMetrics memory = MemoryMetrics.empty();

    private DiskMetrics disk = DiskMetrics.empty();

    private NetworkMetrics network = NetworkMetrics.empty();

    private ProcessMetrics process = ProcessMetrics.empty();

    private long uptimeSeconds;

    private long bootTimeSeconds;

    private long collectedAt;

    public CpuMetrics getCpu() {
        return cpu;
    }

    public void setCpu(CpuMetrics cpu) {
        this.cpu = cpu == null ? CpuMetrics.empty() : cpu;
    }

    public MemoryMetrics getMemory() {
        return memory;
    }

    public void setMemory(MemoryMetrics memory) {
        this.memory = memory == null ? MemoryMetrics.empty() : memory;
    }

    public DiskMetrics getDisk() {
        return disk;
    }

    public void setDisk(DiskMetrics disk) {
        this.disk = disk == null ? DiskMetrics.empty() : disk;
    }

    public NetworkMetrics getNetwork() {
        return network;
    }

    public void setNetwork(NetworkMetrics network) {
        this.network = network == null ? NetworkMetrics.empty() : network;
    }

    public ProcessMetrics getProcess() {
        return process;
    }

    public void setProcess(ProcessMetrics process) {
        this.process = process == null ? ProcessMetrics.empty() : process;
    }

    public long getUptimeSeconds() {
        return uptimeSeconds;
    }

    public void setUptimeSeconds(long uptimeSeconds) {
        this.uptimeSeconds = uptimeSeconds;
    }

    public long getBootTimeSeconds() {
        return bootTimeSeconds;
    }

    public void setBootTimeSeconds(long bootTimeSeconds) {
        this.bootTimeSeconds = bootTimeSeconds;
    }

    public long getCollectedAt() {
        return collectedAt;
    }

    public void setCollectedAt(long collectedAt) {
        this.collectedAt = collectedAt;
    }

    public record CpuMetrics(double usage, double load1m, int count, double[] coreUsage) {

        public static CpuMetrics empty() {
            return new CpuMetrics(0d, 0d, 0, new double[0]);
        }

        public double coreUsage(int index) {
            return coreUsage != null && index >= 0 && index < coreUsage.length ? coreUsage[index] : 0d;
        }
    }

    public record MemoryMetrics(long total, long available, long used, long swapTotal, long swapUsed) {

        public static MemoryMetrics empty() {
            return new MemoryMetrics(0L, 0L, 0L, 0L, 0L);
        }

        public double usage() {
            return total <= 0L ? 0d : (double) used / (double) total;
        }
    }

    public record DiskMetrics(long readBytes, long writeBytes, long reads, long writes, List<MountMetrics> mounts) {

        public static DiskMetrics empty() {
            return new DiskMetrics(0L, 0L, 0L, 0L, new ArrayList<>());
        }
    }

    public record MountMetrics(String mount, long total, long free, long usable) {

        public double usage() {
            return total <= 0L ? 0d : 1d - (double) free / (double) total;
        }
    }

    public record NetworkMetrics(long rxBytes, long txBytes, long rxPackets, long txPackets,
                                 long tcpEstablished, long tcpListen, long tcpTimeWait, long tcpCloseWait,
                                 long tcpTotal) {

        public static NetworkMetrics empty() {
            return new NetworkMetrics(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
        }
    }

    public record ProcessMetrics(int processes, int threads, long openFiles, long maxFiles) {

        public static ProcessMetrics empty() {
            return new ProcessMetrics(0, 0, 0L, 0L);
        }
    }
}
