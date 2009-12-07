package com.google.gerrit.main;

// Copyright (C) 2009 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Main class for a JAR file to run code from "WEB-INF/lib". */
public final class GerritLauncher {
  private static final String pkg = "com.google.gerrit.pgm";
  private static final String NOT_ARCHIVED = "NOT_ARCHIVED";

  public static void main(final String argv[]) throws Exception {
    System.exit(mainImpl(argv));
  }

  private static int mainImpl(final String argv[]) throws Exception {
    if (argv.length == 0) {
      File me;
      try {
        me = getDistributionArchive();
      } catch (FileNotFoundException e) {
        me = null;
      }

      String jar = me != null ? me.getName() : "gerrit.war";
      System.err.println("Gerrit Code Review " + getVersion(me));
      System.err.println("usage: java -jar " + jar + " command [ARG ...]");
      System.err.println();
      System.err.println("The most commonly used commands are:");
      System.err.println("  init           Initialize a Gerrit installation");
      System.err.println("  daemon         Run the Gerrit network daemons");
      System.err.println("  gsql           Run the interactive query console");
      System.err.println("  version        Display the build version number");
      System.err.println();
      System.err.println("  ls             List files available for cat");
      System.err.println("  cat FILE       Display a file from the archive");
      System.err.println();
      return 1;
    }

    // Special cases, a few global options actually are programs.
    //
    if ("-v".equals(argv[0]) || "--version".equals(argv[0])) {
      argv[0] = "version";
    } else if ("-p".equals(argv[0]) || "--cat".equals(argv[0])) {
      argv[0] = "cat";
    } else if ("-l".equals(argv[0]) || "--ls".equals(argv[0])) {
      argv[0] = "ls";
    }

    // Run the application class
    //
    final ClassLoader cl = libClassLoader();
    Thread.currentThread().setContextClassLoader(cl);
    return invokeProgram(cl, argv);
  }

  private static String getVersion(final File me) {
    if (me == null) {
      return "";
    }

    try {
      final JarFile jar = new JarFile(me);
      try {
        Manifest mf = jar.getManifest();
        Attributes att = mf.getMainAttributes();
        String val = att.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
        return val != null ? val : "";
      } finally {
        jar.close();
      }
    } catch (IOException e) {
      return "";
    }
  }

  private static int invokeProgram(final ClassLoader loader,
      final String[] origArgv) throws Exception {
    String name = origArgv[0];
    final String[] argv = new String[origArgv.length - 1];
    System.arraycopy(origArgv, 1, argv, 0, argv.length);

    Class<?> clazz;
    try {
      try {
        clazz = Class.forName(pkg + "." + name, true, loader);
      } catch (ClassNotFoundException cnfe) {
        if (name.equals(name.toLowerCase())) {
          String first = name.substring(0, 1).toUpperCase();
          String cn = first + name.substring(1);
          clazz = Class.forName(pkg + "." + cn, true, loader);
        } else {
          throw cnfe;
        }
      }
    } catch (ClassNotFoundException cnfe) {
      System.err.println("fatal: unknown command " + name);
      System.err.println("      (no " + pkg + "." + name + ")");
      return 1;
    }

    final Method main;
    try {
      main = clazz.getMethod("main", argv.getClass());
    } catch (SecurityException e) {
      System.err.println("fatal: unknown command " + name);
      return 1;
    } catch (NoSuchMethodException e) {
      System.err.println("fatal: unknown command " + name);
      return 1;
    }

    final Object res;
    try {
      if ((main.getModifiers() & Modifier.STATIC) == Modifier.STATIC) {
        res = main.invoke(null, new Object[] {argv});
      } else {
        res = main.invoke(clazz.newInstance(), new Object[] {argv});
      }
    } catch (InvocationTargetException ite) {
      if (ite.getCause() instanceof Exception) {
        throw (Exception) ite.getCause();
      } else if (ite.getCause() instanceof Error) {
        throw (Error) ite.getCause();
      } else {
        throw ite;
      }
    }
    if (res instanceof Number) {
      return ((Number) res).intValue();
    } else {
      return 0;
    }
  }

  private static ClassLoader libClassLoader() throws IOException {
    final File path;
    try {
      path = getDistributionArchive();
    } catch (FileNotFoundException e) {
      if (NOT_ARCHIVED == e.getMessage()) {
        // Assume the CLASSPATH was made complete by the calling process,
        // as we are likely being run from within a developer's IDE.
        //
        return GerritLauncher.class.getClassLoader();
      }
      throw e;
    }

    final ArrayList<URL> jars = new ArrayList<URL>();
    try {
      final ZipFile zf = new ZipFile(path);
      try {
        final Enumeration<? extends ZipEntry> e = zf.entries();
        while (e.hasMoreElements()) {
          final ZipEntry ze = e.nextElement();
          if (ze.isDirectory()) {
            continue;
          }

          if (ze.getName().startsWith("WEB-INF/lib/")) {
            final File tmp = createTempFile(safeName(ze), ".jar");
            final FileOutputStream out = new FileOutputStream(tmp);
            try {
              final InputStream in = zf.getInputStream(ze);
              try {
                final byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf, 0, buf.length)) > 0) {
                  out.write(buf, 0, n);
                }
              } finally {
                in.close();
              }
            } finally {
              out.close();
            }
            jars.add(tmp.toURI().toURL());
          }
        }
      } finally {
        zf.close();
      }
    } catch (IOException e) {
      throw new IOException("Cannot obtain libraries from " + path, e);
    }

    if (jars.isEmpty()) {
      return GerritLauncher.class.getClassLoader();
    }
    return new URLClassLoader(jars.toArray(new URL[jars.size()]));
  }

  private static String safeName(final ZipEntry ze) {
    // Try to derive the name of the temporary file so it
    // doesn't completely suck. Best if we can make it
    // match the name it was in the archive.
    //
    String name = ze.getName();
    if (0 <= name.lastIndexOf('/')) {
      name = name.substring(name.lastIndexOf('/') + 1);
    }
    if (0 <= name.lastIndexOf('.')) {
      name = name.substring(0, name.lastIndexOf('.'));
    }
    if (name.isEmpty()) {
      name = "code";
    }
    return name;
  }

  private static File myArchive;

  /**
   * Locate the JAR/WAR file we were launched from.
   *
   * @return local path of the Gerrit WAR file.
   * @throws FileNotFoundException if the code cannot guess the location.
   */
  public static File getDistributionArchive() throws FileNotFoundException {
    if (myArchive == null) {
      myArchive = locateMyArchive();
    }
    return myArchive;
  }

  private static File locateMyArchive() throws FileNotFoundException {
    final ClassLoader myCL = GerritLauncher.class.getClassLoader();
    final String myName =
        GerritLauncher.class.getName().replace('.', '/') + ".class";

    final URL myClazz = myCL.getResource(myName);
    if (myClazz == null) {
      throw new FileNotFoundException("Cannot find JAR: no " + myName);
    }

    // ZipFile may have the path of our JAR hiding within itself.
    //
    try {
      Field nameField = ZipFile.class.getDeclaredField("name");
      nameField.setAccessible(true);

      JarFile jar = ((JarURLConnection) myClazz.openConnection()).getJarFile();
      File path = new File((String) nameField.get(jar));
      if (path.isFile()) {
        return path;
      }
    } catch (Exception e) {
      // Nope, that didn't work. Try a different method.
      //
    }

    // Maybe this is a local class file, running under a debugger?
    //
    if ("file".equals(myClazz.getProtocol())) {
      final File path = new File(myClazz.getPath());
      if (path.isFile() && path.getParentFile().isDirectory()) {
        throw new FileNotFoundException(NOT_ARCHIVED);
      }
    }

    // The CodeSource might be able to give us the source as a stream.
    // If so, copy it to a local file so we have random access to it.
    //
    final CodeSource src =
        GerritLauncher.class.getProtectionDomain().getCodeSource();
    if (src != null) {
      try {
        final InputStream in = src.getLocation().openStream();
        try {
          final File tmp = createTempFile("gerrit_", ".zip");
          final FileOutputStream out = new FileOutputStream(tmp);
          try {
            final byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf, 0, buf.length)) > 0) {
              out.write(buf, 0, n);
            }
          } finally {
            out.close();
          }
          return tmp;
        } finally {
          in.close();
        }
      } catch (IOException e) {
        // Nope, that didn't work.
        //
      }
    }

    throw new FileNotFoundException("Cannot find local copy of JAR");
  }

  private static boolean temporaryDirectoryFound;
  private static File temporaryDirectory;

  private static File createTempFile(String prefix, String suffix)
      throws IOException {
    if (!temporaryDirectoryFound) {
      final File d = File.createTempFile("gerrit_", "_app", tmproot());
      if (d.delete() && d.mkdir()) {
        // Try to lock the directory down to be accessible by us.
        // We first have to remove all permissions, then add back
        // only the owner permissions.
        //
        d.setWritable(false, false /* all */);
        d.setReadable(false, false /* all */);
        d.setExecutable(false, false /* all */);

        d.setWritable(true, true /* owner only */);
        d.setReadable(true, true /* owner only */);
        d.setExecutable(true, true /* owner only */);

        d.deleteOnExit();
        temporaryDirectory = d;
      }
      temporaryDirectoryFound = true;
    }

    if (temporaryDirectory != null) {
      // If we have a private directory and this name has not yet
      // been used within the private directory, create it as-is.
      //
      final File tmp = new File(temporaryDirectory, prefix + suffix);
      if (tmp.createNewFile()) {
        tmp.deleteOnExit();
        return tmp;
      }
    }

    if (!prefix.endsWith("_")) {
      prefix += "_";
    }

    final File tmp = File.createTempFile(prefix, suffix, temporaryDirectory);
    tmp.deleteOnExit();
    return tmp;
  }

  private static File tmproot() {
    // Try to find the user's home directory. If we can't find it
    // return null so the JVM's default temporary directory is used
    // instead. This is probably /tmp or /var/tmp.
    //
    String userHome = System.getProperty("user.home");
    if (userHome == null || "".equals(userHome)) {
      userHome = System.getenv("HOME");
      if (userHome == null || "".equals(userHome)) {
        System.err.println("warning: cannot determine home directory");
        System.err.println("warning: using system temporary directory instead");
        return null;
      }
    }

    // Ensure the home directory exists. If it doesn't, try to make it.
    //
    final File home = new File(userHome);
    if (!home.exists()) {
      if (home.mkdirs()) {
        System.err.println("warning: created " + home.getAbsolutePath());
      } else {
        System.err.println("warning: " + home.getAbsolutePath() + " not found");
        System.err.println("warning: using system temporary directory instead");
        return null;
      }
    }

    // Use $HOME/.gerritcodereview/tmp for our temporary file area.
    //
    final File tmp = new File(new File(home, ".gerritcodereview"), "tmp");
    if (!tmp.exists() && !tmp.mkdirs()) {
      System.err.println("warning: cannot create " + tmp.getAbsolutePath());
      System.err.println("warning: using system temporary directory instead");
      return null;
    }
    return tmp;
  }

  private GerritLauncher() {
  }
}
