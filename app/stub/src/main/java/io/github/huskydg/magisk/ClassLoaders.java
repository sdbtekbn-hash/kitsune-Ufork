package io.github.huskydg.magisk;

import android.content.pm.PackageInfo;

import io.github.huskydg.magisk.dummy.DummyProvider;
import io.github.huskydg.magisk.dummy.DummyReceiver;
import io.github.huskydg.magisk.dummy.DummyService;

import java.util.HashMap;
import java.util.Map;

// Wrap the actual classloader as we only want to resolve classname
// mapping when loading from platform (via LoadedApk.mClassLoader)
class MappingClassLoader extends ClassLoader {

    private final Map<String, String> mapping;

    MappingClassLoader(ClassLoader parent, Map<String, String> m) {
        super(parent);
        mapping = m;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        String clz = mapping.get(name);
        name = clz != null ? clz : name;
        return super.loadClass(name, resolve);
    }
}

class StubClassLoader extends ClassLoader {

    private final Map<String, Class<?>> mapping = new HashMap<>();

    StubClassLoader(PackageInfo info) {
        super(StubClassLoader.class.getClassLoader());
        for (var c : info.activities) {
            mapping.put(c.name, DownloadActivity.class);
        }
        for (var c : info.services) {
            mapping.put(c.name, DummyService.class);
        }
        for (var c : info.providers) {
            mapping.put(c.name, DummyProvider.class);
        }
        for (var c : info.receivers) {
            mapping.put(c.name, DummyReceiver.class);
        }
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> clz = mapping.get(name);
        return clz == null ? super.loadClass(name, resolve) : clz;
    }
}

class DelegateClassLoader extends ClassLoader {

    DelegateClassLoader() {
        super();
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        return DynLoad.activeClassLoader.loadClass(name);
    }
}
