package cn.slainc.monitor.system;

import cn.slainc.monitor.metrics.MetricsTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.util.concurrent.atomic.AtomicBoolean;

public class OshiSystemMetricsBinder implements MeterBinder {

    private final OshiSystemMetricsCollector collector;

    private final MetricsTemplate metrics;

    private final boolean perCoreCpu;

    private final AtomicBoolean discovered = new AtomicBoolean();

    public OshiSystemMetricsBinder(OshiSystemMetricsCollector collector, MetricsTemplate metrics,
                                   boolean perCoreCpu) {
        if (collector == null) {
            throw new IllegalArgumentException("OshiSystemMetricsCollector 不能为空");
        }
        if (metrics == null) {
            throw new IllegalArgumentException("MetricsTemplate 不能为空");
        }
        this.collector = collector;
        this.metrics = metrics;
        this.perCoreCpu = perCoreCpu;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        metrics.gauge("modality.system.cpu.usage", "系统 CPU 使用率", collector,
                source -> source.snapshot().getCpu().usage());
        metrics.gauge("modality.system.cpu.load.1m", "系统 1 分钟平均负载", collector,
                source -> source.snapshot().getCpu().load1m());
        metrics.gauge("modality.system.cpu.count", "逻辑 CPU 核数", collector,
                source -> source.snapshot().getCpu().count());
        metrics.gauge("modality.system.memory.total.bytes", "物理内存总量", collector,
                source -> source.snapshot().getMemory().total());
        metrics.gauge("modality.system.memory.available.bytes", "物理内存可用量", collector,
                source -> source.snapshot().getMemory().available());
        metrics.gauge("modality.system.memory.used.bytes", "物理内存已用量", collector,
                source -> source.snapshot().getMemory().used());
        metrics.gauge("modality.system.memory.usage", "物理内存使用率", collector,
                source -> source.snapshot().getMemory().usage());
        metrics.gauge("modality.system.swap.total.bytes", "交换区总量", collector,
                source -> source.snapshot().getMemory().swapTotal());
        metrics.gauge("modality.system.swap.used.bytes", "交换区已用量", collector,
                source -> source.snapshot().getMemory().swapUsed());
        metrics.gauge("modality.system.disk.io.read.bytes", "磁盘累计读取字节数", collector,
                source -> source.snapshot().getDisk().readBytes());
        metrics.gauge("modality.system.disk.io.write.bytes", "磁盘累计写入字节数", collector,
                source -> source.snapshot().getDisk().writeBytes());
        metrics.gauge("modality.system.disk.io.reads", "磁盘累计读次数", collector,
                source -> source.snapshot().getDisk().reads());
        metrics.gauge("modality.system.disk.io.writes", "磁盘累计写次数", collector,
                source -> source.snapshot().getDisk().writes());
        metrics.gauge("modality.system.network.rx.bytes", "网卡累计接收字节数", collector,
                source -> source.snapshot().getNetwork().rxBytes());
        metrics.gauge("modality.system.network.tx.bytes", "网卡累计发送字节数", collector,
                source -> source.snapshot().getNetwork().txBytes());
        metrics.gauge("modality.system.network.rx.packets", "网卡累计接收包数", collector,
                source -> source.snapshot().getNetwork().rxPackets());
        metrics.gauge("modality.system.network.tx.packets", "网卡累计发送包数", collector,
                source -> source.snapshot().getNetwork().txPackets());
        metrics.gauge("modality.system.network.tcp.connections", "TCP 连接数", collector,
                source -> source.snapshot().getNetwork().tcpEstablished(), "state", "established");
        metrics.gauge("modality.system.network.tcp.connections", "TCP 连接数", collector,
                source -> source.snapshot().getNetwork().tcpListen(), "state", "listen");
        metrics.gauge("modality.system.network.tcp.connections", "TCP 连接数", collector,
                source -> source.snapshot().getNetwork().tcpTimeWait(), "state", "time_wait");
        metrics.gauge("modality.system.network.tcp.connections", "TCP 连接数", collector,
                source -> source.snapshot().getNetwork().tcpCloseWait(), "state", "close_wait");
        metrics.gauge("modality.system.network.tcp.connections", "TCP 连接数", collector,
                source -> source.snapshot().getNetwork().tcpTotal(), "state", "total");
        metrics.gauge("modality.system.process.count", "系统进程数", collector,
                source -> source.snapshot().getProcess().processes());
        metrics.gauge("modality.system.thread.count", "系统线程数", collector,
                source -> source.snapshot().getProcess().threads());
        metrics.gauge("modality.system.files.open", "已打开文件描述符数", collector,
                source -> source.snapshot().getProcess().openFiles());
        metrics.gauge("modality.system.files.max", "文件描述符上限", collector,
                source -> source.snapshot().getProcess().maxFiles());
        metrics.gauge("modality.system.uptime.seconds", "系统运行时长", collector,
                source -> source.snapshot().getUptimeSeconds());
        metrics.gauge("modality.system.boot.time.seconds", "系统启动时间", collector,
                source -> source.snapshot().getBootTimeSeconds());
        // 分区与单核指标依赖 OSHI 首次采集，放到第一次抓取时再注册，避免拖慢应用启动
        metrics.gauge("modality.system.disk.mounts", "已发现的磁盘分区数量", this,
                OshiSystemMetricsBinder::mountCount);
    }

    private double mountCount() {
        discover();
        return collector.snapshot().getDisk().mounts().size();
    }

    private void discover() {
        if (!discovered.compareAndSet(false, true)) {
            return;
        }
        for (SystemMetricsSnapshot.MountMetrics mount : collector.snapshot().getDisk().mounts()) {
            String mountPoint = mount.mount();
            metrics.gauge("modality.system.disk.total.bytes", "磁盘分区总量", collector,
                    source -> field(source, mountPoint, PartitionField.TOTAL), "mount", mountPoint);
            metrics.gauge("modality.system.disk.free.bytes", "磁盘分区可用量", collector,
                    source -> field(source, mountPoint, PartitionField.FREE), "mount", mountPoint);
            metrics.gauge("modality.system.disk.usable.bytes", "磁盘分区可用量（非特权）", collector,
                    source -> field(source, mountPoint, PartitionField.USABLE), "mount", mountPoint);
            metrics.gauge("modality.system.disk.usage", "磁盘分区使用率", collector,
                    source -> usage(source, mountPoint), "mount", mountPoint);
        }
        if (perCoreCpu) {
            int cores = (int) collector.snapshot().getCpu().count();
            for (int index = 0; index < cores; index++) {
                int core = index;
                metrics.gauge("modality.system.cpu.core.usage", "单核 CPU 使用率", collector,
                        source -> source.snapshot().getCpu().coreUsage(core), "core", String.valueOf(core));
            }
        }
    }

    private static double field(OshiSystemMetricsCollector collector, String mount, PartitionField field) {
        for (SystemMetricsSnapshot.MountMetrics candidate : collector.snapshot().getDisk().mounts()) {
            if (candidate.mount().equals(mount)) {
                return switch (field) {
                    case TOTAL -> candidate.total();
                    case FREE -> candidate.free();
                    case USABLE -> candidate.usable();
                };
            }
        }
        return 0d;
    }

    private static double usage(OshiSystemMetricsCollector collector, String mount) {
        for (SystemMetricsSnapshot.MountMetrics candidate : collector.snapshot().getDisk().mounts()) {
            if (candidate.mount().equals(mount)) {
                return candidate.usage();
            }
        }
        return 0d;
    }

    private enum PartitionField {
        TOTAL, FREE, USABLE
    }
}
