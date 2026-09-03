package cn.slainc.monitor.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.hardware.VirtualMemory;
import oshi.software.os.FileSystem;
import oshi.software.os.InternetProtocolStats;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OshiSystemMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(OshiSystemMetricsCollector.class);

    private final long cacheMillis;

    private final List<String> diskMounts;

    private final List<String> networkInterfaces;

    private final boolean perCoreCpu;

    private final Object lock = new Object();

    private volatile SystemMetricsSnapshot cached;

    private volatile long cachedAt;

    private volatile SystemInfo systemInfo;

    private volatile boolean warned;

    private long[] previousSystemTicks;

    private long[][] previousProcessorTicks;

    public OshiSystemMetricsCollector(long cacheMillis, List<String> diskMounts, List<String> networkInterfaces,
                                      boolean perCoreCpu) {
        this.cacheMillis = Math.max(cacheMillis, 0L);
        this.diskMounts = diskMounts == null ? List.of() : List.copyOf(diskMounts);
        this.networkInterfaces = networkInterfaces == null ? List.of() : List.copyOf(networkInterfaces);
        this.perCoreCpu = perCoreCpu;
    }

    public SystemMetricsSnapshot snapshot() {
        SystemMetricsSnapshot current = cached;
        if (current != null && System.currentTimeMillis() - cachedAt < cacheMillis) {
            return current;
        }
        synchronized (lock) {
            if (cached != null && System.currentTimeMillis() - cachedAt < cacheMillis) {
                return cached;
            }
            try {
                SystemMetricsSnapshot fresh = collect();
                cached = fresh;
                cachedAt = System.currentTimeMillis();
                return fresh;
            } catch (RuntimeException | Error exception) {
                if (!warned) {
                    warned = true;
                    log.warn("OSHI 系统指标采集失败，本次返回上一次快照或 0: {}", exception.getMessage());
                }
                return cached == null ? new SystemMetricsSnapshot() : cached;
            }
        }
    }

    private SystemMetricsSnapshot collect() {
        SystemInfo info = systemInfo();
        HardwareAbstractionLayer hardware = info.getHardware();
        OperatingSystem operatingSystem = info.getOperatingSystem();
        SystemMetricsSnapshot snapshot = new SystemMetricsSnapshot();
        snapshot.setCpu(collectCpu(hardware.getProcessor()));
        snapshot.setMemory(collectMemory(hardware.getMemory()));
        snapshot.setDisk(collectDisk(hardware.getDiskStores(), operatingSystem.getFileSystem()));
        snapshot.setNetwork(collectNetwork(hardware.getNetworkIFs(), operatingSystem.getInternetProtocolStats()));
        snapshot.setProcess(collectProcess(operatingSystem));
        snapshot.setUptimeSeconds(operatingSystem.getSystemUptime());
        snapshot.setBootTimeSeconds(operatingSystem.getSystemBootTime());
        snapshot.setCollectedAt(System.currentTimeMillis());
        return snapshot;
    }

    private SystemInfo systemInfo() {
        SystemInfo current = systemInfo;
        if (current == null) {
            synchronized (lock) {
                if (systemInfo == null) {
                    systemInfo = new SystemInfo();
                }
                current = systemInfo;
            }
        }
        return current;
    }

    private SystemMetricsSnapshot.CpuMetrics collectCpu(CentralProcessor processor) {
        long[] systemTicks = processor.getSystemCpuLoadTicks();
        long[][] processorTicks = perCoreCpu ? processor.getProcessorCpuLoadTicks() : null;
        double usage = previousSystemTicks == null ? 0d : processor.getSystemCpuLoadBetweenTicks(previousSystemTicks);
        double[] cores = perCoreCpu && previousProcessorTicks != null
                ? processor.getProcessorCpuLoadBetweenTicks(previousProcessorTicks) : new double[0];
        previousSystemTicks = systemTicks;
        previousProcessorTicks = processorTicks;
        double load = processor.getSystemLoadAverage(1).length > 0 ? processor.getSystemLoadAverage(1)[0] : 0d;
        return new SystemMetricsSnapshot.CpuMetrics(clean(usage), clean(load), processor.getLogicalProcessorCount(),
                clean(cores));
    }

    private SystemMetricsSnapshot.MemoryMetrics collectMemory(GlobalMemory memory) {
        VirtualMemory virtualMemory = memory.getVirtualMemory();
        long total = memory.getTotal();
        long available = memory.getAvailable();
        return new SystemMetricsSnapshot.MemoryMetrics(total, available, Math.max(total - available, 0L),
                virtualMemory == null ? 0L : virtualMemory.getSwapTotal(),
                virtualMemory == null ? 0L : virtualMemory.getSwapUsed());
    }

    private SystemMetricsSnapshot.DiskMetrics collectDisk(List<HWDiskStore> stores, FileSystem fileSystem) {
        long readBytes = 0L;
        long writeBytes = 0L;
        long reads = 0L;
        long writes = 0L;
        for (HWDiskStore store : stores) {
            try {
                readBytes += store.getReadBytes();
                writeBytes += store.getWriteBytes();
                reads += store.getReads();
                writes += store.getWrites();
            } catch (RuntimeException exception) {
                log.debug("读取磁盘 {} IO 指标失败: {}", store.getName(), exception.getMessage());
            }
        }
        Map<String, SystemMetricsSnapshot.MountMetrics> mounts = new LinkedHashMap<>();
        try {
            for (OSFileStore fileStore : fileSystem.getFileStores()) {
                if (!acceptMount(fileStore) || mounts.containsKey(fileStore.getMount())) {
                    continue;
                }
                mounts.put(fileStore.getMount(), new SystemMetricsSnapshot.MountMetrics(fileStore.getMount(),
                        fileStore.getTotalSpace(), fileStore.getFreeSpace(), fileStore.getUsableSpace()));
            }
        } catch (RuntimeException exception) {
            log.debug("读取磁盘分区指标失败: {}", exception.getMessage());
        }
        return new SystemMetricsSnapshot.DiskMetrics(readBytes, writeBytes, reads, writes,
                new ArrayList<>(mounts.values()));
    }

    private boolean acceptMount(OSFileStore fileStore) {
        if (fileStore.getTotalSpace() <= 0L || fileStore.getMount() == null) {
            return false;
        }
        return diskMounts.isEmpty() || diskMounts.contains(fileStore.getMount());
    }

    private SystemMetricsSnapshot.NetworkMetrics collectNetwork(List<NetworkIF> interfaces,
                                                               InternetProtocolStats protocolStats) {
        long rxBytes = 0L;
        long txBytes = 0L;
        long rxPackets = 0L;
        long txPackets = 0L;
        for (NetworkIF networkIF : interfaces) {
            try {
                networkIF.updateAttributes();
                if (!acceptInterface(networkIF)) {
                    continue;
                }
                rxBytes += networkIF.getBytesRecv();
                txBytes += networkIF.getBytesSent();
                rxPackets += networkIF.getPacketsRecv();
                txPackets += networkIF.getPacketsSent();
            } catch (RuntimeException exception) {
                log.debug("读取网卡 {} 指标失败: {}", networkIF.getName(), exception.getMessage());
            }
        }
        long established = 0L;
        long listen = 0L;
        long timeWait = 0L;
        long closeWait = 0L;
        long total = 0L;
        if (protocolStats != null) {
            for (InternetProtocolStats.IPConnection connection : protocolStats.getConnections()) {
                if (connection.getType() == null || !connection.getType().startsWith("tcp")) {
                    continue;
                }
                total++;
                String state = connection.getState() == null ? "" : connection.getState().name();
                switch (state) {
                    case "ESTABLISHED" -> established++;
                    case "LISTEN" -> listen++;
                    case "TIME_WAIT" -> timeWait++;
                    case "CLOSE_WAIT" -> closeWait++;
                    default -> {
                    }
                }
            }
        }
        return new SystemMetricsSnapshot.NetworkMetrics(rxBytes, txBytes, rxPackets, txPackets, established, listen,
                timeWait, closeWait, total);
    }

    private boolean acceptInterface(NetworkIF networkIF) {
        if (networkInterfaces.isEmpty()) {
            return !"lo".equals(networkIF.getName()) && !"lo0".equals(networkIF.getName());
        }
        return networkInterfaces.contains(networkIF.getName()) || networkInterfaces.contains(networkIF.getDisplayName());
    }

    private SystemMetricsSnapshot.ProcessMetrics collectProcess(OperatingSystem operatingSystem) {
        long openFiles = 0L;
        long maxFiles = 0L;
        try {
            FileSystem fileSystem = operatingSystem.getFileSystem();
            openFiles = fileSystem.getOpenFileDescriptors();
            maxFiles = fileSystem.getMaxFileDescriptors();
        } catch (RuntimeException exception) {
            log.debug("读取文件描述符指标失败: {}", exception.getMessage());
        }
        return new SystemMetricsSnapshot.ProcessMetrics(operatingSystem.getProcessCount(),
                operatingSystem.getThreadCount(), Math.max(openFiles, 0L), Math.max(maxFiles, 0L));
    }

    private static double clean(double value) {
        return Double.isFinite(value) && value >= 0d ? value : 0d;
    }

    private static double[] clean(double[] values) {
        if (values == null) {
            return new double[0];
        }
        double[] result = new double[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = clean(values[index]);
        }
        return result;
    }
}
