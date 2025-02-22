/*--------------------------------------------------------------------------
 *  Copyright 2007 Taro L. Saito
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *--------------------------------------------------------------------------*/
// --------------------------------------
// SQLite JDBC Project
//
// SQLite.java
// Since: 2007/05/10
//
// $URL$
// $Author$
// --------------------------------------
package org.sqlite;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.sqlite.util.OSInfo;
import org.sqlite.util.StringUtils;

/**
 * Set the system properties, org.sqlite.lib.path, org.sqlite.lib.name, appropriately so that the
 * SQLite JDBC driver can find *.dll, *.jnilib and *.so files, according to the current OS (win,
 * linux, mac).
 *
 * <p>The library files are automatically extracted from this project's package (JAR).
 *
 * <p>usage: call {@link #initialize()} before using SQLite JDBC driver.
 *
 * @author leo
 */
public class SQLiteJDBCLoader {

    private static boolean extracted = false;

    /**
     * Loads SQLite native JDBC library.
     *
     * @return True if SQLite native library is successfully loaded; false otherwise.
     */
    public static synchronized boolean initialize() throws Exception {
        // only cleanup before the first extract
        if (!extracted) {
            cleanup();
        }
        loadSQLiteNativeLibrary();
        return extracted;
    }

    private static File getTempDir() {
        return new File(
                System.getProperty("org.sqlite.tmpdir", System.getProperty("java.io.tmpdir")));
    }

    /**
     * Deleted old native libraries e.g. on Windows the DLL file is not removed on VM-Exit (bug #80)
     */
    static void cleanup() {
        String tempFolder = getTempDir().getAbsolutePath();
        File dir = new File(tempFolder);

        File[] nativeLibFiles =
                dir.listFiles(
                        new FilenameFilter() {
                            private final String searchPattern = "sqlite-" + getVersion();

                            public boolean accept(File dir, String name) {
                                return name.startsWith(searchPattern) && !name.endsWith(".lck");
                            }
                        });
        if (nativeLibFiles != null) {
            for (File nativeLibFile : nativeLibFiles) {
                File lckFile = new File(nativeLibFile.getAbsolutePath() + ".lck");
                if (!lckFile.exists()) {
                    try {
                        nativeLibFile.delete();
                    } catch (SecurityException e) {
                        System.err.println("Failed to delete old native lib" + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * @return True if the SQLite JDBC driver is set to pure Java mode; false otherwise.
     * @deprecated Pure Java no longer supported
     */
    @Deprecated
    static boolean getPureJavaFlag() {
        return Boolean.parseBoolean(System.getProperty("sqlite.purejava", "false"));
    }

    /**
     * Checks if the SQLite JDBC driver is set to pure Java mode.
     *
     * @return True if the SQLite JDBC driver is set to pure Java mode; false otherwise.
     * @deprecated Pure Java nolonger supported
     */
    @Deprecated
    public static boolean isPureJavaMode() {
        return false;
    }

    /**
     * Checks if the SQLite JDBC driver is set to native mode.
     *
     * @return True if the SQLite JDBC driver is set to native Java mode; false otherwise.
     */
    public static boolean isNativeMode() throws Exception {
        // load the driver
        initialize();
        return extracted;
    }

    /**
     * Computes the MD5 value of the input stream.
     *
     * @param input InputStream.
     * @return Encrypted string for the InputStream.
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    static String md5sum(InputStream input) throws IOException {
        BufferedInputStream in = new BufferedInputStream(input);

        try {
            MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            DigestInputStream digestInputStream = new DigestInputStream(in, digest);
            for (; digestInputStream.read() >= 0; ) {}

            ByteArrayOutputStream md5out = new ByteArrayOutputStream();
            md5out.write(digest.digest());
            return md5out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm is not available: " + e);
        } finally {
            in.close();
        }
    }

    private static boolean contentsEquals(InputStream in1, InputStream in2) throws IOException {
        if (!(in1 instanceof BufferedInputStream)) {
            in1 = new BufferedInputStream(in1);
        }
        if (!(in2 instanceof BufferedInputStream)) {
            in2 = new BufferedInputStream(in2);
        }

        int ch = in1.read();
        while (ch != -1) {
            int ch2 = in2.read();
            if (ch != ch2) {
                return false;
            }
            ch = in1.read();
        }
        int ch2 = in2.read();
        return ch2 == -1;
    }

    /**
     * Extracts and loads the specified library file to the target folder
     *
     * @param libFolderForCurrentOS Library path.
     * @param libraryFileName Library name.
     * @param targetFolder Target folder.
     * @return
     */
    private static boolean extractAndLoadLibraryFile(
            String libFolderForCurrentOS, String libraryFileName, String targetFolder) {
        String nativeLibraryFilePath = libFolderForCurrentOS + "/" + libraryFileName;
        // Include architecture name in temporary filename in order to avoid conflicts
        // when multiple JVMs with different architectures running at the same time
        String uuid = UUID.randomUUID().toString();
        String extractedLibFileName =
                String.format("sqlite-%s-%s-%s", getVersion(), uuid, libraryFileName);
        String extractedLckFileName = extractedLibFileName + ".lck";

        File extractedLibFile = new File(targetFolder, extractedLibFileName);
        File extractedLckFile = new File(targetFolder, extractedLckFileName);

        try {
            // Extract a native library file into the target directory
            InputStream reader = getResourceAsStream(nativeLibraryFilePath);
            if (!extractedLckFile.exists()) {
                new FileOutputStream(extractedLckFile).close();
            }
            FileOutputStream writer = new FileOutputStream(extractedLibFile);
            try {
                byte[] buffer = new byte[8192];
                int bytesRead = 0;
                while ((bytesRead = reader.read(buffer)) != -1) {
                    writer.write(buffer, 0, bytesRead);
                }
            } finally {
                // Delete the extracted lib file on JVM exit.
                extractedLibFile.deleteOnExit();
                extractedLckFile.deleteOnExit();

                if (writer != null) {
                    writer.close();
                }
                if (reader != null) {
                    reader.close();
                }
            }

            // Set executable (x) flag to enable Java to load the native library
            extractedLibFile.setReadable(true);
            extractedLibFile.setWritable(true, true);
            extractedLibFile.setExecutable(true);

            // Check whether the contents are properly copied from the resource folder
            {
                InputStream nativeIn = getResourceAsStream(nativeLibraryFilePath);
                InputStream extractedLibIn = new FileInputStream(extractedLibFile);
                try {
                    if (!contentsEquals(nativeIn, extractedLibIn)) {
                        throw new RuntimeException(
                                String.format(
                                        "Failed to write a native library file at %s",
                                        extractedLibFile));
                    }
                } finally {
                    if (nativeIn != null) {
                        nativeIn.close();
                    }
                    if (extractedLibIn != null) {
                        extractedLibIn.close();
                    }
                }
            }
            return loadNativeLibrary(targetFolder, extractedLibFileName);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Replacement of java.lang.Class#getResourceAsStream(String) to disable sharing the resource
    // stream
    // in multiple class loaders and specifically to avoid
    // https://bugs.openjdk.java.net/browse/JDK-8205976
    private static InputStream getResourceAsStream(String name) {
        // Remove leading '/' since all our resource paths include a leading directory
        // See:
        // https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/lang/Class.java#L3054
        String resolvedName = name.substring(1);
        ClassLoader cl = SQLiteJDBCLoader.class.getClassLoader();
        URL url = cl.getResource(resolvedName);
        if (url == null) {
            return null;
        }
        try {
            URLConnection connection = url.openConnection();
            connection.setUseCaches(false);
            return connection.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Loads native library using the given path and name of the library.
     *
     * @param path Path of the native library.
     * @param name Name of the native library.
     * @return True for successfully loading; false otherwise.
     */
    private static boolean loadNativeLibrary(String path, String name) {
        File libPath = new File(path, name);
        if (libPath.exists()) {

            try {
                System.load(new File(path, name).getAbsolutePath());
                return true;
            } catch (UnsatisfiedLinkError e) {
                System.err.println(
                        "Failed to load native library:"
                                + name
                                + ". osinfo: "
                                + OSInfo.getNativeLibFolderPathForCurrentOS());
                System.err.println(e);
                return false;
            }

        } else {
            return false;
        }
    }

    /**
     * Loads SQLite native library using given path and name of the library.
     *
     * @throws
     */
    private static void loadSQLiteNativeLibrary() throws Exception {
        if (extracted) {
            return;
        }

        List<String> triedPaths = new LinkedList<String>();

        // Try loading library from org.sqlite.lib.path library path */
        String sqliteNativeLibraryPath = System.getProperty("org.sqlite.lib.path");
        String sqliteNativeLibraryName = System.getProperty("org.sqlite.lib.name");
        if (sqliteNativeLibraryName == null) {
            sqliteNativeLibraryName = System.mapLibraryName("sqlitejdbc");
            if (sqliteNativeLibraryName != null && sqliteNativeLibraryName.endsWith(".dylib")) {
                sqliteNativeLibraryName = sqliteNativeLibraryName.replace(".dylib", ".jnilib");
            }
        }

        if (sqliteNativeLibraryPath != null) {
            if (loadNativeLibrary(sqliteNativeLibraryPath, sqliteNativeLibraryName)) {
                extracted = true;
                return;
            } else {
                triedPaths.add(sqliteNativeLibraryPath);
            }
        }

        // Load the os-dependent library from the jar file
        String packagePath = SQLiteJDBCLoader.class.getPackage().getName().replaceAll("\\.", "/");
        sqliteNativeLibraryPath =
                String.format(
                        "/%s/native/%s", packagePath, OSInfo.getNativeLibFolderPathForCurrentOS());
        boolean hasNativeLib = hasResource(sqliteNativeLibraryPath + "/" + sqliteNativeLibraryName);

        if (!hasNativeLib) {
            if (OSInfo.getOSName().equals("Mac")) {
                // Fix for openjdk7 for Mac
                String altName = "libsqlitejdbc.jnilib";
                if (hasResource(sqliteNativeLibraryPath + "/" + altName)) {
                    sqliteNativeLibraryName = altName;
                    hasNativeLib = true;
                }
            }
        }

        if (hasNativeLib) {
            // temporary library folder
            String tempFolder = getTempDir().getAbsolutePath();
            // Try extracting the library from jar
            if (extractAndLoadLibraryFile(
                    sqliteNativeLibraryPath, sqliteNativeLibraryName, tempFolder)) {
                extracted = true;
                return;
            } else {
                triedPaths.add(sqliteNativeLibraryPath);
            }
        }

        // As a last resort try from java.library.path
        String javaLibraryPath = System.getProperty("java.library.path", "");
        for (String ldPath : javaLibraryPath.split(File.pathSeparator)) {
            if (ldPath.isEmpty()) {
                continue;
            }
            if (loadNativeLibrary(ldPath, sqliteNativeLibraryName)) {
                extracted = true;
                return;
            } else {
                triedPaths.add(ldPath);
            }
        }

        extracted = false;
        throw new Exception(
                String.format(
                        "No native library found for os.name=%s, os.arch=%s, paths=[%s]",
                        OSInfo.getOSName(),
                        OSInfo.getArchName(),
                        StringUtils.join(triedPaths, File.pathSeparator)));
    }

    private static boolean hasResource(String path) {
        return SQLiteJDBCLoader.class.getResource(path) != null;
    }

    @SuppressWarnings("unused")
    private static void getNativeLibraryFolderForTheCurrentOS() {
        String osName = OSInfo.getOSName();
        String archName = OSInfo.getArchName();
    }

    /** @return The major version of the SQLite JDBC driver. */
    public static int getMajorVersion() {
        String[] c = getVersion().split("\\.");
        return (c.length > 0) ? Integer.parseInt(c[0]) : 1;
    }

    /** @return The minor version of the SQLite JDBC driver. */
    public static int getMinorVersion() {
        String[] c = getVersion().split("\\.");
        return (c.length > 1) ? Integer.parseInt(c[1]) : 0;
    }

    /** @return The version of the SQLite JDBC driver. */
    public static String getVersion() {

        URL versionFile =
                SQLiteJDBCLoader.class.getResource(
                        "/META-INF/maven/org.xerial/sqlite-jdbc/pom.properties");
        if (versionFile == null) {
            versionFile =
                    SQLiteJDBCLoader.class.getResource(
                            "/META-INF/maven/org.xerial/sqlite-jdbc/VERSION");
        }

        String version = "unknown";
        try {
            if (versionFile != null) {
                Properties versionData = new Properties();
                versionData.load(versionFile.openStream());
                version = versionData.getProperty("version", version);
                version = version.trim().replaceAll("[^0-9\\.]", "");
            }
        } catch (IOException e) {
            System.err.println(e);
        }
        return version;
    }
}
