package armadillo.plugin;

import armadillo.utils.StreamUtil;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class PluginClassloader extends ClassLoader {
    private final String classpath;

    public PluginClassloader(ClassLoader parent, String classpath) {
        super(parent);
        this.classpath = classpath;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = loadClassData(name);
        if (bytes != null)
            return defineClass(name, bytes, 0, bytes.length);
        return super.findClass(name);
    }

    private byte[] loadClassData(String cls) {
        if (classpath != null) {
            if (classpath.endsWith(".zip") || classpath.endsWith(".jar")) {
                String entryName = cls.replace(".", "/") + ".class";
                try (ZipFile zipFile = new ZipFile(classpath)) {
                    ZipEntry entry = zipFile.getEntry(entryName);
                    if (entry == null)
                        return null;
                    InputStream stream = zipFile.getInputStream(entry);
                    if (stream == null)
                        return null;
                    return StreamUtil.readBytes(stream);
                } catch (Exception e) {
                    // class not in this plugin jar, return null to delegate to parent
                }
            }
        }
        return null;
    }
}
