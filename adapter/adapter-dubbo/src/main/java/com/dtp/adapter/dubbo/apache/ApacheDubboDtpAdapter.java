package com.dtp.adapter.dubbo.apache;

import cn.hutool.core.map.MapUtil;
import com.dtp.common.config.DtpProperties;
import com.dtp.common.dto.ExecutorWrapper;
import com.dtp.common.util.ReflectionUtil;
import com.dtp.core.adapter.AbstractDtpAdapter;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.dubbo.common.Version;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.store.DataStore;
import org.apache.dubbo.common.threadpool.manager.DefaultExecutorRepository;
import org.apache.dubbo.common.threadpool.manager.ExecutorRepository;
import org.apache.dubbo.rpc.model.ApplicationModel;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import static org.apache.dubbo.common.constants.CommonConstants.EXECUTOR_SERVICE_COMPONENT_KEY;

/**
 * ApacheDubboDtpAdapter related
 *
 * @author yanhom
 * @since 1.0.6
 */
@Slf4j
@SuppressWarnings("all")
public class ApacheDubboDtpAdapter extends AbstractDtpAdapter {

    private static final String NAME = "dubboTp";

    private static final Map<String, ExecutorWrapper> DUBBO_EXECUTORS = Maps.newHashMap();

    @Override
    public void refresh(DtpProperties dtpProperties) {
        refresh(NAME, dtpProperties.getDubboTp(), dtpProperties.getPlatforms());
    }

    @Override
    public Map<String, ExecutorWrapper> getExecutorWrappers() {
        return DUBBO_EXECUTORS;
    }

    @Override
    protected void initialize() {
        super.initialize();
        String currVersion = Version.getVersion();
        if (DubboVersion.compare(DubboVersion.VERSION_2_7_5, currVersion) > 0) {
            DataStore dataStore = ExtensionLoader.getExtensionLoader(DataStore.class).getDefaultExtension();
            Map<String, Object> executors = dataStore.get(EXECUTOR_SERVICE_COMPONENT_KEY);
            if (MapUtil.isNotEmpty(executors)) {
                executors.forEach((k, v) -> {
                    val name = genTpName(k);
                    val executorWrapper = new ExecutorWrapper(name, (ThreadPoolExecutor) v);
                    DUBBO_EXECUTORS.put(name, executorWrapper);
                });
            }
            return;
        }

        ExecutorRepository executorRepository;
        if (DubboVersion.compare(currVersion, DubboVersion.VERSION_3_0_3) >= 0) {
            executorRepository = ApplicationModel.defaultModel().getExtensionLoader(ExecutorRepository.class).getDefaultExtension();
        } else {
            executorRepository = ExtensionLoader.getExtensionLoader(ExecutorRepository.class).getDefaultExtension();
        }

        val data = (ConcurrentMap<String, ConcurrentMap<Integer, ExecutorService>>) ReflectionUtil.getFieldValue(
                DefaultExecutorRepository.class, "data", executorRepository);
        if (Objects.isNull(data)) {
            return;
        }
        Map<Integer, ExecutorService> executorMap = data.get(EXECUTOR_SERVICE_COMPONENT_KEY);
        if (MapUtil.isNotEmpty(executorMap)) {
            executorMap.forEach((k, v) -> {
                val name = genTpName(k.toString());
                val executorWrapper = new ExecutorWrapper(name, (ThreadPoolExecutor) v);
                initNotifyItems(name, executorWrapper);
                DUBBO_EXECUTORS.put(name, executorWrapper);
            });
        }
        log.info("DynamicTp adapter, apache dubbo executors init end, executors: {}", DUBBO_EXECUTORS);
    }

    private String genTpName(String port) {
        return NAME + "#" + port;
    }
}
